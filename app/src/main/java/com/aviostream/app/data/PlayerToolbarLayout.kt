package com.aviostream.app.data

/**
 * Step 63 — Player toolbar customization (P25).
 * Scoped port of Nuvio player/PlayerToolbarLayout.kt and
 * player/LegacyPlayerButtonLayout.kt, restricted to the buttons
 * Avio's player actually has.
 *
 * Bottom pill (in Nuvio's legacy order): PiP, Quality, Subs, Audio,
 * Chapters, Resize, Speed, Sources, Episodes, Live channels.
 * Top cluster: Playback info, External player, Rotate, Lock.
 * Back stays always last in the top cluster (structural, not
 * customisable — same as Nuvio, where Back is not a layout button).
 *
 * Not ported: Nuvio's RATE / SWITCH_ENGINE / VIDEO_SETTINGS /
 * SUBMIT_INTRO / PARTY / CAST / PICTURE_IN_PICTURE (top) buttons —
 * Avio has no rating, engine switch, party or cast; video settings
 * is iOS-only in Nuvio; PiP lives in Avio's pill, not the top.
 *
 * Layouts encode as "id:v,id:h" strings in SettingsStore, so an
 * unknown/missing button falls back to visible in its default spot.
 */
enum class PlayerBottomButton(val storageId: String, val label: String) {
    PIP("pip", "PiP"),
    QUALITY("quality", "Quality"),
    SUBTITLES("subtitles", "Subtitles"),
    AUDIO("audio", "Audio tracks"),
    RESIZE("resize", "Resize mode"),
    SPEED("speed", "Playback speed"),
    SLEEP("sleep", "Sleep timer"),
    SOURCES("sources", "Sources"),
    CHAPTERS("chapters", "Chapters"),
    NEXT_EPISODE("next_episode", "Next episode"),
    EPISODES("episodes", "Episodes"),
    LIVE_CHANNELS("live_channels", "Live channels"),
}

enum class PlayerTopButton(val storageId: String, val label: String) {
    PLAYBACK_INFO("playback_info", "Playback info"),
    EXTERNAL_PLAYER("external_player", "External player"),
    ROTATE("rotate", "Rotate"),
    LOCK("lock", "Lock controls"),
}

data class PlayerButtonEntry<B : Enum<B>>(
    val button: B,
    val visible: Boolean,
)

data class PlayerButtonLayout<B : Enum<B>>(val entries: List<PlayerButtonEntry<B>>) {
    val visibleButtons: List<B>
        get() = entries.filter { it.visible }.map { it.button }

    fun withVisibility(button: B, visible: Boolean): PlayerButtonLayout<B> =
        PlayerButtonLayout(entries.map { if (it.button == button) it.copy(visible = visible) else it })

    fun moved(fromIndex: Int, toIndex: Int): PlayerButtonLayout<B> {
        if (fromIndex !in entries.indices || toIndex !in entries.indices || fromIndex == toIndex) return this
        val updated = entries.toMutableList()
        updated.add(toIndex, updated.removeAt(fromIndex))
        return PlayerButtonLayout(updated)
    }

    fun encode(storageId: (B) -> String): String = entries.joinToString(",") { entry ->
        "${storageId(entry.button)}:${if (entry.visible) "v" else "h"}"
    }

    companion object {
        fun <B : Enum<B>> allVisible(buttons: List<B>): PlayerButtonLayout<B> =
            PlayerButtonLayout(buttons.map { PlayerButtonEntry(it, visible = true) })

        fun <B : Enum<B>> decode(
            value: String?,
            buttons: List<B>,
            storageId: (B) -> String,
        ): PlayerButtonLayout<B> {
            if (value.isNullOrBlank()) return allVisible(buttons)
            val parsed = value.split(',').mapNotNull { token ->
                val id = token.substringBefore(':').trim()
                val button = buttons.firstOrNull { storageId(it) == id } ?: return@mapNotNull null
                PlayerButtonEntry(button, visible = token.substringAfter(':', "v").trim() != "h")
            }.distinctBy { it.button }
            val missing = buttons.filter { button -> parsed.none { it.button == button } }
                .map { PlayerButtonEntry(it, visible = true) }
            return PlayerButtonLayout(parsed + missing)
        }
    }
}

typealias PlayerBottomButtonLayout = PlayerButtonLayout<PlayerBottomButton>
typealias PlayerTopButtonLayout = PlayerButtonLayout<PlayerTopButton>

object PlayerButtonLayouts {
    val DefaultBottom: PlayerBottomButtonLayout =
        PlayerButtonLayout.allVisible(PlayerBottomButton.entries)
    val DefaultTop: PlayerTopButtonLayout =
        PlayerButtonLayout.allVisible(PlayerTopButton.entries)

    fun encodeBottom(layout: PlayerBottomButtonLayout): String = layout.encode { it.storageId }
    fun encodeTop(layout: PlayerTopButtonLayout): String = layout.encode { it.storageId }

    fun decodeBottom(value: String?): PlayerBottomButtonLayout =
        PlayerButtonLayout.decode(value, PlayerBottomButton.entries) { it.storageId }

    fun decodeTop(value: String?): PlayerTopButtonLayout =
        PlayerButtonLayout.decode(value, PlayerTopButton.entries) { it.storageId }
}
