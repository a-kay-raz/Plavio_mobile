package com.aviostream.app.ui.screens

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Dock → tab scroll-to-top signals (v0.14.30 port): the same
 * pattern as [HomeScrollSignal], for the tabs that never had one.
 * Re-tapping the already-selected dock seat bumps the counter for
 * that tab; the tab's screen collects it and smooth-scrolls its
 * root list to the very top. Plain counters (not an event bus), so
 * a tap that lands mid-switch still takes effect on arrival. Keys
 * are the tab route strings. Home keeps its own [HomeScrollSignal]
 * (the dock fires it on every Home tap, as it always has).
 * Main-thread confined: writers are dock clicks, readers are
 * composition effects.
 */
object TabScrollSignals {
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val LIVE = "live"

    private val flows = mutableMapOf<String, MutableStateFlow<Int>>()

    fun requests(tab: String): MutableStateFlow<Int> =
        flows.getOrPut(tab) { MutableStateFlow(0) }

    fun requestScrollToTop(tab: String) {
        val flow = requests(tab)
        flow.value += 1
    }
}
