package com.example.seal

import android.net.Uri
import android.util.Log
import com.example.model.SealAudioFormat
import com.example.model.SealAudioQuality
import com.example.model.SealSettings
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "YouTubeAudioExtractor"

data class YouTubeAudioStream(
    val url: String,
    val mimeType: String,
    val extension: String,
    val bitrateBps: Int,
    val qualityLabel: String,
    val userAgent: String
)

data class YouTubeResolvedTrack(
    val videoId: String,
    val title: String,
    val author: String,
    val durationMs: Long,
    val watchUrl: String,
    val streams: List<YouTubeAudioStream>
)

private data class InnerTubeClientSpec(
    val clientName: String,
    val clientVersion: String,
    val headerClientName: String,
    val userAgent: String,
    val osName: String,
    val osVersion: String,
    val androidSdkVersion: Int? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null
)

class YouTubeAudioExtractor(
    private val httpClient: OkHttpClient = defaultClient()
) {
    fun resolve(rawUrl: String): Result<YouTubeResolvedTrack> {
        val videoId = extractVideoId(rawUrl)
            ?: return Result.failure(IllegalArgumentException("Ungültiger YouTube-Link."))
        val watchUrl = "https://www.youtube.com/watch?v=$videoId"

        val watchHtml = runCatching { fetchWatchPageHtml(watchUrl) }.getOrNull()
        val apiKey = watchHtml?.let(::extractInnerTubeApiKey) ?: DEFAULT_INNERTUBE_API_KEY
        val visitorData = watchHtml?.let(::extractVisitorData)

        var bestTitle = ""
        var bestAuthor = ""
        var bestDurationMs = 0L
        val collectedStreams = mutableListOf<YouTubeAudioStream>()

        // 1. Try InnerTube player clients that expose direct audio URLs (ANDROID_VR, IOS, ANDROID)
        for (client in INNERTUBE_CLIENTS) {
            val res = runCatching {
                fetchInnerTubePlayer(videoId, apiKey, visitorData, client)
            }.getOrNull()
            if (res != null) {
                if (bestTitle.isBlank()) bestTitle = res.title
                if (bestAuthor.isBlank()) bestAuthor = res.author
                if (bestDurationMs <= 0L) bestDurationMs = res.durationMs
                collectedStreams += res.streams
                if (collectedStreams.any { it.extension == "m4a" } && collectedStreams.size >= 2) {
                    break
                }
            }
        }

        // 2. Parse ytInitialPlayerResponse embedded in watch HTML if still lacking streams
        if (collectedStreams.isEmpty() && !watchHtml.isNullOrBlank()) {
            val htmlResolved = runCatching {
                extractPlayerJsonFromHtml(watchHtml)?.let { json ->
                    parseInnerTubePlayerJson(json, videoId, WEB_USER_AGENT)
                }
            }.getOrNull()
            if (htmlResolved != null) {
                if (bestTitle.isBlank()) bestTitle = htmlResolved.title
                if (bestAuthor.isBlank()) bestAuthor = htmlResolved.author
                if (bestDurationMs <= 0L) bestDurationMs = htmlResolved.durationMs
                collectedStreams += htmlResolved.streams
            }
        }

        // 3. Fallback to public Piped / Invidious APIs if YouTube blocks direct device IP
        if (collectedStreams.isEmpty()) {
            for (pipedBase in PIPED_API_BASES) {
                val piped = runCatching { fetchFromPiped(pipedBase, videoId) }.getOrNull()
                if (piped != null && piped.streams.isNotEmpty()) {
                    if (bestTitle.isBlank()) bestTitle = piped.title
                    if (bestAuthor.isBlank()) bestAuthor = piped.author
                    if (bestDurationMs <= 0L) bestDurationMs = piped.durationMs
                    collectedStreams += piped.streams
                    break
                }
            }
        }

        if (collectedStreams.isEmpty()) {
            for (invBase in INVIDIOUS_API_BASES) {
                val inv = runCatching { fetchFromInvidious(invBase, videoId) }.getOrNull()
                if (inv != null && inv.streams.isNotEmpty()) {
                    if (bestTitle.isBlank()) bestTitle = inv.title
                    if (bestAuthor.isBlank()) bestAuthor = inv.author
                    if (bestDurationMs <= 0L) bestDurationMs = inv.durationMs
                    collectedStreams += inv.streams
                    break
                }
            }
        }

        // 4. Enrich title/author via YouTube oEmbed if needed
        if (bestTitle.isBlank() || bestAuthor.isBlank()) {
            val oEmbed = runCatching { fetchOEmbedMetadata(watchUrl) }.getOrNull()
            if (oEmbed != null) {
                if (bestTitle.isBlank()) bestTitle = oEmbed.first
                if (bestAuthor.isBlank()) bestAuthor = oEmbed.second
            }
        }

        val deduplicated = collectedStreams
            .distinctBy { "${it.extension}_${it.bitrateBps}_${it.url.substringBefore('&')}" }

        if (deduplicated.isEmpty()) {
            Log.w(TAG, "No direct YouTube audio stream found for videoId=$videoId")
            return Result.failure(
                Exception("YouTube-Audio konnte nicht extrahiert werden. Prüfe Internetverbindung oder versuche es später erneut.")
            )
        }

        return Result.success(
            YouTubeResolvedTrack(
                videoId = videoId,
                title = bestTitle.ifBlank { "YouTube Audio ($videoId)" },
                author = bestAuthor.ifBlank { "YouTube" },
                durationMs = bestDurationMs,
                watchUrl = watchUrl,
                streams = deduplicated
            )
        )
    }

    private fun fetchWatchPageHtml(watchUrl: String): String {
        val req = Request.Builder()
            .url(watchUrl)
            .header("User-Agent", WEB_USER_AGENT)
            .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
            .header("Cookie", "CONSENT=YES+cb.20210328-17-p0.en+FX+471")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return ""
            return resp.body?.string().orEmpty()
        }
    }

    private fun fetchInnerTubePlayer(
        videoId: String,
        apiKey: String,
        visitorData: String?,
        client: InnerTubeClientSpec
    ): YouTubeResolvedTrack? {
        val clientJson = JSONObject().apply {
            put("clientName", client.clientName)
            put("clientVersion", client.clientVersion)
            put("hl", "de")
            put("gl", "DE")
            put("osName", client.osName)
            put("osVersion", client.osVersion)
            client.androidSdkVersion?.let { put("androidSdkVersion", it) }
            client.deviceMake?.let { put("deviceMake", it) }
            client.deviceModel?.let { put("deviceModel", it) }
            visitorData?.takeIf { it.isNotBlank() }?.let { put("visitorData", it) }
        }
        val payload = JSONObject().apply {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put("context", JSONObject().apply {
                put("client", clientJson)
            })
        }.toString()

        val reqBuilder = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?key=$apiKey&prettyPrint=false")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Content-Type", "application/json")
            .header("User-Agent", client.userAgent)
            .header("X-YouTube-Client-Name", client.headerClientName)
            .header("X-YouTube-Client-Version", client.clientVersion)
            .header("Origin", "https://www.youtube.com")

        if (!visitorData.isNullOrBlank()) {
            reqBuilder.header("X-Goog-Visitor-Id", visitorData)
        }

        httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return null
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
            return parseInnerTubePlayerJson(json, videoId, client.userAgent)
        }
    }

    private fun fetchFromPiped(baseUrl: String, videoId: String): YouTubeResolvedTrack? {
        val req = Request.Builder()
            .url("$baseUrl/streams/$videoId")
            .header("Accept", "application/json")
            .header("User-Agent", WEB_USER_AGENT)
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
            if (json.has("error")) return null
            val title = json.optString("title").trim()
            val author = json.optString("uploader").trim()
            val durationSec = json.optLong("duration", 0L)
            val arr = json.optJSONArray("audioStreams") ?: return null
            val streams = parsePipedAudioStreams(arr)
            if (streams.isEmpty()) return null
            return YouTubeResolvedTrack(
                videoId = videoId,
                title = title,
                author = author,
                durationMs = durationSec * 1000L,
                watchUrl = "https://www.youtube.com/watch?v=$videoId",
                streams = streams
            )
        }
    }

    private fun fetchFromInvidious(baseUrl: String, videoId: String): YouTubeResolvedTrack? {
        val req = Request.Builder()
            .url("$baseUrl/api/v1/videos/$videoId")
            .header("Accept", "application/json")
            .header("User-Agent", WEB_USER_AGENT)
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
            val title = json.optString("title").trim()
            val author = json.optString("author").trim()
            val durationSec = json.optLong("lengthSeconds", 0L)
            val arr = json.optJSONArray("adaptiveFormats") ?: return null
            val streams = mutableListOf<YouTubeAudioStream>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val url = obj.optString("url").trim()
                val mime = obj.optString("type").trim()
                if (url.isBlank() || !mime.startsWith("audio/", ignoreCase = true)) continue
                val bitrate = obj.optInt("bitrate", 128000)
                val ext = extensionFromMime(mime)
                streams += YouTubeAudioStream(
                    url = url,
                    mimeType = mime.substringBefore(';'),
                    extension = ext,
                    bitrateBps = bitrate,
                    qualityLabel = "${bitrate / 1000} kbps",
                    userAgent = WEB_USER_AGENT
                )
            }
            if (streams.isEmpty()) return null
            return YouTubeResolvedTrack(
                videoId = videoId,
                title = title,
                author = author,
                durationMs = durationSec * 1000L,
                watchUrl = "https://www.youtube.com/watch?v=$videoId",
                streams = streams
            )
        }
    }

    private fun fetchOEmbedMetadata(watchUrl: String): Pair<String, String>? {
        val encoded = Uri.encode(watchUrl)
        val req = Request.Builder()
            .url("https://www.youtube.com/oembed?url=$encoded&format=json")
            .header("Accept", "application/json")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val json = runCatching { JSONObject(resp.body?.string().orEmpty()) }.getOrNull() ?: return null
            val title = json.optString("title").trim()
            val author = json.optString("author_name").trim()
            return if (title.isNotBlank() || author.isNotBlank()) title to author else null
        }
    }

    companion object {
        private const val DEFAULT_INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        private const val WEB_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        private val PIPED_API_BASES = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.adminforge.de",
            "https://api.piped.private.coffee"
        )

        private val INVIDIOUS_API_BASES = listOf(
            "https://inv.nadeko.net",
            "https://yewtu.be"
        )

        private val INNERTUBE_CLIENTS = listOf(
            InnerTubeClientSpec(
                clientName = "ANDROID_VR",
                clientVersion = "1.60.19",
                headerClientName = "28",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                osName = "Android",
                osVersion = "12L",
                androidSdkVersion = 32,
                deviceMake = "Oculus",
                deviceModel = "Quest 3"
            ),
            InnerTubeClientSpec(
                clientName = "IOS",
                clientVersion = "19.29.1",
                headerClientName = "5",
                userAgent = "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)",
                osName = "iPhone",
                osVersion = "17.5.1.21F90",
                deviceMake = "Apple",
                deviceModel = "iPhone16,2"
            ),
            InnerTubeClientSpec(
                clientName = "ANDROID",
                clientVersion = "19.29.37",
                headerClientName = "3",
                userAgent = "com.google.android.youtube/19.29.37 (Linux; U; Android 14) gzip",
                osName = "Android",
                osVersion = "14",
                androidSdkVersion = 34,
                deviceMake = "Google",
                deviceModel = "Pixel 8"
            )
        )

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        fun isYouTubeUrl(rawUrl: String): Boolean = extractVideoId(rawUrl) != null

        fun extractVideoId(rawUrl: String): String? {
            val trimmed = rawUrl.trim()
            if (!trimmed.startsWith("http://", ignoreCase = true) &&
                !trimmed.startsWith("https://", ignoreCase = true)
            ) {
                return null
            }
            val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
            val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
            val validId = Regex("^[A-Za-z0-9_-]{11}$")

            if (host == "youtu.be") {
                val seg = uri.pathSegments.firstOrNull()?.take(11)
                return seg?.takeIf { validId.matches(it) }
            }

            if (host == "youtube.com" ||
                host == "music.youtube.com" ||
                host == "youtube-nocookie.com"
            ) {
                val vParam = uri.getQueryParameter("v")?.take(11)
                if (vParam != null && validId.matches(vParam)) return vParam

                val segments = uri.pathSegments
                if (segments.size >= 2 && segments[0] in setOf("shorts", "embed", "live", "v")) {
                    val candidate = segments[1].take(11)
                    if (validId.matches(candidate)) return candidate
                }
            }
            return null
        }

        internal fun extractInnerTubeApiKey(html: String): String? {
            return Regex(""""INNERTUBE_API_KEY"\s*:\s*"([^"]+)"""")
                .find(html)?.groupValues?.getOrNull(1)
        }

        internal fun extractVisitorData(html: String): String? {
            return Regex(""""VISITOR_DATA"\s*:\s*"([^"]+)"""")
                .find(html)?.groupValues?.getOrNull(1)
        }

        internal fun extractPlayerJsonFromHtml(html: String): JSONObject? {
            val marker = "var ytInitialPlayerResponse ="
            val idx = html.indexOf(marker).takeIf { it >= 0 }
                ?: html.indexOf("ytInitialPlayerResponse =").takeIf { it >= 0 }
                ?: return null
            val braceStart = html.indexOf('{', idx)
            if (braceStart < 0) return null

            var depth = 0
            var inString = false
            var escaped = false
            for (i in braceStart until html.length) {
                val c = html[i]
                if (escaped) {
                    escaped = false
                    continue
                }
                if (c == '\\' && inString) {
                    escaped = true
                    continue
                }
                if (c == '"') {
                    inString = !inString
                    continue
                }
                if (!inString) {
                    if (c == '{') depth++
                    else if (c == '}') {
                        depth--
                        if (depth == 0) {
                            val rawJson = html.substring(braceStart, i + 1)
                            return runCatching { JSONObject(rawJson) }.getOrNull()
                        }
                    }
                }
            }
            return null
        }

        internal fun parseInnerTubePlayerJson(
            json: JSONObject,
            videoId: String,
            userAgent: String
        ): YouTubeResolvedTrack? {
            val playability = json.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
            val streamingData = json.optJSONObject("streamingData") ?: return null
            val adaptive = streamingData.optJSONArray("adaptiveFormats")
            val combined = streamingData.optJSONArray("formats")

            val streams = mutableListOf<YouTubeAudioStream>()
            fun collectFrom(arr: JSONArray?, audioOnlyRequired: Boolean) {
                if (arr == null) return
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val directUrl = obj.optString("url").trim()
                    if (directUrl.isBlank()) continue
                    val mime = obj.optString("mimeType").trim()
                    val isAudio = mime.startsWith("audio/", ignoreCase = true)
                    if (audioOnlyRequired && !isAudio) continue
                    val bitrate = obj.optInt("averageBitrate", obj.optInt("bitrate", 128000))
                    val ext = extensionFromMime(mime)
                    val qualityLabel = obj.optString("audioQuality").takeIf { it.isNotBlank() }
                        ?: "${bitrate / 1000} kbps"
                    streams += YouTubeAudioStream(
                        url = directUrl,
                        mimeType = mime.substringBefore(';').ifBlank { "audio/mp4" },
                        extension = ext,
                        bitrateBps = bitrate,
                        qualityLabel = qualityLabel,
                        userAgent = userAgent
                    )
                }
            }

            collectFrom(adaptive, audioOnlyRequired = true)
            if (streams.isEmpty()) {
                collectFrom(combined, audioOnlyRequired = true)
            }
            if (streams.isEmpty() && playability.equals("OK", ignoreCase = true)) {
                collectFrom(combined, audioOnlyRequired = false)
            }

            if (streams.isEmpty()) return null

            val details = json.optJSONObject("videoDetails")
            val title = details?.optString("title")?.trim().orEmpty()
            val author = details?.optString("author")?.trim().orEmpty()
            val durationSec = details?.optString("lengthSeconds")?.toLongOrNull() ?: 0L

            return YouTubeResolvedTrack(
                videoId = videoId,
                title = title,
                author = author,
                durationMs = durationSec * 1000L,
                watchUrl = "https://www.youtube.com/watch?v=$videoId",
                streams = streams
            )
        }

        internal fun parsePipedAudioStreams(arr: JSONArray): List<YouTubeAudioStream> {
            val out = mutableListOf<YouTubeAudioStream>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val url = obj.optString("url").trim()
                if (url.isBlank()) continue
                val mime = obj.optString("mimeType", "audio/mp4").trim()
                val bitrate = obj.optInt("bitrate", 128000)
                val quality = obj.optString("quality", "${bitrate / 1000} kbps").trim()
                out += YouTubeAudioStream(
                    url = url,
                    mimeType = mime.substringBefore(';').ifBlank { "audio/mp4" },
                    extension = extensionFromMime(mime),
                    bitrateBps = bitrate,
                    qualityLabel = quality,
                    userAgent = WEB_USER_AGENT
                )
            }
            return out
        }

        internal fun selectBestStream(
            streams: List<YouTubeAudioStream>,
            settings: SealSettings
        ): YouTubeAudioStream {
            val preferredExt = when (settings.format) {
                SealAudioFormat.OGG -> "ogg"
                else -> "m4a"
            }

            val formatPool = streams.filter { it.extension == preferredExt }.ifEmpty {
                // Prefer Android-native m4a container first over webm when format is general
                streams.filter { it.extension == "m4a" }.ifEmpty { streams }
            }

            return when (settings.quality) {
                SealAudioQuality.LOW -> formatPool.minByOrNull { it.bitrateBps } ?: streams.first()
                SealAudioQuality.MEDIUM -> formatPool.minByOrNull { abs(it.bitrateBps - 128_000) } ?: formatPool.first()
                SealAudioQuality.AUTO, SealAudioQuality.LOSSLESS ->
                    formatPool.maxByOrNull { it.bitrateBps } ?: streams.maxByOrNull { it.bitrateBps } ?: streams.first()
            }
        }

        internal fun extensionFromMime(mime: String): String {
            val low = mime.lowercase()
            return when {
                low.contains("mp4") || low.contains("m4a") || low.contains("aac") -> "m4a"
                low.contains("webm") || low.contains("opus") || low.contains("ogg") -> "ogg"
                low.contains("mpeg") || low.contains("mp3") -> "mp3"
                low.contains("flac") -> "flac"
                else -> "m4a"
            }
        }
    }
}
