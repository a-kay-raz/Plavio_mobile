package com.aviostream.app.media

import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.ts.TsExtractor
import com.aviostream.app.media.mkv.TailSeekMatroskaExtractor

/**
 * v0.14.30 port (feature #20): the stock [DefaultExtractorsFactory]
 * set with ONE swap — the Matroska extractor is Avio's tail-cues
 * fork ([TailSeekMatroskaExtractor]), which recovers the Cues index
 * from the file tail for muxes whose head SeekHead doesn't reference
 * it (stock Media3 declares those files unseekable, and every seek
 * is then refused). Every other extractor is the stock instance,
 * untouched. The fork is constructed exactly as the stock factory
 * constructs MatroskaExtractor under its defaults
 * (DefaultSubtitleParserFactory, flags 0).
 */
class AvioExtractorsFactory : ExtractorsFactory {

    override fun createExtractors(): Array<Extractor> {
        // Live TS hardening — the timestamp search window is widened
        // to 1500 packets (the Just Player value) so segments whose
        // timestamps jump (ad splices, encoder restarts on live
        // channels) are ridden out instead of failing the load.
        // Consistent with the standing LIVE patience rule: a
        // discontinuity is not a source error. Applies to every
        // TS source; VOD TS files gain the same tolerance and
        // nothing stricter is lost. NOTE: the other half of the
        // Just Player pattern, TsExtractor.FLAG_HDMV_DTS_AUDIO_
        // STREAMS, exists only in Media3 1.5+ — this tree pins
        // Media3 1.4.1 (verified against the 1.4.1 AAR: the
        // constant is absent), so that half waits for a Media3
        // upgrade and is NOT faked here.
        val defaults = DefaultExtractorsFactory()
            .setTsExtractorTimestampSearchBytes(
                1500 * TsExtractor.TS_PACKET_SIZE
            )
            .createExtractors()
        return Array(defaults.size) { i ->
            if (defaults[i] is MatroskaExtractor) {
                TailSeekMatroskaExtractor(DefaultSubtitleParserFactory(), 0)
            } else {
                defaults[i]
            }
        }
    }
}
