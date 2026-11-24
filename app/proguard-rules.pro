# Project-specific ProGuard rules.
# R8 minification is ON for release builds (isMinifyEnabled = true).

# ── CloudStream (CS3) runtime ─────────────────────────────────────────────────
# Plugins are loaded at runtime via PathClassLoader and reflect into the
# runtime API, so their classes and the host's CS3 layer must survive R8.
# R8 minification is ON, and these keeps stop it stripping the runtime.
# proguard-rules.pro (its com.nuvio.tv.core.cloudstream line re-pointed here).
# Every kept package was verified present 2026-11-22: AAR (1,098 classes) for
# com.lagradost.cloudstream3/api, NiceHttp 0.4.18, jackson-module-kotlin
# 2.13.1 (173 AAR classes reference it), jsoup 1.22.1, rhino 1.8.1,
# tmdb-java 2.13.0, fuzzywuzzy 1.4.0, cryptography 0.6.0, NewPipeExtractor
# v0.26.5, kotlinx-datetime 0.8.0, kotlinx-serialization-json 1.7.3,
# OkHttp 5.3.2 runtime.
-keep class com.lagradost.cloudstream3.** { *; }
-keep class com.lagradost.api.** { *; }
-keep class com.lagradost.nicehttp.** { *; }
-keep class com.aviostream.app.data.cloudstream.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepnames class com.fasterxml.jackson.** { *; }
-keep class org.jsoup.** { *; }
-keep class org.mozilla.javascript.** { *; }
# Rhino references java.beans and jdk.dynalink, which do not exist on
# Android. R8 fails on these missing classes; they are only used by
# Rhino's JVM-only paths (guarded by reflection), so silence the warnings.
-dontwarn java.beans.**
-dontwarn jdk.dynalink.**
-keep class com.uwetrottmann.tmdb2.** { *; }
-keep class me.xdrop.fuzzywuzzy.** { *; }
-keep class dev.whyoleg.cryptography.** { *; }
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class kotlinx.datetime.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn com.lagradost.**
-dontwarn com.fasterxml.jackson.**
