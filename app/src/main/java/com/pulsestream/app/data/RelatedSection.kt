package com.pulsestream.app.data

/** One "More like this" / "More from <actor>" row from a meta addon. */
data class RelatedSection(
    val id: String,
    val title: String,
    val metas: List<MetaPreview>
)
