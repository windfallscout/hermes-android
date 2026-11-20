package com.hermes.client.ui.models

import com.hermes.client.data.network.ModelProviderDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EffortTest {
    @Test fun parseEffortSuffix_extracts_valid_suffixes_and_rejects_others() {
        assertEquals(EffortVariant("claude-opus-5-5", "high"), parseEffortSuffix("claude-opus-5-5-high"))
        assertEquals(EffortVariant("gemini-4-hydrogen", "xhigh"), parseEffortSuffix("gemini-4-hydrogen-xhigh"))
        assertNull(parseEffortSuffix("gpt-5.1-codex-max"))
        assertNull(parseEffortSuffix("high"))
        assertNull(parseEffortSuffix("-high"))
        assertNull(parseEffortSuffix("gpt-5.5"))
    }

    @Test fun effectiveEffort_prefers_explicit_then_suffix_then_null() {
        assertEquals("high", effectiveEffort("claude-opus-5-5-low", "high"))
        assertEquals("low", effectiveEffort("claude-opus-5-5-low", null))
        assertEquals("low", effectiveEffort("claude-opus-5-5-low", "  "))
        assertNull(effectiveEffort("gpt-5.5", null))
        assertNull(effectiveEffort(null, null))
    }

    @Test fun effortVariantOf_resolves_same_provider_sibling_only() {
        val providers = listOf(
            ModelProviderDto(
                slug = "opencode-go",
                models = listOf("claude-opus-5-5-low", "claude-opus-5-5-high", "gpt-5.5"),
            ),
            ModelProviderDto(
                slug = "other",
                models = listOf("claude-opus-5-5-xhigh"),
            ),
        )

        assertEquals(
            "claude-opus-5-5-high",
            effortVariantOf(providers, "opencode-go", "claude-opus-5-5-low", "high"),
        )
        assertNull(effortVariantOf(providers, "opencode-go", "gpt-5.5", "high"))
        assertNull(effortVariantOf(providers, "opencode-go", "claude-opus-5-5-low", "xhigh"))
        assertNull(effortVariantOf(providers, null, "claude-opus-5-5-low", "high"))
    }

    @Test fun effortLabel_formats_display_chips() {
        assertEquals("XHigh", effortLabel("xhigh"))
        assertEquals("Med", effortLabel("medium"))
        assertEquals("Low", effortLabel("low"))
        assertEquals("High", effortLabel("high"))
    }
}
