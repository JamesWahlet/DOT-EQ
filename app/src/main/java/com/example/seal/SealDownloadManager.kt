package com.example.seal

import android.net.Uri
import com.example.ai.AiChatActionResult
import com.example.data.EqPreferencesRepository
import com.example.model.SealAudioFormat
import com.example.model.SealAudioQuality
import com.example.model.SealDownloadStatus
import com.example.model.SealDownloadTask
import com.example.model.SealSaveLocation
import com.example.model.SealSettings
import com.example.model.SealTrackSuggestion
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SealDownloadManager(
    private val scope: CoroutineScope,
    private val prefsRepo: EqPreferencesRepository,
    private val mediaRepo: SealMediaRepository
) {
    private val _settings = MutableStateFlow(SealSettings())
    val settings: StateFlow<SealSettings> = _settings.asStateFlow()

    private val _downloads = MutableStateFlow<List<SealDownloadTask>>(emptyList())
    val downloads: StateFlow<List<SealDownloadTask>> = _downloads.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SealTrackSuggestion>>(emptyList())
    val searchResults: StateFlow<List<SealTrackSuggestion>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()

    fun loadInitial() {
        scope.launch {
            _settings.value = prefsRepo.sealSettingsFlow.first()
            _downloads.value = prefsRepo.sealHistoryFlow.first()
        }
    }

    fun updateFormat(format: SealAudioFormat) = updateSettings { copy(format = format) }
    fun updateQuality(quality: SealAudioQuality) = updateSettings { copy(quality = quality) }
    fun updateSaveLocation(location: SealSaveLocation) = updateSettings {
        copy(saveLocation = location)
    }
    fun updateCustomFolder(uri: Uri?, label: String) = updateSettings {
        if (uri == null) {
            copy(saveLocation = SealSaveLocation.MUSIC, customFolderUri = null, customFolderLabel = "")
        } else {
            copy(
                saveLocation = SealSaveLocation.CUSTOM,
                customFolderUri = uri.toString(),
                customFolderLabel = label.ifBlank { "Eigener Ordner" }
            )
        }
    }
    fun updateFilenameTemplate(template: String) =
        updateSettings { copy(filenameTemplate = template.ifBlank { "%(artist)s - %(title)s" }) }

    private fun updateSettings(transform: SealSettings.() -> SealSettings) {
        val next = _settings.value.transform()
        _settings.value = next
        scope.launch { prefsRepo.saveSealSettings(next) }
    }

    fun searchSongs(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _searchError.value = "Bitte Songname oder Artist eingeben."
            return
        }
        _isSearching.value = true
        _searchError.value = null
        scope.launch {
            val result = mediaRepo.searchTracks(trimmed, _settings.value)
            _isSearching.value = false
            result.fold(
                onSuccess = { list ->
                    _searchResults.value = list
                    _searchError.value = null
                },
                onFailure = { err ->
                    _searchResults.value = emptyList()
                    _searchError.value = err.message ?: "Suche fehlgeschlagen."
                }
            )
        }
    }

    suspend fun resolveForAiAction(action: AiChatActionResult): Pair<List<SealTrackSuggestion>, String?> {
        if (!action.requestSongDownload) return emptyList<SealTrackSuggestion>() to null

        val activeSettings = resolveEffectiveSettings(action.preferredDownloadFormat, null)
        val url = action.songUrl.trim()
        val query = action.songSearchQuery.trim()

        if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
            if (YouTubeAudioExtractor.isYouTubeUrl(url)) {
                val ytHits = mediaRepo.searchTracks(url, activeSettings)
                ytHits.fold(
                    onSuccess = { hits ->
                        _searchResults.value = hits
                        return hits.take(3) to null
                    },
                    onFailure = { err ->
                        return emptyList<SealTrackSuggestion>() to (err.message ?: "YouTube-Link konnte nicht aufgelöst werden.")
                    }
                )
            }
            val directTitle = query.ifBlank { "Audio-Stream" }
            val ext = if (activeSettings.format == SealAudioFormat.ORIGINAL ||
                activeSettings.quality == SealAudioQuality.AUTO
            ) {
                "m4a"
            } else {
                activeSettings.format.extension
            }
            val directSuggestion = SealTrackSuggestion(
                id = "url_${UUID.randomUUID()}",
                title = directTitle,
                artist = "Direct URL",
                sourceLabel = "Direkt-Link",
                shareUrl = url,
                downloadUrl = url,
                suggestedFilename = SealMediaRepository.formatTrackFilename(
                    template = activeSettings.filenameTemplate,
                    artist = "",
                    title = directTitle,
                    fallbackFromDisp = null,
                    extension = ext
                )
            )
            return listOf(directSuggestion) to null
        }

        if (query.isBlank()) {
            return emptyList<SealTrackSuggestion>() to "Kein Songtitel erkannt."
        }

        val searchResult = mediaRepo.searchTracks(query, activeSettings)
        return searchResult.fold(
            onSuccess = { hits ->
                _searchResults.value = hits
                hits.take(3) to null
            },
            onFailure = { err ->
                emptyList<SealTrackSuggestion>() to (err.message ?: "Kein Download-Link gefunden.")
            }
        )
    }

    fun startDownloadFromInput(input: String, qualityOverride: SealAudioQuality? = null) {
        val clean = input.trim()
        if (clean.isBlank()) return
        if (qualityOverride != null && qualityOverride != _settings.value.quality) {
            updateQuality(qualityOverride)
        }
        val effectiveSettings = resolveEffectiveSettings("", qualityOverride)
        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            val isYt = YouTubeAudioExtractor.isYouTubeUrl(clean)
            val rawName = if (isYt) {
                "YouTube Audio (${YouTubeAudioExtractor.extractVideoId(clean).orEmpty()})"
            } else {
                clean.substringAfterLast('/').substringBefore('?').ifBlank { "Audio Track" }
            }
            val suggestion = SealTrackSuggestion(
                id = UUID.randomUUID().toString(),
                title = rawName,
                artist = if (isYt) "YouTube" else "",
                sourceLabel = if (isYt) "YouTube" else "URL",
                shareUrl = clean,
                downloadUrl = clean,
                suggestedFilename = rawName
            )
            startDownload(suggestion, qualityOverride = effectiveSettings.quality)
        } else {
            _isSearching.value = true
            _searchError.value = null
            scope.launch {
                val res = mediaRepo.searchTracks(clean, effectiveSettings)
                _isSearching.value = false
                res.fold(
                    onSuccess = { list ->
                        _searchResults.value = list
                        val top = list.firstOrNull()
                        if (top != null) {
                            startDownload(top, qualityOverride = effectiveSettings.quality)
                        } else {
                            _searchError.value = "Kein Treffer gefunden."
                        }
                    },
                    onFailure = { err ->
                        _searchError.value = err.message ?: "Song nicht gefunden."
                    }
                )
            }
        }
    }

    fun startDownload(
        suggestion: SealTrackSuggestion,
        formatOverride: String? = null,
        qualityOverride: SealAudioQuality? = null
    ) {
        val activeSettings = resolveEffectiveSettings(formatOverride.orEmpty(), qualityOverride)
        val taskId = UUID.randomUUID().toString()
        val task = SealDownloadTask(
            id = taskId,
            title = suggestion.title,
            artist = suggestion.artist,
            sourceUrl = suggestion.shareUrl,
            resolvedMediaUrl = suggestion.downloadUrl,
            fileName = suggestion.suggestedFilename,
            formatLabel = "${activeSettings.quality.label} · ${activeSettings.format.label}",
            progress = 0f,
            status = SealDownloadStatus.DOWNLOADING
        )
        upsertTask(task)

        val job = scope.launch {
            try {
                val outcome = mediaRepo.downloadTrack(
                    mediaUrl = suggestion.downloadUrl,
                    preferredTitle = suggestion.title,
                    preferredArtist = suggestion.artist,
                    settings = activeSettings,
                    onProgress = { read, total ->
                        val frac = if (total > 0L) (read.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0.35f
                        updateTask(taskId) {
                            copy(
                                progress = frac,
                                bytesDownloaded = read,
                                totalBytes = total,
                                status = SealDownloadStatus.DOWNLOADING
                            )
                        }
                    }
                )

                outcome.fold(
                    onSuccess = { done ->
                        updateTask(taskId) {
                            copy(
                                title = done.resolvedTitle.ifBlank { title },
                                artist = done.resolvedArtist.ifBlank { artist },
                                progress = 1f,
                                bytesDownloaded = done.bytesWritten,
                                totalBytes = maxOf(totalBytes, done.bytesWritten),
                                fileName = done.resolvedFileName,
                                resolvedMediaUrl = done.resolvedMediaUrl,
                                formatLabel = done.resolvedQualityLabel,
                                savedUriString = done.savedUri.toString(),
                                status = SealDownloadStatus.COMPLETED,
                                errorMessage = null
                            )
                        }
                        persistHistory()
                    },
                    onFailure = { err ->
                        updateTask(taskId) {
                            copy(
                                status = SealDownloadStatus.FAILED,
                                errorMessage = err.message ?: "Download fehlgeschlagen."
                            )
                        }
                        persistHistory()
                    }
                )
            } catch (_: CancellationException) {
                updateTask(taskId) {
                    copy(status = SealDownloadStatus.CANCELLED)
                }
                persistHistory()
            } finally {
                activeJobs.remove(taskId)
            }
        }
        activeJobs[taskId] = job
    }

    fun cancelDownload(taskId: String) {
        activeJobs.remove(taskId)?.cancel()
        updateTask(taskId) { copy(status = SealDownloadStatus.CANCELLED) }
        persistHistory()
    }

    fun removeTask(taskId: String) {
        activeJobs.remove(taskId)?.cancel()
        _downloads.value = _downloads.value.filterNot { it.id == taskId }
        persistHistory()
    }

    fun clearHistory() {
        _downloads.value = _downloads.value.filter { it.status == SealDownloadStatus.DOWNLOADING }
        persistHistory()
    }

    private fun resolveEffectiveSettings(
        formatHint: String,
        qualityOverride: SealAudioQuality?
    ): SealSettings {
        var base = _settings.value
        if (qualityOverride != null) {
            base = base.copy(quality = qualityOverride)
        }
        if (formatHint.isNotBlank()) {
            val fmt = SealAudioFormat.entries.firstOrNull {
                it.name.equals(formatHint, ignoreCase = true) || it.extension.equals(formatHint, ignoreCase = true)
            }
            if (fmt != null) {
                base = base.copy(format = fmt)
            }
        }
        return base
    }

    private fun upsertTask(task: SealDownloadTask) {
        _downloads.value = listOf(task) + _downloads.value.filterNot { it.id == task.id }
    }

    private fun updateTask(id: String, transform: SealDownloadTask.() -> SealDownloadTask) {
        _downloads.value = _downloads.value.map { if (it.id == id) it.transform() else it }
    }

    private fun persistHistory() {
        val snapshot = _downloads.value
        scope.launch {
            prefsRepo.saveSealHistory(snapshot)
        }
    }
}
