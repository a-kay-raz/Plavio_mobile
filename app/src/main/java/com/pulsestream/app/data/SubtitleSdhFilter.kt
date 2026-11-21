package com.pulsestream.app.data

import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.text.TextOutput

/**
 * Step 38 — SDH (deaf/hard-of-hearing) cue filtering (Nuvio
 * features/player/SubtitleSdhFilter.kt, verbatim regexes). Strips speaker
 * labels ("NAME:"), bracketed sound descriptions ("[applause]") and
 * parenthesized sounds ("(door slams)") from cue text; a cue that is left
 * with nothing but whitespace/dashes is dropped (null).
 */
object SubtitleSdhFilter {
    private val squareBrackets = Regex("\\[[^]]*][ \\t]*")
    private val parentheses = Regex(
        "(?:\\((?=[A-Za-z0-9 '#.,\\\"\\\\\\-\\r\\n]*\\))(?![0-9]*\\))[^)]*\\)|" +
            "（(?=[A-Za-z0-9 '#.,\\\"\\\\\\-\\r\\n]*）)(?![0-9]*）)[^）]*）)[ \\t]*",
    )
    private val speakerLabel = Regex(
        "(?m)^([ \\t]*-[ \\t]*)?(?:[A-Za-z0-9 ()'#.,]+|\\[[^]\\r\\n]*]):(?=\\s|$)[ \\t]*",
    )

    fun filter(text: String): String? {
        var filtered = speakerLabel.replace(text) { match -> match.groups[1]?.value.orEmpty() }
        filtered = squareBrackets.replace(filtered, "")
        filtered = parentheses.replace(filtered, "")
        return filtered.lines()
            .filter { line -> line.any { !it.isWhitespace() && it != '-' } }
            .joinToString("\n")
            .takeIf(String::isNotBlank)
    }
}

/**
 * A TextOutput that strips SDH annotations from every cue before passing
 * it to the delegate (Nuvio's PlayerEngine.android.kt cue pipeline, same
 * shape). The delegate is the PlayerView's SubtitleView, so rendering is
 * unchanged apart from the filtered text. When [enabled] is false the cues
 * pass through untouched.
 */
@OptIn(UnstableApi::class)
class SdhFilteringTextOutput(
    private val delegate: TextOutput,
    private val enabled: () -> Boolean,
) : TextOutput {
    override fun onCues(cueGroup: CueGroup) {
        val processed = cueGroup.cues.mapNotNull(::processCue)
        delegate.onCues(CueGroup(processed, cueGroup.presentationTimeUs))
    }

    @Deprecated("Uses the deprecated Media3 callback for text outputs.")
    override fun onCues(cues: List<Cue>) {
        delegate.onCues(cues.mapNotNull(::processCue))
    }

    private fun processCue(cue: Cue): Cue? {
        if (!enabled()) return cue
        val text = cue.text?.toString() ?: return cue
        val filtered = SubtitleSdhFilter.filter(text) ?: return null
        if (filtered == text) return cue
        return cue.buildUpon().setText(filtered).build()
    }
}
