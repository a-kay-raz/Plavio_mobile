package com.aviostream.app.data.update

import java.net.URI
import org.json.JSONObject

/** The fork's mobile release contract: stable.json at the repo root. */
internal object MobileReleaseManifest {
    fun parse(json: JSONObject): UpdateInfo {
        val version = json.optString("version").removePrefix("v")
        require(version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) { "Invalid stable mobile version" }
        require(json.optString("platform", "mobile") == "mobile") { "Not a mobile release" }
        require(json.optString("channel", "stable") == "stable") { "Not a stable release" }
        val url = json.optString("apkUrl")
        val uri = URI(url)
        // Fork releases only: a GitHub release asset of this repo.
        // Upstream's GitLab origin is deliberately NOT accepted — its APK
        // is signed with upstream's key and would fail to install over
        // (or replace) the fork.
        require(uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null &&
            uri.port == -1 && uri.query == null && uri.fragment == null) { "Invalid mobile APK origin" }
        val segments = uri.rawPath.split("/").filter { it.isNotEmpty() }
        require(segments.size == 6 &&
            segments[0] == "a-kay-raz" &&
            segments[1] == "Plavio_mobile" &&
            segments[2] == "releases" &&
            segments[3] == "download" &&
            (segments[4] == "v$version" ||
                segments[4].matches(Regex("v${Regex.escape(version)}-dev-[0-9]+"))) &&
            segments[5] == "Avio-v$version-arm64.apk") { "APK does not match stable mobile version" }
        // S6: optional SHA-256 — when the manifest carries one it
        // must be a 64-hex digest; a malformed value fails the whole
        // check rather than installing unverified. Absent = install
        // as before (the current stable.json carries no hash yet).
        val sha256 = json.optString("sha256").lowercase().takeIf { it.isNotBlank() }
        if (sha256 != null) require(sha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid APK SHA-256 in manifest" }
        return UpdateInfo(version, json.optString("notes").take(16_000), url, json.optBoolean("major", false), sha256)
    }
}
