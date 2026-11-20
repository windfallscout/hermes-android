package com.hermes.client.data.network

import kotlinx.serialization.json.Json
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
            models = listOf(
                ModelIdDto("  "),
                ModelIdDto("gpt-5.5"),
                ModelIdDto("GPT-5.5"),
                ModelIdDto("  claude-sonnet-4  "),
                ModelIdDto(""),
            ),
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
            models = listOf(ModelIdDto("opus")),
        )
        val fallbackMapped = providerToDto(blankDisplayName, activeProvider = "opencode-go")
        assertEquals("anthropic", fallbackMapped.slug)
        assertEquals("anthropic", fallbackMapped.name)
        assertFalse(fallbackMapped.isCurrent)

        val nullDisplayName = ProviderDto(id = "openai", displayName = null)
        assertEquals("openai", providerToDto(nullDisplayName, activeProvider = null).name)
    }

    @Test fun models_decode_object_shape_and_legacy_strings() {
        val json = Json { ignoreUnknownKeys = true }
        val payload = """
            {
              "providers": [
                {
                  "id": "opencode-go",
                  "has_key": true,
                  "models": [
                    {"id": "deepseek-v4.1-flash", "label": "DeepSeek V4.1 Flash"},
                    {"id": "glm-5"}
                  ],
                  "models_total": 29
                },
                {
                  "id": "legacy",
                  "models": ["legacy-model"]
                }
              ],
              "active_provider": "opencode-go"
            }
        """.trimIndent()
        val decoded = json.decodeFromString<ProvidersDto>(payload)
        assertEquals(2, decoded.providers.size)
        assertEquals("DeepSeek V4.1 Flash", decoded.providers[0].models[0].label)
        assertEquals(
            listOf("deepseek-v4.1-flash", "glm-5"),
            providerToDto(decoded.providers[0], decoded.activeProvider).models,
        )
        assertEquals(
            listOf("legacy-model"),
            providerToDto(decoded.providers[1], decoded.activeProvider).models,
        )
    }

    @Test fun live_models_decode_object_shape_and_legacy_strings() {
        val json = Json { ignoreUnknownKeys = true }
        val objectLive = json.decodeFromString<LiveModelsDto>(
            """{"provider":"opencode-go","models":[{"id":"deepseek-v4.1-flash","label":"DeepSeek V4.1 Flash"},{"id":""}],"count":2}""",
        )
        assertEquals(listOf("deepseek-v4.1-flash"), modelIds(objectLive.models))

        val legacyLive = json.decodeFromString<LiveModelsDto>(
            """{"models":["legacy-model"]}""",
        )
        assertEquals(listOf("legacy-model"), modelIds(legacyLive.models))
    }

    @Test fun modelIds_drops_blank_ids_and_dedupes() {
        assertEquals(
            listOf("a", "b"),
            modelIds(listOf(ModelIdDto(" a "), ModelIdDto("A"), ModelIdDto(""), ModelIdDto("b"))),
        )
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
