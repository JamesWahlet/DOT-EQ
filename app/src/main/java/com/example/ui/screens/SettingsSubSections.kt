package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.auth.AuthenticatedUser
import com.example.i18n.LocalAppStrings
import com.example.model.AppLanguage
import com.example.model.GitHubUpdateState
import com.example.ui.components.NothingCard
import com.example.ui.components.NothingSectionHeader
import com.example.ui.theme.LocalNothingColors
import com.example.update.GitHubUpdateRepository

@Composable
fun SettingsSubgroupBanner(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String
) {
    val colors = LocalNothingColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp, start = 4.dp, end = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(colors.pillTrack)
                .border(1.dp, colors.cardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.inkPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colors.inkPrimary,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AccountSettingsCard(
    authUser: AuthenticatedUser?,
    isAuthLoading: Boolean,
    authError: String?,
    onSignInGoogle: () -> Unit,
    onSignOutGoogle: () -> Unit
) {
    val colors = LocalNothingColors.current
    val strings = LocalAppStrings.current

    NothingCard(modifier = Modifier.testTag("settings_account_card")) {
        NothingSectionHeader(
            title = strings.accountSectionTitle,
            trailingText = if (authUser != null) strings.accountConnected else null
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (authUser != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.pillTrack)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = null,
                    tint = colors.inkPrimary,
                    modifier = Modifier.size(32.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = authUser.displayName ?: "Google Konto",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!authUser.email.isNullOrBlank()) {
                        Text(
                            text = authUser.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onSignOutGoogle,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("settings_account_signout_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.accountSignOutBtn, color = colors.inkPrimary)
            }
        } else {
            Text(
                text = strings.accountSignedOut,
                style = MaterialTheme.typography.titleMedium,
                color = colors.inkPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.accountSignedOutDesc,
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkSecondary
            )
            if (!authError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = authError,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.red
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
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
                    .height(48.dp)
                    .testTag("settings_account_signin_button")
            ) {
                if (isAuthLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colors.pillActiveContent
                    )
                } else {
                    Icon(Icons.Filled.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(strings.accountSignInBtn)
            }
        }
    }
}

@Composable
fun LanguageSettingsCard(
    appLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit
) {
    val colors = LocalNothingColors.current
    val strings = LocalAppStrings.current

    NothingCard(modifier = Modifier.testTag("settings_language_card")) {
        NothingSectionHeader(
            title = strings.languageSectionTitle,
            trailingText = appLanguage.label
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = strings.languageSectionDesc,
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppLanguage.entries.forEach { lang ->
                val selected = appLanguage == lang
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) colors.pillActiveContainer else colors.pillTrack)
                        .border(
                            width = 1.dp,
                            color = if (selected) colors.inkPrimary else colors.cardBorder,
                            shape = RoundedCornerShape(50)
                        )
                        .clickable { onSelectLanguage(lang) }
                        .testTag("settings_lang_${lang.code}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = null,
                            tint = if (selected) colors.pillActiveContent else colors.inkPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = lang.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) colors.pillActiveContent else colors.inkPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoSettingsCard(
    githubRepo: String,
    updateState: GitHubUpdateState,
    onCheckForUpdates: () -> Unit,
    onUpdateGithubRepo: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val colors = LocalNothingColors.current
    val strings = LocalAppStrings.current
    var editingRepo by remember { mutableStateOf(false) }
    var repoInput by remember(githubRepo) { mutableStateOf(githubRepo) }

    NothingCard(modifier = Modifier.testTag("settings_info_card")) {
        NothingSectionHeader(
            title = strings.infoSectionTitle,
            trailingText = "${strings.infoVersionLabel} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        )
        Spacer(modifier = Modifier.height(10.dp))

        // App Website Card / Action
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.pillTrack)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Public,
                    contentDescription = null,
                    tint = colors.inkPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DotEQ Studio",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkPrimary
                    )
                    Text(
                        text = strings.infoWebsiteUrlLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkSecondary
                    )
                }
            }

            Button(
                onClick = {
                    openExternalUrl(
                        context = context,
                        url = GitHubUpdateRepository.DEFAULT_WEBSITE_URL,
                        onError = onShowMessage
                    )
                },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.inkPrimary,
                    contentColor = colors.pillActiveContent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("info_open_website_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.infoOpenWebsiteBtn)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // GitHub Connection & Update Announcer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.pillTrack)
                .border(
                    width = 1.dp,
                    color = if (updateState is GitHubUpdateState.UpdateAvailable) colors.yellow else colors.cardBorder,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(14.dp)
                .testTag("info_github_card"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = null,
                        tint = colors.inkPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.infoGithubTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkPrimary
                        )
                        Text(
                            text = "${strings.infoGithubRepoLabel}: $githubRepo",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = { editingRepo = !editingRepo },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("info_edit_repo_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "GitHub Repository bearbeiten",
                        tint = colors.inkSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (editingRepo) {
                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    label = { Text("owner/repository") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.inkPrimary,
                        unfocusedBorderColor = colors.cardBorder,
                        focusedTextColor = colors.inkPrimary,
                        unfocusedTextColor = colors.inkPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("info_repo_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            repoInput = githubRepo
                            editingRepo = false
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(strings.cancel, color = colors.inkSecondary)
                    }
                    Button(
                        onClick = {
                            onUpdateGithubRepo(repoInput)
                            editingRepo = false
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.inkPrimary,
                            contentColor = colors.pillActiveContent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("info_repo_save_button")
                    ) {
                        Text(strings.save)
                    }
                }
            }

            // Update status readout
            when (updateState) {
                GitHubUpdateState.Idle -> {
                    Text(
                        text = "GitHub Releases API · v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkSecondary
                    )
                }
                GitHubUpdateState.Checking -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.inkPrimary
                        )
                        Text(
                            text = strings.infoCheckingUpdates,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkPrimary
                        )
                    }
                }
                is GitHubUpdateState.UpdateAvailable -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.card)
                            .border(1.5.dp, colors.yellow, RoundedCornerShape(16.dp))
                            .padding(12.dp)
                            .testTag("info_update_banner"),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NewReleases,
                                contentDescription = null,
                                tint = colors.yellow,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${strings.infoUpdateAvailablePrefix}: ${updateState.release.tagName}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkPrimary
                            )
                        }
                        if (updateState.release.notes.isNotBlank()) {
                            Text(
                                text = updateState.release.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkSecondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = {
                                openExternalUrl(context, updateState.release.htmlUrl, onShowMessage)
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.inkPrimary,
                                contentColor = colors.pillActiveContent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("info_open_release_button")
                        ) {
                            Text(strings.infoOpenReleaseBtn)
                        }
                    }
                }
                is GitHubUpdateState.UpToDate -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (colors.isMonochrome) colors.inkPrimary else colors.green,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${strings.infoUpToDateLabel} (${updateState.latestVersion})",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkPrimary
                        )
                    }
                }
                is GitHubUpdateState.Error -> {
                    Text(
                        text = updateState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCheckForUpdates,
                    enabled = updateState !is GitHubUpdateState.Checking,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("info_check_updates_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = strings.infoCheckUpdatesBtn,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = colors.inkPrimary
                    )
                }
                OutlinedButton(
                    onClick = {
                        openExternalUrl(
                            context = context,
                            url = GitHubUpdateRepository.repoWebUrl(githubRepo),
                            onError = onShowMessage
                        )
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("info_open_github_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = strings.infoOpenGithubBtn,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = colors.inkPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Full App Features Overview
        NothingSectionHeader(title = strings.infoFeaturesTitle)
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            strings.infoFeatureList.forEach { (featureTitle, featureDesc) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.pillTrack)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = featureTitle,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = featureDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkSecondary
                    )
                }
            }
        }
    }
}

private fun openExternalUrl(
    context: Context,
    url: String,
    onError: (String) -> Unit
) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        onError("Kein Browser zum Öffnen von $url gefunden.")
    }
}
