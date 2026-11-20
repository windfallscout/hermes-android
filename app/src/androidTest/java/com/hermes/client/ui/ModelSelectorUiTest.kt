package com.hermes.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.hermes.client.data.network.ModelProviderDto
import com.hermes.client.data.repository.favKey
import com.hermes.client.ui.chat.ModelChip
import com.hermes.client.ui.models.ModelScope
import com.hermes.client.ui.models.ModelSelectorContent
import com.hermes.client.ui.models.modelSelectorRows
import com.hermes.client.ui.theme.HermesTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ModelSelectorUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun displays_full_long_model_names_40_to_80_chars_without_clipping() {
        val model45 = "anthropic/claude-3.7-sonnet-20250219:thinking"
        val model64 = "openrouter:anthropic/claude-3.7-sonnet-20250219:thinking-preview"
        val model80 = "huggingface:meta-llama/Llama-3.3-70B-Instruct-Turbo-Free-FP8-Quantized-v2-202607"
        val providerSlug = "openrouter-enterprise-gateway-us-east-prod"
        assertTrue(model45.length in 40..80)
        assertTrue(model64.length in 40..80)
        assertTrue(model80.length in 40..80)

        val providers = listOf(
            ModelProviderDto(
                slug = providerSlug,
                name = providerSlug,
                isCurrent = false,
                models = listOf(model45, model64, model80),
            ),
        )
        val items = modelSelectorRows(
            providers = providers,
            favorites = emptySet(),
            query = "",
            currentProvider = providerSlug,
            currentModel = model80,
        )

        rule.setContent {
            HermesTheme {
                Box(Modifier.width(320.dp)) {
                    ModelSelectorContent(
                        items = items,
                        query = "",
                        onQueryChange = {},
                        scope = ModelScope.SESSION,
                        onScopeChange = {},
                        onToggleFavorite = { _, _ -> },
                        onSelect = { _, _ -> },
                        pending = false,
                        error = null,
                    )
                }
            }
        }

        // All 40-80 char names and the current indicator must be displayed and fit within 320.dp.
        listOf(model45, model64, model80).forEach { name ->
            val node = rule.onNodeWithText(name)
            node.assertIsDisplayed()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("Expected $name to fit inside 320.dp, got right=${bounds.right}", bounds.right <= 320.dp)
        }
        rule.onNodeWithText("$providerSlug  ·  current").assertIsDisplayed()
        val favButton = rule.onAllNodesWithContentDescription("Favorite").onFirst()
        favButton.assertIsDisplayed()
        assertTrue(favButton.getUnclippedBoundsInRoot().right <= 320.dp)
    }

    @Test fun displays_favorited_long_model_row_with_unfavorite_action_within_bounds() {
        val model64 = "openrouter:anthropic/claude-3.7-sonnet-20250219:thinking-preview"
        val providerSlug = "openrouter-enterprise-gateway-us-east-prod"
        val providers = listOf(
            ModelProviderDto(slug = providerSlug, name = providerSlug, models = listOf(model64)),
        )
        val items = modelSelectorRows(
            providers = providers,
            favorites = setOf(favKey(providerSlug, model64)),
            query = "",
            currentProvider = providerSlug,
            currentModel = model64,
        )

        rule.setContent {
            HermesTheme {
                Box(Modifier.width(320.dp)) {
                    ModelSelectorContent(
                        items = items,
                        query = "",
                        onQueryChange = {},
                        scope = null,
                        onScopeChange = {},
                        onToggleFavorite = { _, _ -> },
                        onSelect = { _, _ -> },
                        pending = false,
                        error = null,
                    )
                }
            }
        }

        rule.onAllNodesWithText(model64).onFirst().assertIsDisplayed()
        rule.onAllNodesWithText("$providerSlug  ·  current").onFirst().assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Unfavorite").onFirst().assertIsDisplayed()
    }

    @Test fun model_chip_constrains_width_for_long_80_char_name_and_stays_clickable() {
        val model80 = "huggingface:meta-llama/Llama-3.3-70B-Instruct-Turbo-Free-FP8-Quantized-v2-202607"
        var clicked = false

        rule.setContent {
            HermesTheme {
                ModelChip(
                    currentProvider = "openrouter",
                    currentModel = model80,
                    onClick = { clicked = true },
                )
            }
        }

        val chip = rule.onNodeWithText(model80)
        chip.assertIsDisplayed()
        val chipWidth = chip.getUnclippedBoundsInRoot().width
        assertTrue("chip must stay bounded, got width=$chipWidth", chipWidth <= 140.dp)
        chip.performClick()
        assertTrue(clicked)
    }
}
