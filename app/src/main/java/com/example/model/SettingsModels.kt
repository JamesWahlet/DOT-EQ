package com.example.model

enum class AppLanguage(val code: String, val label: String) {
    DE("de", "DEUTSCH"),
    EN("en", "ENGLISH")
}

enum class SettingsSubgroup {
    CONNECTIONS,
    SPECIAL,
    DESIGN,
    ACCOUNT,
    LANGUAGE,
    INFO
}

data class GitHubReleaseInfo(
    val tagName: String,
    val title: String,
    val notes: String,
    val htmlUrl: String,
    val publishedAt: String
)

sealed interface GitHubUpdateState {
    data object Idle : GitHubUpdateState
    data object Checking : GitHubUpdateState
    data class UpToDate(
        val currentVersion: String,
        val latestVersion: String,
        val repoSlug: String,
        val releaseUrl: String
    ) : GitHubUpdateState

    data class UpdateAvailable(
        val currentVersion: String,
        val release: GitHubReleaseInfo,
        val repoSlug: String
    ) : GitHubUpdateState

    data class Error(
        val message: String,
        val repoSlug: String
    ) : GitHubUpdateState
}
