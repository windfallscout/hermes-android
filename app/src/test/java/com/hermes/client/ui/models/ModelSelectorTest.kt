package com.hermes.client.ui.models

import com.hermes.client.data.network.ModelProviderDto
import com.hermes.client.data.repository.favKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSelectorTest {
    private val providers = listOf(
        ModelProviderDto(slug = "openai-codex", name = null, isCurrent = true,
            models = listOf("gpt-5.5", "gpt-5.5-mini")),
        ModelProviderDto(slug = "OpenRouter", name = "OpenRouter", isCurrent = false,
            models = listOf("stepfun/step-3.7-flash:free")),
    )

    private fun rows(items: List<ModelListItem>) =
        items.filterIsInstance<ModelListItem.Row>().map { it.row }
    private fun headers(items: List<ModelListItem>) =
        items.filterIsInstance<ModelListItem.Header>().map { it.title }

    @Test fun groups_by_provider_with_headers_in_input_order() {
        val items = modelSelectorRows(providers, emptySet(), "", null, null)
        assertEquals(listOf("openai-codex", "OpenRouter"), headers(items))
        assertEquals(3, rows(items).size)
    }

    @Test fun favorites_pinned_first_and_also_shown_in_group() {
        val favs = setOf(favKey("openai-codex", "gpt-5.5"))
        val items = modelSelectorRows(providers, favs, "", null, null)
        assertEquals("Favorites", (items.first() as ModelListItem.Header).title)
        // appears in the Favorites section AND its provider group, both flagged
        assertEquals(2, rows(items).count { it.model == "gpt-5.5" && it.isFavorite })
    }

    @Test fun no_favorites_header_when_none_present() {
        val items = modelSelectorRows(providers, emptySet(), "", null, null)
        assertTrue(headers(items).none { it == "Favorites" })
    }

    @Test fun query_filters_model_and_provider_case_insensitively() {
        assertEquals(
            listOf("stepfun/step-3.7-flash:free"),
            rows(modelSelectorRows(providers, emptySet(), "STEP", null, null)).map { it.model },
        )
        assertEquals(2, rows(modelSelectorRows(providers, emptySet(), "codex", null, null)).size)
        assertTrue(modelSelectorRows(providers, emptySet(), "zzz", null, null).isEmpty())
    }

    @Test fun marks_exactly_the_current_row() {
        val items = modelSelectorRows(providers, emptySet(), "", "openai-codex", "gpt-5.5")
        val current = rows(items).filter { it.isCurrent }
        assertEquals(1, current.size)
        assertEquals("gpt-5.5", current[0].model)
        assertEquals("openai-codex", current[0].provider)
    }

    @Test fun provider_header_marks_the_current_provider() {
        val items = modelSelectorRows(providers, emptySet(), "", null, null)
        val codex = items.filterIsInstance<ModelListItem.Header>().first { it.title == "openai-codex" }
        assertTrue(codex.isCurrent)
    }

    @Test fun non_current_provider_header_is_not_current() {
        val items = modelSelectorRows(providers, emptySet(), "", null, null)
        val openRouter = items.filterIsInstance<ModelListItem.Header>().first { it.title == "OpenRouter" }
        assertTrue(!openRouter.isCurrent)
    }

    @Test fun favorite_filtered_out_by_query_produces_no_favorites_header() {
        val favs = setOf(favKey("openai-codex", "gpt-5.5"))
        // "step" matches only the OpenRouter model, not the favorited gpt-5.5 — so the favorite
        // is filtered out and no "Favorites" header should appear.
        val items = modelSelectorRows(providers, favs, "step", null, null)
        assertTrue(headers(items).none { it == "Favorites" })
    }

    @Test fun preserves_long_model_and_provider_names_40_to_80_chars() {
        val model44 = "anthropic/claude-3.7-sonnet-20250219:thinking" // 45 chars
        val model64 = "openrouter:anthropic/claude-3.7-sonnet-20250219:thinking-preview" // 64 chars
        val model80 = "huggingface:meta-llama/Llama-3.3-70B-Instruct-Turbo-Free-FP8-Quantized-v2-202607" // 80 chars
        val longProvider = "openrouter-enterprise-gateway-us-east-prod" // 42 chars
        assertTrue(model44.length in 40..80)
        assertTrue(model64.length in 40..80)
        assertTrue(model80.length in 40..80)
        assertTrue(longProvider.length in 40..80)

        val longProviders = listOf(
            ModelProviderDto(
                slug = longProvider,
                name = "$longProvider (primary)",
                isCurrent = true,
                models = listOf(model44, model64, model80),
            ),
        )
        val favs = setOf(favKey(longProvider, model64))
        val items = modelSelectorRows(longProviders, favs, "", longProvider, model80)

        assertEquals(listOf("Favorites", "$longProvider (primary)"), headers(items))
        val modelRows = rows(items)
        // 1 in Favorites + 3 in provider group
        assertEquals(4, modelRows.size)
        assertTrue(modelRows.any { it.model == model44 })
        assertTrue(modelRows.any { it.model == model64 && it.isFavorite })
        assertTrue(modelRows.any { it.model == model80 && it.isCurrent && it.provider == longProvider })
    }

    @Test fun query_matches_provider_colon_model_on_long_names() {
        val longModel = "anthropic/claude-3.7-sonnet-20250219:thinking-extended-context" // 62 chars
        assertTrue(longModel.length in 40..80)
        val longProviders = listOf(
            ModelProviderDto(slug = "openrouter", name = "OpenRouter", models = listOf(longModel)),
        )

        val byProviderColonModel = modelSelectorRows(
            longProviders, emptySet(), "openrouter:anthropic/claude-3.7", null, null,
        )
        assertEquals(listOf(longModel), rows(byProviderColonModel).map { it.model })

        val byTail = modelSelectorRows(
            longProviders, emptySet(), "extended-context", null, null,
        )
        assertEquals(listOf(longModel), rows(byTail).map { it.model })
    }

    @Test fun populates_effort_for_suffixed_model_and_null_otherwise() {
        val effortProviders = listOf(
            ModelProviderDto(
                slug = "opencode-go",
                models = listOf("claude-opus-5-5-high", "gpt-5.5"),
            ),
        )
        val modelRows = rows(modelSelectorRows(effortProviders, emptySet(), "", null, null))
        assertEquals("high", modelRows.first { it.model == "claude-opus-5-5-high" }.effort)
        assertNull(modelRows.first { it.model == "gpt-5.5" }.effort)
    }
}
