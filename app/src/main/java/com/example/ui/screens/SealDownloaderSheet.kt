package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.SealAudioFormat
import com.example.model.SealAudioQuality
import com.example.model.SealDownloadStatus
import com.example.model.SealDownloadTask
import com.example.model.SealSaveLocation
import com.example.model.SealSettings
import com.example.model.SealTrackSuggestion
import com.example.seal.SealMediaRepository
import com.example.ui.components.NothingCard
import com.example.ui.components.NothingSectionHeader
import com.example.ui.theme.LocalNothingColors
import com.example.ui.theme.NothingRed

@Composable
fun SealDownloaderSheet(
    sealSettings: SealSettings,
    downloads: List<SealDownloadTask>,
    searchResults: List<SealTrackSuggestion>,
    isSearching: Boolean,
    searchError: String?,
    onSearchSongs: (String) -> Unit,
    onDirectDownloadInput: (String, SealAudioQuality) -> Unit,
    onStartTrackDownload: (SealTrackSuggestion) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRemoveTask: (String) -> Unit,
    onClearHistory: () -> Unit,
    onUpdateFormat: (SealAudioFormat) -> Unit,
    onUpdateQuality: (SealAudioQuality) -> Unit,
    onUpdateSaveLocation: (SealSaveLocation) -> Unit,
    onUpdateCustomFolder: (Uri?, String) -> Unit,
    onUpdateFilenameTemplate: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalNothingColors.current
    var queryInput by rememberSaveable { mutableStateOf("") }
    var templateInput by rememberSaveable(sealSettings.filenameTemplate) {
        mutableStateOf(sealSettings.filenameTemplate)
    }
    var pendingLoadInput by remember { mutableStateOf<String?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val label = SealMediaRepository.resolveFolderDisplayName(uri)
            onUpdateCustomFolder(uri, label)
            onShowMessage("Speicherordner: $label")
        }
    }

    BackHandler {
        if (pendingLoadInput != null) {
            pendingLoadInput = null
        } else {
            onClose()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("seal_studio_sheet"),
        color = colors.canvas
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
            ) {
                // Header (strictly monochrome Nothing aesthetic, no blue accents)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "LINK DOWNLOADER",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.inkPrimary
                        )
                        Text(
                            text = "SONG SUCHE · LINK KOPIEREN · AUDIO DOWNLOAD",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.card)
                            .border(1.dp, colors.cardBorder, CircleShape)
                            .clickable { onClose() }
                            .testTag("seal_close_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Link Downloader schließen",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Search / Paste Link Card (no blue accents)
                    NothingCard(backgroundColor = colors.frostedCard) {
                        NothingSectionHeader(
                            title = "SONG ODER LINK",
                            trailingText = sealSettings.quality.label
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = queryInput,
                            onValueChange = { queryInput = it },
                            placeholder = {
                                Text(
                                    text = "Songname, Artist oder Audio-URL...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.inkMuted
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.inkPrimary,
                                unfocusedBorderColor = colors.cardBorder,
                                focusedContainerColor = colors.card,
                                unfocusedContainerColor = colors.card,
                                focusedTextColor = colors.inkPrimary,
                                unfocusedTextColor = colors.inkPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("seal_input_field")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val pasted = cm?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty().trim()
                                    if (pasted.isNotBlank()) {
                                        queryInput = pasted
                                        onShowMessage("Aus Zwischenablage eingefügt")
                                    } else {
                                        onShowMessage("Zwischenablage ist leer")
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("seal_paste_button")
                            ) {
                                Icon(
                                    Icons.Filled.ContentPaste,
                                    contentDescription = "Einfügen",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            OutlinedButton(
                                onClick = { onSearchSongs(queryInput) },
                                enabled = !isSearching && queryInput.isNotBlank(),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("seal_search_button")
                            ) {
                                if (isSearching) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = colors.inkPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text("SUCHEN", maxLines = 1)
                            }

                            Button(
                                onClick = { pendingLoadInput = queryInput.trim() },
                                enabled = !isSearching && queryInput.isNotBlank(),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.inkPrimary,
                                    contentColor = colors.pillActiveContent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("seal_download_now_button")
                            ) {
                                Icon(
                                    Icons.Filled.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("LOAD", maxLines = 1)
                            }
                        }

                        if (!searchError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = searchError,
                                style = MaterialTheme.typography.bodySmall,
                                color = NothingRed
                            )
                        }
                    }

                    // 2. Search Results with Copy Link & Download
                    if (searchResults.isNotEmpty()) {
                        NothingCard {
                            NothingSectionHeader(
                                title = "TREFFER",
                                trailingText = "${searchResults.size} TRACKS"
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.testTag("seal_search_results")
                            ) {
                                searchResults.forEachIndexed { idx, track ->
                                    LinkTrackResultCard(
                                        index = idx,
                                        track = track,
                                        onCopyLink = {
                                            copyToClipboard(context, track.shareUrl)
                                            onShowMessage("Link kopiert: ${track.title}")
                                        },
                                        onDownload = {
                                            onStartTrackDownload(track)
                                            onShowMessage("Download gestartet: ${track.title}")
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 3. Link Downloader Settings Card (Ordered by Relevance: Quality -> Folder -> Format -> Template)
                    NothingCard(modifier = Modifier.testTag("seal_config_card")) {
                        NothingSectionHeader(
                            title = "DOWNLOADER-EINSTELLUNGEN",
                            trailingText = sealSettings.quality.label
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // 3a. Quality (AUTO, LOW, MEDIUM, LOSSLESS) - Highest relevance
                        Text(
                            text = "QUALITÄT",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SealAudioQuality.entries.forEach { q ->
                                val selected = sealSettings.quality == q
                                Box(
                                    modifier = Modifier
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (selected) colors.inkPrimary else colors.pillTrack)
                                        .border(
                                            1.dp,
                                            if (selected) colors.inkPrimary else colors.cardBorder,
                                            RoundedCornerShape(50)
                                        )
                                        .clickable { onUpdateQuality(q) }
                                        .padding(horizontal = 16.dp)
                                        .testTag("seal_quality_${q.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = q.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) colors.pillActiveContent else colors.inkPrimary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = sealSettings.quality.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // 3b. Folder Selection on Device (SAF Folder Picker + Quick Presets)
                        Text(
                            text = "SPEICHERORDNER",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedButton(
                            onClick = { folderPickerLauncher.launch(null) },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("link_pick_folder_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CreateNewFolder,
                                contentDescription = null,
                                tint = colors.inkPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (sealSettings.saveLocation == SealSaveLocation.CUSTOM &&
                                    sealSettings.customFolderLabel.isNotBlank()
                                ) {
                                    "ORDNER: ${sealSettings.customFolderLabel}"
                                } else {
                                    "ORDNER AUF HANDY WÄHLEN"
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = colors.inkPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(SealSaveLocation.MUSIC, SealSaveLocation.DOWNLOADS).forEach { loc ->
                                val selected = sealSettings.saveLocation == loc
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (selected) colors.inkPrimary else colors.pillTrack)
                                        .border(
                                            1.dp,
                                            if (selected) colors.inkPrimary else colors.cardBorder,
                                            RoundedCornerShape(50)
                                        )
                                        .clickable { onUpdateSaveLocation(loc) }
                                        .testTag("seal_loc_${loc.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = loc.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) colors.pillActiveContent else colors.inkPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 3c. Audio Format
                        Text(
                            text = "AUDIO-FORMAT",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SealAudioFormat.entries.forEach { fmt ->
                                val selected = sealSettings.format == fmt
                                Box(
                                    modifier = Modifier
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (selected) colors.inkPrimary else colors.pillTrack)
                                        .border(
                                            1.dp,
                                            if (selected) colors.inkPrimary else colors.cardBorder,
                                            RoundedCornerShape(50)
                                        )
                                        .clickable { onUpdateFormat(fmt) }
                                        .padding(horizontal = 14.dp)
                                        .testTag("seal_format_${fmt.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = fmt.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) colors.pillActiveContent else colors.inkPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 3d. Filename Template (lowest relevance, at bottom)
                        OutlinedTextField(
                            value = templateInput,
                            onValueChange = {
                                templateInput = it
                                onUpdateFilenameTemplate(it)
                            },
                            label = { Text("Dateinamen-Template") },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.inkPrimary,
                                unfocusedBorderColor = colors.cardBorder,
                                focusedTextColor = colors.inkPrimary,
                                unfocusedTextColor = colors.inkPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("seal_template_input")
                        )
                    }

                    // 4. Downloads & History Card
                    NothingCard(modifier = Modifier.testTag("seal_downloads_card")) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            NothingSectionHeader(
                                title = "DOWNLOADS",
                                trailingText = "${downloads.size}",
                                modifier = Modifier.weight(1f)
                            )
                            if (downloads.isNotEmpty()) {
                                Text(
                                    text = "LEEREN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.inkSecondary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable { onClearHistory() }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (downloads.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(colors.pillTrack)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Noch keine Downloads. Suche einen Song oben, füge einen Link ein oder frage Gemini im KI-Tab.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.inkSecondary
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                downloads.forEachIndexed { idx, task ->
                                    LinkDownloadTaskCard(
                                        index = idx,
                                        task = task,
                                        onCopyLink = {
                                            copyToClipboard(context, task.resolvedMediaUrl.ifBlank { task.sourceUrl })
                                            onShowMessage("Download-Link kopiert")
                                        },
                                        onOpenFile = { uriStr ->
                                            openDownloadedAudio(context, uriStr, onShowMessage)
                                        },
                                        onCancel = { onCancelDownload(task.id) },
                                        onRemove = { onRemoveTask(task.id) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Quality Prompt Modal shown when user presses LOAD
            pendingLoadInput?.let { targetInput ->
                LinkQualityPromptDialog(
                    targetInput = targetInput,
                    initialQuality = sealSettings.quality,
                    onConfirm = { chosenQuality ->
                        pendingLoadInput = null
                        onDirectDownloadInput(targetInput, chosenQuality)
                    },
                    onDismiss = { pendingLoadInput = null }
                )
            }
        }
    }
}

@Composable
private fun LinkQualityPromptDialog(
    targetInput: String,
    initialQuality: SealAudioQuality,
    onConfirm: (SealAudioQuality) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalNothingColors.current
    var selectedQuality by remember(initialQuality) { mutableStateOf(initialQuality) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.frostedCard)
                    .border(1.2.dp, colors.cardBorder, RoundedCornerShape(28.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .testTag("link_quality_prompt_dialog"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NothingSectionHeader(
                    title = "QUALITÄT WÄHLEN",
                    trailingText = selectedQuality.label
                )

                Text(
                    text = targetInput,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SealAudioQuality.entries.forEach { quality ->
                        val isSelected = selectedQuality == quality
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) colors.inkPrimary else colors.pillTrack)
                                .border(
                                    1.dp,
                                    if (isSelected) colors.inkPrimary else colors.cardBorder,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedQuality = quality }
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                                .testTag("quality_prompt_${quality.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = quality.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) colors.pillActiveContent else colors.inkPrimary
                                )
                                Text(
                                    text = quality.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) {
                                        colors.pillActiveContent.copy(alpha = 0.8f)
                                    } else {
                                        colors.inkSecondary
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = colors.pillActiveContent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("quality_prompt_cancel_button")
                    ) {
                        Text("ABBRECHEN", maxLines = 1)
                    }
                    Button(
                        onClick = { onConfirm(selectedQuality) },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.inkPrimary,
                            contentColor = colors.pillActiveContent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("quality_prompt_confirm_button")
                    ) {
                        Text("LOAD STARTEN", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkTrackResultCard(
    index: Int,
    track: SealTrackSuggestion,
    onCopyLink: () -> Unit,
    onDownload: () -> Unit
) {
    val colors = LocalNothingColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.pillTrack)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
            .padding(14.dp)
            .testTag("seal_result_card_$index"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.inkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        track.artist.takeIf { it.isNotBlank() },
                        track.durationLabel.takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.card)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = track.sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkSecondary
                )
            }
        }

        Text(
            text = track.shareUrl,
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCopyLink,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("seal_copy_link_$index")
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("LINK KOPIEREN", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }

            Button(
                onClick = onDownload,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.inkPrimary,
                    contentColor = colors.pillActiveContent
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("seal_download_item_$index")
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("DOWNLOAD", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LinkDownloadTaskCard(
    index: Int,
    task: SealDownloadTask,
    onCopyLink: () -> Unit,
    onOpenFile: (String) -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = LocalNothingColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.pillTrack)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp))
            .padding(12.dp)
            .testTag("seal_task_card_$index"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.fileName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${task.formatLabel} · ${task.status.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = when (task.status) {
                        SealDownloadStatus.COMPLETED -> colors.green
                        SealDownloadStatus.FAILED -> NothingRed
                        SealDownloadStatus.DOWNLOADING -> colors.inkPrimary
                        else -> colors.inkSecondary
                    }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.card)
                        .clickable { onCopyLink() }
                        .testTag("seal_task_copy_$index"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Link kopieren",
                        tint = colors.inkPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                if (task.status == SealDownloadStatus.COMPLETED && !task.savedUriString.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.inkPrimary)
                            .clickable { onOpenFile(task.savedUriString) }
                            .testTag("seal_task_open_$index"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = "Audio öffnen",
                            tint = colors.pillActiveContent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.card)
                        .clickable {
                            if (task.status == SealDownloadStatus.DOWNLOADING) onCancel() else onRemove()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (task.status == SealDownloadStatus.DOWNLOADING) {
                            Icons.Filled.Close
                        } else {
                            Icons.Filled.DeleteOutline
                        },
                        contentDescription = "Entfernen",
                        tint = colors.inkSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        if (task.status == SealDownloadStatus.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { task.progress.coerceIn(0.05f, 1f) },
                color = colors.inkPrimary,
                trackColor = colors.cardBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
            )
        } else if (!task.errorMessage.isNullOrBlank()) {
            Text(
                text = task.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = NothingRed
            )
        }
    }
}

internal fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("DotEQ Audio Link", text))
}

internal fun openDownloadedAudio(
    context: Context,
    uriString: String,
    onError: (String) -> Unit
) {
    runCatching {
        val uri = Uri.parse(uriString)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "audio/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(viewIntent, "Audio öffnen").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }.onFailure {
        onError("Kein Audio-Player zum Öffnen gefunden.")
    }
}
