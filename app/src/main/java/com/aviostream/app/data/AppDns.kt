package com.aviostream.app.data

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps

/**
 * DNS provider choice (v0.14.67, GitHub issue #10): some ISPs poison
 * their own DNS answers (the reporter's api.themoviedb.org resolved
 * to his ISP's block address, so every plug failed before it could
 * start). The user can route the app's lookups through
 * DNS-over-HTTPS instead — Cloudflare or Google — whose answers the
 * ISP cannot rewrite. SYSTEM (the default) is exactly the pre-.67
 * behaviour: the platform resolver, untouched.
 *
 * ONE provider for every shared transport: BoundedHttp (metadata /
 * API), PlugHttp (plugs) and PlaybackHttp (playback) all consult
 * [currentDns] when they build a client, and they key their client
 * caches on [choice], so flipping the setting takes effect on the
 * next client build — no app restart.
 */
enum class DnsChoice(val key: String, val label: String) {
    SYSTEM("system", "System"),
    CLOUDFLARE("cloudflare", "Cloudflare"),
    GOOGLE("google", "Google");

    companion object {
        fun fromKey(key: String?): DnsChoice =
            values().find { it.key == key } ?: SYSTEM
    }
}

object AppDns {

    /** The live choice. SettingsStore pushes every change here. */
    @Volatile
    var choice: DnsChoice = DnsChoice.SYSTEM
        private set

    @Volatile
    private var cachedDoh: Dns? = null

    @Volatile
    private var cachedDohChoice: DnsChoice? = null

    fun applyChoice(newChoice: DnsChoice) {
        if (choice == newChoice) return
        choice = newChoice
        synchronized(this) {
            cachedDoh = null
            cachedDohChoice = null
        }
    }

    /**
     * The [Dns] every shared client builder uses. For the DoH
     * choices the resolver itself is built on a plain bootstrap
     * client (system DNS), which is only ever asked to resolve the
     * DoH endpoint's own host — every app hostname goes through the
     * encrypted query.
     */
    fun currentDns(): Dns {
        val current = choice
        if (current == DnsChoice.SYSTEM) return Dns.SYSTEM
        cachedDoh?.let { if (cachedDohChoice == current) return it }
        synchronized(this) {
            cachedDoh?.let { if (cachedDohChoice == current) return it }
            val url = when (current) {
                DnsChoice.CLOUDFLARE -> "https://cloudflare-dns.com/dns-query"
                DnsChoice.GOOGLE -> "https://dns.google/dns-query"
                DnsChoice.SYSTEM -> return Dns.SYSTEM
            }
            val doh = DnsOverHttps.Builder()
                .client(OkHttpClient())
                .url(url.toHttpUrl())
                .build()
            cachedDoh = doh
            cachedDohChoice = current
            return doh
        }
    }
}
