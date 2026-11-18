package com.example.seal

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.model.SealAudioFormat
import com.example.model.SealAudioQuality
import com.example.model.SealSaveLocation
import com.example.model.SealSettings
import com.example.model.SealTrackSuggestion
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

private const val TAG = "LinkMediaRepository"

data class SealDownloadOutcome(
    val savedUri: Uri,
    val resolvedFileName: String,
    val resolvedMediaUrl: String,
    val resolvedQualityLabel: String,
    val bytesWritten: Long
)

class SealMediaRepository(
    private val context: Context,
    private val httpClient: OkHttpClient = defaultOkHttpClient()
) {
    suspend fun searchTracks(
        query: String,
        settings: SealSettings = SealSettings()
    ): Result<List<SealTrackSuggestion>> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Suchbegriff darf nicht leer sein."))
        }

        try {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val (audiusResults, itunesResults) = coroutineScope {
                val audiusDeferred = async {
                    runCatching { fetchAudiusTracks(encoded, settings) }.getOrElse { emptyList() }
                }
                val itunesDeferred = async {
                    runCatching { fetchItunesTracks(encoded, settings) }.getOrElse { emptyList() }
                }
                audiusDeferred.await() to itunesDeferred.await()
            }

            // Combine Audius (full-length CC/Indie streams) + iTunes (verified catalog)
            val combined = buildList {
                addAll(audiusResults.take(3))
                addAll(itunesResults.take(4))
            }

            if (combined.isEmpty()) {
                Result.failure(Exception("Keine Audio-Treffer für '$cleanQuery' gefunden."))
            } else {
                Result.success(combined)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Track search failed for query=$cleanQuery", e)
            Result.failure(Exception(e.localizedMessage ?: "Song-Suche fehlgeschlagen."))
        }
    }

    suspend fun downloadTrack(
        mediaUrl: String,
        preferredTitle: String,
        preferredArtist: String,
        settings: SealSettings,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<SealDownloadOutcome> = withContext(Dispatchers.IO) {
        try {
            var targetUrl = mediaUrl.trim()
            if (!targetUrl.startsWith("http://", ignoreCase = true) &&
                !targetUrl.startsWith("https://", ignoreCase = true)
            ) {
                return@withContext Result.failure(IllegalArgumentException("Ungültige URL: $targetUrl"))
            }

            val initialRequest = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "DotEQ-LinkDownloader/1.0 (Android)")
                .build()

            httpClient.newCall(initialRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Download fehlgeschlagen (HTTP ${response.code}).")
                    )
                }

                val contentType = response.header("Content-Type").orEmpty().lowercase()
                val body = response.body ?: return@withContext Result.failure(Exception("Leere Server-Antwort."))

                // If URL points to an HTML page, pick the best quality audio URL available on that page
                if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
                    val htmlSnippet = body.string().take(180_000)
                    val extractedAudioUrl = extractDirectAudioUrlFromHtml(htmlSnippet, settings.quality)
                    if (extractedAudioUrl != null && extractedAudioUrl != targetUrl) {
                        targetUrl = extractedAudioUrl
                        return@withContext downloadDirectStream(
                            streamUrl = targetUrl,
                            preferredTitle = preferredTitle,
                            preferredArtist = preferredArtist,
                            settings = settings,
                            onProgress = onProgress
                        )
                    } else {
                        return@withContext Result.failure(
                            Exception("Webseite enthält keinen direkten Audio-Stream. Nutze die Song-Suche oder füge einen direkten Audio-Link ein.")
                        )
                    }
                }

                val contentDisp = response.header("Content-Disposition").orEmpty()
                val totalBytes = body.contentLength()
                val ext = resolveExtension(targetUrl, contentType, settings.format, settings.quality)
                val fileName = formatTrackFilename(
                    template = settings.filenameTemplate,
                    artist = preferredArtist,
                    title = preferredTitle,
                    fallbackFromDisp = extractFilenameFromHeader(contentDisp),
                    extension = ext
                )
                val mimeType = resolveMimeType(ext, contentType, settings.format, settings.quality)
                val qualityBadge = buildQualityBadge(settings.quality, ext)

                val savedUri = streamBodyToDestination(
                    inputStream = body.byteStream(),
                    totalBytes = totalBytes,
                    fileName = fileName,
                    mimeType = mimeType,
                    settings = settings,
                    onProgress = onProgress
                )

                Result.success(
                    SealDownloadOutcome(
                        savedUri = savedUri.first,
                        resolvedFileName = fileName,
                        resolvedMediaUrl = targetUrl,
                        resolvedQualityLabel = qualityBadge,
                        bytesWritten = savedUri.second
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Download error for $mediaUrl", e)
            Result.failure(Exception(e.localizedMessage ?: "Download fehlgeschlagen."))
        }
    }

    private suspend fun downloadDirectStream(
        streamUrl: String,
        preferredTitle: String,
        preferredArtist: String,
        settings: SealSettings,
        onProgress: (Long, Long) -> Unit
    ): Result<SealDownloadOutcome> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(streamUrl)
            .header("User-Agent", "DotEQ-LinkDownloader/1.0 (Android)")
            .build()
        httpClient.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Audio-Stream Fehler (HTTP ${response.code})."))
            }
            val body = response.body ?: return@withContext Result.failure(Exception("Leerer Audio-Stream."))
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            if (contentType.contains("text/html")) {
                return@withContext Result.failure(Exception("Kein direkter Audio-Stream verfügbar."))
            }
            val totalBytes = body.contentLength()
            val ext = resolveExtension(streamUrl, contentType, settings.format, settings.quality)
            val fileName = formatTrackFilename(
                template = settings.filenameTemplate,
                artist = preferredArtist,
                title = preferredTitle,
                fallbackFromDisp = extractFilenameFromHeader(response.header("Content-Disposition").orEmpty()),
                extension = ext
            )
            val mimeType = resolveMimeType(ext, contentType, settings.format, settings.quality)
            val qualityBadge = buildQualityBadge(settings.quality, ext)
            val (uri, bytes) = streamBodyToDestination(
                inputStream = body.byteStream(),
                totalBytes = totalBytes,
                fileName = fileName,
                mimeType = mimeType,
                settings = settings,
                onProgress = onProgress
            )
            Result.success(
                SealDownloadOutcome(
                    savedUri = uri,
                    resolvedFileName = fileName,
                    resolvedMediaUrl = streamUrl,
                    resolvedQualityLabel = qualityBadge,
                    bytesWritten = bytes
                )
            )
        }
    }

    private suspend fun streamBodyToDestination(
        inputStream: InputStream,
        totalBytes: Long,
        fileName: String,
        mimeType: String,
        settings: SealSettings,
        onProgress: (Long, Long) -> Unit
    ): Pair<Uri, Long> = coroutineScope {
        // 1. User-selected SAF folder on device
        if (settings.saveLocation == SealSaveLocation.CUSTOM && !settings.customFolderUri.isNullOrBlank()) {
            val safResult = runCatching {
                val treeUri = Uri.parse(settings.customFolderUri)
                val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
                val docUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentUri,
                    mimeType,
                    fileName
                ) ?: return@runCatching null

                var written = 0L
                context.contentResolver.openOutputStream(docUri)?.use { out ->
                    written = copyWithProgress(inputStream, out, totalBytes, onProgress)
                } ?: run {
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, docUri) }
                    return@runCatching null
                }
                docUri to written
            }.getOrNull()

            if (safResult != null) {
                return@coroutineScope safResult
            }
        }

        // 2. Scoped MediaStore Music/Downloads folder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val isDownloads = settings.saveLocation == SealSaveLocation.DOWNLOADS
            val collection = if (isDownloads) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, settings.saveLocation.relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val insertedUri = resolver.insert(collection, values)
            if (insertedUri != null) {
                var written = 0L
                try {
                    resolver.openOutputStream(insertedUri)?.use { out ->
                        written = copyWithProgress(inputStream, out, totalBytes, onProgress)
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(insertedUri, values, null, null)
                    onProgress(written, maxOf(totalBytes, written))
                    return@coroutineScope insertedUri to written
                } catch (t: Throwable) {
                    runCatching { resolver.delete(insertedUri, null, null) }
                    if (t is CancellationException) throw t
                }
            }
        }

        // 3. App-private FileProvider fallback directory (e.g. legacy API / sandbox tests)
        val outDir = File(context.filesDir, "link_downloads").apply { mkdirs() }
        val targetFile = File(outDir, fileName)
        var written = 0L
        FileOutputStream(targetFile).use { out ->
            written = copyWithProgress(inputStream, out, totalBytes, onProgress)
        }
        onProgress(written, maxOf(totalBytes, written))
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
        }.getOrElse { Uri.fromFile(targetFile) }
        uri to written
    }

    private suspend fun copyWithProgress(
        inputStream: InputStream,
        out: java.io.OutputStream,
        totalBytes: Long,
        onProgress: (Long, Long) -> Unit
    ): Long = coroutineScope {
        var written = 0L
        val buffer = ByteArray(16 * 1024)
        var lastReport = 0L
        while (true) {
            ensureActive()
            val read = inputStream.read(buffer)
            if (read == -1) break
            out.write(buffer, 0, read)
            written += read
            if (written - lastReport >= 24_576L || read < buffer.size) {
                lastReport = written
                onProgress(written, totalBytes)
            }
        }
        out.flush()
        onProgress(written, maxOf(totalBytes, written))
        written
    }

    private fun fetchAudiusTracks(encodedQuery: String, settings: SealSettings): List<SealTrackSuggestion> {
        val url = "https://discoveryprovider.audius.co/v1/tracks/search?query=$encodedQuery&limit=5&app_name=DotEQ"
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "DotEQ-LinkDownloader/1.0")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string().orEmpty()
            return parseAudiusSearchJson(body, settings)
        }
    }

    private fun fetchItunesTracks(encodedQuery: String, settings: SealSettings): List<SealTrackSuggestion> {
        val url = "https://itunes.apple.com/search?term=$encodedQuery&media=music&entity=song&limit=6"
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "DotEQ-LinkDownloader/1.0")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string().orEmpty()
            return parseItunesSearchJson(body, settings)
        }
    }

    companion object {
        private fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        internal fun resolveFolderDisplayName(uri: Uri): String {
            val rawDocId = runCatching { DocumentsContract.getTreeDocumentId(uri) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment.orEmpty()
            val cleaned = rawDocId
                .substringAfter(':', rawDocId)
                .trim('/')
            return cleaned.ifBlank { "Eigener Ordner" }
        }

        internal fun buildQualityBadge(quality: SealAudioQuality, ext: String): String {
            val upperExt = ext.uppercase()
            return when (quality) {
                SealAudioQuality.AUTO -> "AUTO · $upperExt"
                SealAudioQuality.LOW -> "LOW · $upperExt"
                SealAudioQuality.MEDIUM -> "MEDIUM · $upperExt"
                SealAudioQuality.LOSSLESS -> "LOSSLESS · $upperExt"
            }
        }

        internal fun parseItunesSearchJson(
            rawJson: String,
            settings: SealSettings = SealSettings()
        ): List<SealTrackSuggestion> {
            if (rawJson.isBlank()) return emptyList()
            val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return emptyList()
            val arr = root.optJSONArray("results") ?: return emptyList()
            val out = mutableListOf<SealTrackSuggestion>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val title = obj.optString("trackName").trim()
                val artist = obj.optString("artistName").trim()
                val preview = obj.optString("previewUrl").trim()
                val viewUrl = obj.optString("trackViewUrl").trim().ifBlank { preview }
                if (title.isBlank() || preview.isBlank()) continue
                val durationMs = obj.optLong("trackTimeMillis", 0L)
                val ext = if (settings.format == SealAudioFormat.ORIGINAL) {
                    "m4a"
                } else {
                    settings.format.extension
                }
                out += SealTrackSuggestion(
                    id = "itunes_${obj.optLong("trackId", i.toLong())}",
                    title = title,
                    artist = artist.ifBlank { "Unknown" },
                    album = obj.optString("collectionName").trim(),
                    sourceLabel = "Apple / iTunes M4A",
                    shareUrl = viewUrl,
                    downloadUrl = preview,
                    durationLabel = formatDurationMs(durationMs),
                    suggestedFilename = formatTrackFilename(
                        template = settings.filenameTemplate,
                        artist = artist,
                        title = title,
                        fallbackFromDisp = null,
                        extension = ext
                    )
                )
            }
            return out
        }

        internal fun parseAudiusSearchJson(
            rawJson: String,
            settings: SealSettings = SealSettings()
        ): List<SealTrackSuggestion> {
            if (rawJson.isBlank()) return emptyList()
            val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return emptyList()
            val arr = root.optJSONArray("data") ?: return emptyList()
            val out = mutableListOf<SealTrackSuggestion>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id").trim()
                val title = obj.optString("title").trim()
                val userObj = obj.optJSONObject("user")
                val artist = userObj?.optString("name")?.trim().orEmpty()
                val permalink = obj.optString("permalink").trim()
                val isStreamable = obj.optBoolean("is_streamable", true)
                if (id.isBlank() || title.isBlank() || !isStreamable) continue

                val streamUrl = "https://discoveryprovider.audius.co/v1/tracks/$id/stream?app_name=DotEQ"
                val shareUrl = if (permalink.startsWith("http")) {
                    permalink
                } else if (permalink.isNotBlank()) {
                    "https://audius.co$permalink"
                } else {
                    streamUrl
                }
                val durationSec = obj.optInt("duration", 0)
                val ext = if (settings.format == SealAudioFormat.ORIGINAL) {
                    "mp3"
                } else {
                    settings.format.extension
                }
                out += SealTrackSuggestion(
                    id = "audius_$id",
                    title = title,
                    artist = artist.ifBlank { "Audius Artist" },
                    album = obj.optString("genre").trim(),
                    sourceLabel = "Audius Full MP3",
                    shareUrl = shareUrl,
                    downloadUrl = streamUrl,
                    durationLabel = formatDurationMs(durationSec * 1000L),
                    suggestedFilename = formatTrackFilename(
                        template = settings.filenameTemplate,
                        artist = artist,
                        title = title,
                        fallbackFromDisp = null,
                        extension = ext
                    )
                )
            }
            return out
        }

        internal fun extractDirectAudioUrlFromHtml(
            html: String,
            quality: SealAudioQuality = SealAudioQuality.AUTO
        ): String? {
            val candidates = linkedSetOf<String>()

            Regex("""property=["']og:audio(?::secure_url)?["'][^>]*content=["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
                .findAll(html).forEach { it.groupValues.getOrNull(1)?.let(candidates::add) }

            Regex("""<(?:audio|source)[^>]*src=["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
                .findAll(html).forEach { it.groupValues.getOrNull(1)?.let(candidates::add) }

            Regex("""https?://[^\s"'<>]+\.(?:flac|wav|alac|m4a|aac|opus|ogg|mp3)(?:\?[^\s"'<>]*)?""", RegexOption.IGNORE_CASE)
                .findAll(html).forEach { candidates.add(it.value) }

            if (candidates.isEmpty()) return null
            if (candidates.size == 1) return candidates.first()

            fun scoreUrl(url: String): Int {
                val low = url.lowercase()
                val baseRank = when {
                    low.contains(".flac") || low.contains(".wav") || low.contains(".alac") -> 100
                    low.contains(".m4a") || low.contains(".aac") -> 80
                    low.contains(".opus") || low.contains(".ogg") -> 70
                    low.contains(".mp3") -> 60
                    else -> 50
                }
                return when (quality) {
                    SealAudioQuality.AUTO, SealAudioQuality.LOSSLESS -> baseRank
                    SealAudioQuality.MEDIUM -> if (low.contains(".m4a") || low.contains(".mp3")) baseRank + 30 else baseRank
                    SealAudioQuality.LOW -> 150 - baseRank
                }
            }

            return candidates.maxByOrNull(::scoreUrl)
        }

        internal fun formatTrackFilename(
            template: String,
            artist: String,
            title: String,
            fallbackFromDisp: String?,
            extension: String
        ): String {
            val safeTitle = sanitizeFileSegment(title).ifBlank {
                sanitizeFileSegment(fallbackFromDisp?.substringBeforeLast('.').orEmpty()).ifBlank { "doteq_track" }
            }
            val safeArtist = sanitizeFileSegment(artist)
            val baseName = template
                .replace("%(artist)s", safeArtist.ifBlank { "DotEQ" })
                .replace("%(title)s", safeTitle)
                .replace(Regex("""%\([^)]+\)s"""), "")
                .trim(' ', '-', '_')
                .ifBlank { if (safeArtist.isNotBlank()) "$safeArtist - $safeTitle" else safeTitle }
            val safeExt = extension.lowercase().trimStart('.').ifBlank { "m4a" }
            return "$baseName.$safeExt"
        }

        private fun sanitizeFileSegment(raw: String): String =
            raw.replace(Regex("""[\\/:*?"<>|]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .take(48)

        private fun extractFilenameFromHeader(contentDisposition: String): String? {
            return Regex("""filename\*?=(?:UTF-8'')?["']?([^"';]+)["']?""", RegexOption.IGNORE_CASE)
                .find(contentDisposition)?.groupValues?.getOrNull(1)?.trim()
        }

        private fun resolveExtension(
            url: String,
            contentType: String,
            preferred: SealAudioFormat,
            quality: SealAudioQuality
        ): String {
            val detected = when {
                contentType.contains("flac") || url.contains(".flac", ignoreCase = true) -> "flac"
                contentType.contains("wav") || url.contains(".wav", ignoreCase = true) -> "wav"
                contentType.contains("mp4") || contentType.contains("aac") || url.contains(".m4a", ignoreCase = true) -> "m4a"
                contentType.contains("ogg") || contentType.contains("opus") || url.contains(".ogg", ignoreCase = true) -> "ogg"
                contentType.contains("mpeg") || url.contains(".mp3", ignoreCase = true) -> "mp3"
                else -> null
            }

            // AUTO / ORIGINAL always pick the highest native format provided by the link
            if (quality == SealAudioQuality.AUTO || preferred == SealAudioFormat.ORIGINAL) {
                return detected ?: if (preferred != SealAudioFormat.ORIGINAL) preferred.extension else "m4a"
            }

            // LOSSLESS preserves lossless container from link or uses FLAC
            if (quality == SealAudioQuality.LOSSLESS) {
                return if (detected == "flac" || detected == "wav") detected else SealAudioFormat.FLAC.extension
            }

            return preferred.extension
        }

        private fun resolveMimeType(
            ext: String,
            serverMime: String,
            preferred: SealAudioFormat,
            quality: SealAudioQuality
        ): String {
            if (quality != SealAudioQuality.AUTO &&
                quality != SealAudioQuality.LOSSLESS &&
                preferred != SealAudioFormat.ORIGINAL
            ) {
                return preferred.mimeType
            }
            return when (ext.lowercase()) {
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "flac" -> "audio/flac"
                "ogg", "opus" -> "audio/ogg"
                "wav" -> "audio/wav"
                else -> serverMime.substringBefore(';').ifBlank { "audio/mp4" }
            }
        }

        internal fun formatDurationMs(ms: Long): String {
            if (ms <= 0L) return ""
            val totalSec = ms / 1000L
            val min = totalSec / 60L
            val sec = totalSec % 60L
            return "%d:%02d".format(min, sec)
        }
    }
}
