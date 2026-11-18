package com.example.update

import com.example.model.GitHubReleaseInfo
import com.example.model.GitHubUpdateState
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class GitHubUpdateRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val DEFAULT_GITHUB_REPO = "doteq-audio/DotEQ"
        const val DEFAULT_WEBSITE_URL = "https://ais-pre-uzrfui5gmyxbvhte4wkfdf-75134429926.europe-west2.run.app"

        fun normalizeRepoSlug(raw: String): String {
            val cleaned = raw.trim()
                .removePrefix("https://github.com/")
                .removePrefix("http://github.com/")
                .trim('/')
            val parts = cleaned.split('/').filter { it.isNotBlank() }
            return if (parts.size >= 2) "${parts[0]}/${parts[1]}" else DEFAULT_GITHUB_REPO
        }

        fun repoWebUrl(repoSlug: String): String {
            return "https://github.com/${normalizeRepoSlug(repoSlug)}"
        }

        fun compareVersions(remoteTag: String, currentVersion: String): Int {
            val remoteParts = extractNumericParts(remoteTag)
            val currentParts = extractNumericParts(currentVersion)
            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r != c) return r.compareTo(c)
            }
            return 0
        }

        private fun extractNumericParts(version: String): List<Int> {
            val cleaned = version.trim().removePrefix("v").removePrefix("V")
            return cleaned.split('.', '-', '_')
                .mapNotNull { token ->
                    token.takeWhile { it.isDigit() }.toIntOrNull()
                }
        }
    }

    suspend fun checkForUpdates(
        repoSlugInput: String,
        currentVersion: String
    ): GitHubUpdateState = withContext(Dispatchers.IO) {
        val repoSlug = normalizeRepoSlug(repoSlugInput)
        val repoUrl = repoWebUrl(repoSlug)

        try {
            val latestRelease = fetchLatestRelease(repoSlug)
            if (latestRelease != null) {
                return@withContext if (compareVersions(latestRelease.tagName, currentVersion) > 0) {
                    GitHubUpdateState.UpdateAvailable(
                        currentVersion = currentVersion,
                        release = latestRelease,
                        repoSlug = repoSlug
                    )
                } else {
                    GitHubUpdateState.UpToDate(
                        currentVersion = currentVersion,
                        latestVersion = latestRelease.tagName,
                        repoSlug = repoSlug,
                        releaseUrl = latestRelease.htmlUrl.ifBlank { repoUrl }
                    )
                }
            }

            // Fallback to tags if repository has tags without formal GitHub releases
            val latestTag = fetchLatestTag(repoSlug)
            if (latestTag != null) {
                return@withContext if (compareVersions(latestTag, currentVersion) > 0) {
                    GitHubUpdateState.UpdateAvailable(
                        currentVersion = currentVersion,
                        release = GitHubReleaseInfo(
                            tagName = latestTag,
                            title = "Release $latestTag",
                            notes = "Neues Tag auf GitHub verfügbar.",
                            htmlUrl = "$repoUrl/releases/tag/$latestTag",
                            publishedAt = ""
                        ),
                        repoSlug = repoSlug
                    )
                } else {
                    GitHubUpdateState.UpToDate(
                        currentVersion = currentVersion,
                        latestVersion = latestTag,
                        repoSlug = repoSlug,
                        releaseUrl = "$repoUrl/releases"
                    )
                }
            }

            GitHubUpdateState.Error(
                message = "Kein GitHub-Release unter $repoSlug gefunden.",
                repoSlug = repoSlug
            )
        } catch (e: Exception) {
            GitHubUpdateState.Error(
                message = e.localizedMessage ?: "GitHub-Verbindung nicht erreichbar.",
                repoSlug = repoSlug
            )
        }
    }

    private fun fetchLatestRelease(repoSlug: String): GitHubReleaseInfo? {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$repoSlug/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "DotEQ-Android")
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return null
            val o = JSONObject(body)
            val tag = o.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
            return GitHubReleaseInfo(
                tagName = tag,
                title = o.optString("name", tag).ifBlank { tag },
                notes = o.optString("body", "").take(240),
                htmlUrl = o.optString("html_url", repoWebUrl(repoSlug)),
                publishedAt = o.optString("published_at", "").take(10)
            )
        }
    }

    private fun fetchLatestTag(repoSlug: String): String? {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$repoSlug/tags?per_page=1")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "DotEQ-Android")
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return null
            val arr = JSONArray(body)
            if (arr.length() == 0) return null
            return arr.getJSONObject(0).optString("name").takeIf { it.isNotBlank() }
        }
    }
}
