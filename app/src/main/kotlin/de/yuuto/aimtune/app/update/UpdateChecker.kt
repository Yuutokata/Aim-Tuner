package de.yuuto.aimtune.app.update

import de.yuuto.aimtune.core.update.Version
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** The newest published release: its version and the page to download it from. */
data class ReleaseInfo(val version: Version, val url: String)

/**
 * Asks GitHub for the newest release of the repository. It tells the player about it, nothing more: no download, no
 * install. Any failure (no network, rate limit, a private repository, which GitHub answers with 404, odd JSON) gives
 * null, so the check can never get in the way of the app.
 */
class UpdateChecker(
    private val client: HttpClient = defaultClient(),
    private val apiBase: String = API_BASE,
    private val repo: String = REPO,
    private val userAgent: String = "AimTune/${AppVersion.current}",
) {
    private val log = System.getLogger("aimtune.update")

    fun latest(): ReleaseInfo? = try {
        val request = HttpRequest.newBuilder(URI.create("$apiBase/repos/$repo/releases/latest"))
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", userAgent)
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 200) parse(response.body()) else null.also { log.log(System.Logger.Level.DEBUG, "update check: HTTP ${response.statusCode()}") }
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        null
    } catch (e: Exception) {
        log.log(System.Logger.Level.DEBUG, "update check failed: ${e.javaClass.simpleName}")
        null
    }

    internal fun parse(body: String): ReleaseInfo? {
        val release: JsonObject = Json.parseToJsonElement(body).jsonObject
        if (release["draft"]?.jsonPrimitive?.contentOrNull == "true" || release["prerelease"]?.jsonPrimitive?.contentOrNull == "true") return null
        val version = release["tag_name"]?.jsonPrimitive?.contentOrNull?.let(Version::parse) ?: return null
        // The link is opened in the browser: only ever a page of the repository itself.
        val url = release["html_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("https://github.com/$repo/") } ?: return null
        return ReleaseInfo(version, url)
    }

    companion object {
        const val REPO = "Yuutokata/Aim-Tuner"
        const val API_BASE = "https://api.github.com"
        private val CONNECT_TIMEOUT = Duration.ofSeconds(3)
        private val REQUEST_TIMEOUT = Duration.ofSeconds(5)

        fun defaultClient(): HttpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build()
    }
}
