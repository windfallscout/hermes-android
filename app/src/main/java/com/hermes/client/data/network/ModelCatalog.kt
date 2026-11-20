package com.hermes.client.data.network

/** Maps a /api/providers row onto the UI-facing provider shape; blanks dropped, order preserved. */
fun providerToDto(p: ProviderDto, activeProvider: String?): ModelProviderDto =
    ModelProviderDto(
        slug = p.id,
        name = p.displayName?.takeIf { it.isNotBlank() } ?: p.id,
        isCurrent = p.id == activeProvider,
        models = cleanModels(p.models),
    )

/** Live ids first, then static ids the live list did not report — case-insensitive dedupe. */
fun mergeModels(static: List<String>, live: List<String>): List<String> =
    cleanModels(live + static)

/** Provider ids worth a live fetch: those with a key plus the active provider (never blank). */
fun providersNeedingLiveModels(providers: List<ProviderDto>, activeProvider: String?): List<String> =
    (providers.filter { it.hasKey || it.id == activeProvider }.map { it.id } + listOfNotNull(activeProvider))
        .filter { it.isNotBlank() }
        .distinct()

/** True only when the endpoint is missing on an older server (404/405) — anything else rethrows. */
fun shouldFallBackToModelOptions(code: Int): Boolean = code == 404 || code == 405

/** Drop blanks, trim, dedupe case-insensitively keeping the first occurrence. */
internal fun cleanModels(models: List<String>): List<String> {
    val seen = HashSet<String>()
    val out = ArrayList<String>(models.size)
    for (raw in models) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) continue
        if (seen.add(trimmed.lowercase())) {
            out.add(trimmed)
        }
    }
    return out
}
