package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.EqPreferencesRepository
import com.example.i18n.stringsFor
import com.example.model.AppLanguage
import com.example.model.GitHubReleaseInfo
import com.example.model.GitHubUpdateState
import com.example.update.GitHubUpdateRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsSubgroupsAndUpdateTest {

    @Test
    fun `compareVersions correctly orders semantic versions and v prefixes`() {
        assertTrue(GitHubUpdateRepository.compareVersions("v1.1.0", "1.0") > 0)
        assertTrue(GitHubUpdateRepository.compareVersions("2.0", "1.9.9") > 0)
        assertEquals(0, GitHubUpdateRepository.compareVersions("v1.0.0", "1.0"))
        assertTrue(GitHubUpdateRepository.compareVersions("0.9.5", "1.0") < 0)
    }

    @Test
    fun `normalizeRepoSlug extracts owner and repo from full github URLs`() {
        assertEquals(
            "doteq-audio/DotEQ",
            GitHubUpdateRepository.normalizeRepoSlug("https://github.com/doteq-audio/DotEQ/")
        )
        assertEquals(
            "custom/my-repo",
            GitHubUpdateRepository.normalizeRepoSlug("custom/my-repo")
        )
        assertEquals(
            GitHubUpdateRepository.DEFAULT_GITHUB_REPO,
            GitHubUpdateRepository.normalizeRepoSlug("invalid")
        )
    }

    @Test
    fun `stringsFor returns distinct German and English localizations`() {
        val de = stringsFor(AppLanguage.DE)
        val en = stringsFor(AppLanguage.EN)
        assertEquals("EINSTELLUNGEN", de.settingsTitle)
        assertEquals("SETTINGS", en.settingsTitle)
        assertEquals("VERBINDUNGEN", de.groupConnections)
        assertEquals("CONNECTIONS", en.groupConnections)
        assertTrue(de.infoFeatureList.size >= 6)
        assertEquals(de.infoFeatureList.size, en.infoFeatureList.size)
    }

    @Test
    fun `preferences repository persists AppLanguage and custom GitHub repo`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = EqPreferencesRepository(context)

        repo.saveAppLanguage(AppLanguage.EN)
        repo.saveGithubRepo("https://github.com/doteq-team/DotEQ-App")

        assertEquals(AppLanguage.EN, repo.appLanguageFlow.first())
        assertEquals("doteq-team/DotEQ-App", repo.githubRepoFlow.first())

        // Restore defaults
        repo.saveAppLanguage(AppLanguage.DE)
        repo.saveGithubRepo(GitHubUpdateRepository.DEFAULT_GITHUB_REPO)
        assertEquals(AppLanguage.DE, repo.appLanguageFlow.first())
    }

    @Test
    fun `GitHubUpdateState models update announcement fields cleanly`() {
        val release = GitHubReleaseInfo(
            tagName = "v1.2.0",
            title = "DotEQ 1.2",
            notes = "Neue Crossover-Filter",
            htmlUrl = "https://github.com/doteq-audio/DotEQ/releases/tag/v1.2.0",
            publishedAt = "2026-11-18"
        )
        val state = GitHubUpdateState.UpdateAvailable(
            currentVersion = "1.0",
            release = release,
            repoSlug = "doteq-audio/DotEQ"
        )
        assertEquals("v1.2.0", state.release.tagName)
        assertEquals("1.0", state.currentVersion)
    }
}
