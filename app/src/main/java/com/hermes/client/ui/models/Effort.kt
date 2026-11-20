package com.hermes.client.ui.models

import com.hermes.client.data.network.ModelProviderDto

/** Levels offered by the selector — the gateway ladder subset that maps to real routes. */
val SELECTABLE_EFFORTS: List<String> = listOf("low", "medium", "high", "xhigh")

private val EFFORT_SUFFIXES = listOf("xhigh", "ultra", "minimal", "medium", "high", "low")
// Deliberately NOT "max"/"none": real model ids end with those words.

/** Parsed base model id and trailing thinking-effort level. */
data class EffortVariant(val base: String, val effort: String)

/** "claude-opus-5-5-high" -> ("claude-opus-5-5", "high"); null when the id carries no level. */
fun parseEffortSuffix(model: String): EffortVariant? {
    for (suffix in EFFORT_SUFFIXES) {
        if (model.length > suffix.length + 1 && model.endsWith("-$suffix")) {
            return EffortVariant(
                base = model.dropLast(suffix.length + 1),
                effort = suffix,
            )
        }
    }
    return null
}

/** Explicit choice wins, else the level encoded in the model id, else null. */
fun effectiveEffort(model: String?, explicit: String?): String? =
    explicit?.takeIf { it.isNotBlank() } ?: model?.let { parseEffortSuffix(it)?.effort }

/** Chip label: "xhigh" -> "XHigh", "medium" -> "Med", others -> capitalised. */
fun effortLabel(level: String): String = when (level) {
    "xhigh" -> "XHigh"
    "medium" -> "Med"
    else -> level.replaceFirstChar { it.titlecase() }
}

/** Sibling id of [model] carrying [effort] in the same provider, or null when the catalog has none. */
fun effortVariantOf(
    providers: List<ModelProviderDto>,
    provider: String?,
    model: String?,
    effort: String,
): String? {
    if (provider.isNullOrBlank() || model.isNullOrBlank()) return null
    val parsed = parseEffortSuffix(model) ?: return null
    val target = "${parsed.base}-$effort"
    val p = providers.firstOrNull { it.slug == provider } ?: return null
    return p.models.firstOrNull { it == target }
}
