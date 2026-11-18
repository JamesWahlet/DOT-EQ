package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ai.AiChatMessage
import com.example.ai.AiSoundRecommendation
import com.example.ai.VoiceCaptureState
import com.example.audio.AudioMath
import com.example.audio.BluetoothAudioState
import com.example.auth.AuthenticatedUser
import com.example.model.EqState
import com.example.model.SealDownloadStatus
import com.example.model.SealDownloadTask
import com.example.model.SealTrackSuggestion
import com.example.ui.components.EssentialSpaceLogo
import com.example.ui.components.NothingCard
import com.example.ui.components.NothingVoiceWaveformView
import com.example.ui.theme.LocalNothingColors
import com.example.ui.theme.NothingRed

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiTunerScreen(
    authUser: AuthenticatedUser?,
    isAuthLoading: Boolean,
    authError: String?,
    eqState: EqState,
    bluetoothState: BluetoothAudioState,
    chatMessages: List<AiChatMessage>,
    isSending: Boolean,
    voiceState: VoiceCaptureState,
    sealDownloads: List<SealDownloadTask> = emptyList(),
    onSignInGoogle: () -> Unit,
    onSignOutGoogle: () -> Unit,
    onSendMessage: (String) -> Unit,
    onStartVoiceInput: () -> Unit,
    onStopAndSendVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onResetChat: () -> Unit,
    onApplyRecommendation: (AiSoundRecommendation) -> Unit,
    onSaveAiPreset: (AiSoundRecommendation) -> Unit,
    onCopySongLink: (SealTrackSuggestion) -> Unit = {},
    onDownloadSong: (SealTrackSuggestion) -> Unit = {},
    onOpenSealDownloader: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalNothingColors.current
    var prompt by rememberSaveable { mutableStateOf("") }
    var voicePermissionDenied by rememberSaveable { mutableStateOf(false) }
    val chatScrollState = rememberScrollState()

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voicePermissionDenied = false
            onStartVoiceInput()
        } else {
            voicePermissionDenied = true
        }
    }

    val quickPrompts = listOf(
        "Mehr Bass & Ultra Bass an",
        "Song mit Link Downloader laden",
        "Balance rechts & Crossover",
        "Preset für Bluetooth"
    )

    LaunchedEffect(chatMessages.size, isSending, voiceState.isListening) {
        chatScrollState.animateScrollTo(chatScrollState.maxValue)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (authUser == null) {
            // Minimal Essential-Space Style Google Sign-In Gate (clean rounded frame, no 90° brackets)
            NothingCard(
                backgroundColor = colors.frostedCard,
                cornerRadius = 30.dp,
                contentPadding = 24.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(colors.pillTrack)
                            .border(1.dp, colors.yellow.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        EssentialSpaceLogo(
                            color = colors.yellow,
                            size = 34.dp
                        )
                    }
                    Text(
                        text = "GOOGLE GEMINI",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.inkPrimary
                    )
                    Text(
                        text = "Mit Google anmelden",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.inkSecondary
                    )

                    if (!authError.isNullOrBlank()) {
                        Text(
                            text = authError,
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingRed
                        )
                    }

                    Button(
                        onClick = onSignInGoogle,
                        enabled = !isAuthLoading,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.inkPrimary,
                            contentColor = colors.pillActiveContent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("ai_google_signin_button")
                    ) {
                        if (isAuthLoading) {
                            CircularProgressIndicator(
                                color = colors.pillActiveContent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text("MIT GOOGLE ANMELDEN", maxLines = 1)
                    }
                }
            }
        } else {
            // Compact Signed-In Google Account + Frosted Essential Header
            NothingCard(
                backgroundColor = colors.frostedCard,
                contentPadding = 14.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.pillTrack)
                            .border(1.dp, colors.yellow.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        EssentialSpaceLogo(
                            color = colors.yellow,
                            size = 20.dp
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = authUser.displayName ?: "GOOGLE GEMINI",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.inkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        authUser.email?.let { email ->
                            Text(
                                text = email,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (chatMessages.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.pillTrack)
                                .clickable { onResetChat() }
                                .testTag("ai_reset_chat_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Chat leeren",
                                tint = colors.inkPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.pillTrack)
                            .clickable { onSignOutGoogle() }
                            .testTag("ai_signout_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Abmelden",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // Clean Minimalist Chat Thread
            NothingCard(contentPadding = 14.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 210.dp, max = 340.dp)
                        .verticalScroll(chatScrollState)
                        .testTag("ai_chat_thread"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (chatMessages.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.pillTrack)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            EssentialSpaceLogo(color = colors.yellow, size = 16.dp)
                            Text(
                                text = buildString {
                                    append(eqState.activePresetName)
                                    bluetoothState.connectedDevice?.let { append(" · ${it.name}") }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkSecondary
                            )
                        }
                    } else {
                        chatMessages.forEach { msg ->
                            if (msg.isUser) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .widthIn(max = 280.dp)
                                            .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                                            .background(colors.inkPrimary)
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = msg.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.pillActiveContent
                                        )
                                    }
                                }
                            } else {
                                AssistantChatBubble(
                                    message = msg,
                                    onApplyRecommendation = onApplyRecommendation,
                                    onSaveAiPreset = onSaveAiPreset,
                                    onCopySongLink = { track ->
                                        copyToClipboard(context, track.shareUrl)
                                        onCopySongLink(track)
                                    },
                                    onDownloadSong = onDownloadSong
                                )
                            }
                        }
                    }

                    if (isSending) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.pillTrack)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = colors.inkPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "...",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkSecondary
                            )
                        }
                    }
                }
            }

            // Active / recent Seal download banner when user triggers a download from KI
            val latestDownload = sealDownloads.firstOrNull()
            if (latestDownload != null &&
                (latestDownload.status == SealDownloadStatus.DOWNLOADING ||
                    latestDownload.status == SealDownloadStatus.COMPLETED ||
                    latestDownload.status == SealDownloadStatus.FAILED)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.frostedCard)
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                        .clickable { onOpenSealDownloader() }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("ai_seal_status_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        tint = when (latestDownload.status) {
                            SealDownloadStatus.COMPLETED -> colors.green
                            SealDownloadStatus.FAILED -> NothingRed
                            else -> colors.inkPrimary
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = latestDownload.fileName,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.inkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (latestDownload.status == SealDownloadStatus.DOWNLOADING) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { latestDownload.progress.coerceIn(0.05f, 1f) },
                                color = colors.inkPrimary,
                                trackColor = colors.cardBorder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                        } else {
                            Text(
                                text = when (latestDownload.status) {
                                    SealDownloadStatus.COMPLETED -> "Gespeichert · Link Downloader öffnen"
                                    SealDownloadStatus.FAILED -> latestDownload.errorMessage ?: "Download fehlgeschlagen"
                                    else -> latestDownload.status.name
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Essential Space Voice Capture Bar when actively listening
            AnimatedVisibility(visible = voiceState.isListening) {
                val recPulse by rememberInfiniteTransition(label = "voice_rec_dot").animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "rec_alpha"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.frostedCard)
                        .border(1.2.dp, colors.yellow.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("ai_voice_listening_pill")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(NothingRed.copy(alpha = recPulse))
                            )
                            NothingVoiceWaveformView(
                                amplitude = voiceState.amplitude,
                                barColor = colors.yellow,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(colors.pillTrack)
                                    .clickable { onCancelVoice() }
                                    .testTag("ai_voice_cancel_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Spracheingabe abbrechen",
                                    tint = colors.inkSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(NothingRed)
                                    .clickable { onStopAndSendVoice() }
                                    .testTag("ai_voice_stop_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = "Aufnahme beenden & senden",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (voiceState.partialTranscript.isNotBlank()) {
                            Text(
                                text = voiceState.partialTranscript,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (voicePermissionDenied) {
                Text(
                    text = "Mikrofon-Berechtigung für Voice-KI erforderlich.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingRed
                )
            } else if (!voiceState.errorMessage.isNullOrBlank()) {
                Text(
                    text = voiceState.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingRed
                )
            }

            // Minimal Quick Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPrompts.forEachIndexed { idx, chipText ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.card)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(50))
                            .clickable(enabled = !isSending && !voiceState.isListening) {
                                onSendMessage(chipText)
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                            .testTag("ai_chip_$idx")
                    ) {
                        Text(
                            text = chipText,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkPrimary
                        )
                    }
                }
            }

            // Composer with Essential Voice Mic Button + Send Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = {
                        Text(
                            text = "Sprechen oder tippen...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.inkMuted
                        )
                    },
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.inkPrimary,
                        unfocusedBorderColor = colors.cardBorder,
                        focusedContainerColor = colors.card,
                        unfocusedContainerColor = colors.card,
                        focusedTextColor = colors.inkPrimary,
                        unfocusedTextColor = colors.inkPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_prompt_input")
                )

                // Essential Space Voice Trigger Button
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (voiceState.isListening) colors.yellow else colors.pillTrack)
                        .border(
                            width = 1.2.dp,
                            color = if (voiceState.isListening) colors.yellow else colors.cardBorder,
                            shape = CircleShape
                        )
                        .clickable(enabled = !isSending) {
                            if (voiceState.isListening) {
                                onStopAndSendVoice()
                            } else {
                                val hasMicPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMicPerm) {
                                    voicePermissionDenied = false
                                    onStartVoiceInput()
                                } else {
                                    micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                        .testTag("ai_voice_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (voiceState.isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (voiceState.isListening) "Voice senden" else "Voice KI starten",
                        tint = if (voiceState.isListening) Color(0xFF15171A) else colors.inkPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            if (prompt.isNotBlank() && !isSending) colors.inkPrimary else colors.pillTrack
                        )
                        .border(
                            1.dp,
                            if (prompt.isNotBlank() && !isSending) colors.inkPrimary else colors.cardBorder,
                            CircleShape
                        )
                        .clickable(enabled = prompt.isNotBlank() && !isSending) {
                            val outgoing = prompt
                            prompt = ""
                            onSendMessage(outgoing)
                        }
                        .testTag("ai_generate_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            color = colors.inkPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Senden",
                            tint = if (prompt.isNotBlank()) colors.pillActiveContent else colors.inkMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantChatBubble(
    message: AiChatMessage,
    onApplyRecommendation: (AiSoundRecommendation) -> Unit,
    onSaveAiPreset: (AiSoundRecommendation) -> Unit,
    onCopySongLink: (SealTrackSuggestion) -> Unit,
    onDownloadSong: (SealTrackSuggestion) -> Unit
) {
    val colors = LocalNothingColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
            .background(colors.pillTrack)
            .border(
                width = 1.dp,
                color = if (message.isError) NothingRed else colors.cardBorder,
                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (message.appliedBadges.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                message.appliedBadges.forEach { badge ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.inkPrimary)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.pillActiveContent
                        )
                    }
                }
            }
        }

        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (message.isError) NothingRed else colors.inkPrimary
        )

        if (message.previewBandsDb != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.graphBackdrop)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val zeroY = h * 0.5f
                    drawLine(
                        colors.inkMuted.copy(alpha = 0.45f),
                        Offset(8f, zeroY),
                        Offset(w - 8f, zeroY),
                        1.dp.toPx()
                    )
                    val path = Path()
                    for (s in 0..48) {
                        val nx = s / 48f
                        val freq = AudioMath.normToFreq(nx)
                        val db = AudioMath.evaluateEqCurveDb(freq, message.previewBandsDb, 0f)
                            .coerceIn(-12f, 12f)
                        val x = nx * w
                        val y = 10f + (1f - (db + 12f) / 24f) * (h - 20f)
                        if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = colors.inkPrimary,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        val rec = message.recommendation
        if (rec != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onApplyRecommendation(rec) },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("ai_apply_button")
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ANWENDEN", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { onSaveAiPreset(rec) },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("ai_save_button")
                ) {
                    Icon(
                        Icons.Filled.BookmarkAdd,
                        contentDescription = "Als Preset speichern",
                        tint = colors.inkPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (message.downloadLookupError != null) {
            Text(
                text = message.downloadLookupError,
                style = MaterialTheme.typography.bodySmall,
                color = NothingRed
            )
        }

        if (message.downloadSuggestions.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                message.downloadSuggestions.forEachIndexed { idx, track ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.card)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
                            .padding(10.dp)
                            .testTag("ai_seal_suggestion_card_$idx"),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.inkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = track.sourceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkSecondary
                            )
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
                                onClick = { onCopySongLink(track) },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("ai_seal_copy_link_$idx")
                            ) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("LINK KOPIEREN", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                            Button(
                                onClick = { onDownloadSong(track) },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.inkPrimary,
                                    contentColor = colors.pillActiveContent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("ai_seal_download_$idx")
                            ) {
                                Icon(
                                    Icons.Filled.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("DOWNLOAD", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
