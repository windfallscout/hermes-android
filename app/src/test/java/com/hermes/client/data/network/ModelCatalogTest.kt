package com.hermes.client.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test fun providerToDto_maps_display_name_fallback_isCurrent_and_cleans_models() {
        val withDisplayName = ProviderDto(
            id = "opencode-go",
            displayName = "OpenCode Go",
            hasKey = true,
            models = listOf("  ", "gpt-5.5", "GPT-5.5", "  claude-sonnet-4  ", ""),
        )
        val mapped = providerToDto(withDisplayName, activeProvider = "opencode-go")
        assertEquals("opencode-go", mapped.slug)
        assertEquals("OpenCode Go", mapped.name)
        assertTrue(mapped.isCurrent)
        assertEquals(listOf("gpt-5.5", "claude-sonnet-4"), mapped.models)

        val blankDisplayName = ProviderDto(
            id = "anthropic",
            displayName = "   ",
            hasKey = false,
            models = listOf("opus"),
        )
        val fallbackMapped = providerToDto(blankDisplayName, activeProvider = "opencode-go")
        assertEquals("anthropic", fallbackMapped.slug)
        assertEquals("anthropic", fallbackMapped.name)
        assertFalse(fallbackMapped.isCurrent)

        val nullDisplayName = ProviderDto(id = "openai", displayName = null)
        assertEquals("openai", providerToDto(nullDisplayName, activeProvider = null).name)
    }

    @Test fun mergeModels_puts_live_first_dedupes_case_insensitively_and_drops_blanks() {
        val static = listOf("", "DeepSeek-V4.1-Flash", "gpt-5.5", "  claude-opus-5-5  ", "GPT-5.5")
        val live = listOf("  ", "deepseek-v4.1-flash", "gemini-4-hydrogen", "GEMINI-4-HYDROGEN")

        val merged = mergeModels(static = static, live = live)
        assertEquals(
            listOf("deepseek-v4.1-flash", "gemini-4-hydrogen", "gpt-5.5", "claude-opus-5-5"),
            merged,
        )
    }

    @Test fun providersNeedingLiveModels_selects_hasKey_or_active_and_drops_blanks() {
        val providers = listOf(
            ProviderDto(id = "keyed", hasKey = true),
            ProviderDto(id = "keyless", hasKey = false),
            ProviderDto(id = "active-keyless", hasKey = false),
            ProviderDto(id = "", hasKey = true),
            ProviderDto(id = "   ", hasKey = true),
        )
        val ids = providersNeedingLiveModels(providers, activeProvider = "active-keyless")
        assertEquals(listOf("keyed", "active-keyless"), ids)

        // When activeProvider also has a key, it is not duplicated.
        val deduped = providersNeedingLiveModels(providers, activeProvider = "keyed")
        assertEquals(listOf("keyed"), deduped)

        // Blank activeProvider is ignored.
        val blankActive = providersNeedingLiveModels(providers, activeProvider = "  ")
        assertEquals(listOf("keyed"), blankActive)
    }

    @Test fun shouldFallBackToModelOptions_true_only_for_404_and_405() {
        assertTrue(shouldFallBackToModelOptions(404))
        assertTrue(shouldFallBackToModelOptions(405))
        assertFalse(shouldFallBackToModelOptions(500))
        assertFalse(shouldFallBackToModelOptions(401))
        assertFalse(shouldFallBackToModelOptions(403))
        assertFalse(shouldFallBackToModelOptions(200))
    }
}
