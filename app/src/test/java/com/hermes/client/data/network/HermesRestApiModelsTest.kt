package com.hermes.client.data.network

import com.hermes.client.data.auth.GatewayConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HermesRestApiModelsTest {
    @get:Rule val serverRule = MockWebServerRule()
    private val json = Json { ignoreUnknownKeys = true }

    private fun api(server: MockWebServer) = HermesRestApi(OkHttpClient(), json) {
        GatewayConfig(baseUrl = server.url("/").toString().trimEnd('/'), token = "secret")
    }

    @Test fun modelProviders_merges_live_for_keyed_provider_and_keeps_static_for_keyless() = runTest {
        val server = serverRule.server
        server.enqueue(MockResponse.Builder().code(200).body(
            """{
                "providers": [
                    {"id": "opencode-go", "display_name": "OpenCode Go", "has_key": true, "configurable": false, "models": [], "models_total": 2},
                    {"id": "local-vllm", "display_name": "", "has_key": false, "models": ["llama-3.3-70b"], "models_total": 1}
                ],
                "active_provider": "opencode-go"
            }"""
        ).build())
        server.enqueue(MockResponse.Builder().code(200).body(
            """{"provider": "opencode-go", "models": ["deepseek-v4.1-flash", "glm-5"], "count": 2}"""
        ).build())

        val result = api(server).modelProviders()

        assertEquals(2, result.size)
        assertEquals("opencode-go", result[0].slug)
        assertEquals("OpenCode Go", result[0].name)
        assertTrue(result[0].isCurrent)
        assertEquals(listOf("deepseek-v4.1-flash", "glm-5"), result[0].models)

        assertEquals("local-vllm", result[1].slug)
        assertEquals("local-vllm", result[1].name)
        assertFalse(result[1].isCurrent)
        assertEquals(listOf("llama-3.3-70b"), result[1].models)

        assertEquals(2, server.requestCount)
        val first = server.takeRequest()
        assertEquals("/api/providers", first.target)
        val second = server.takeRequest()
        assertEquals("/api/models/live?provider=opencode-go", second.target)
    }

    @Test fun modelProviders_falls_back_to_model_options_on_404() = runTest {
        val server = serverRule.server
        server.enqueue(MockResponse.Builder().code(404).body("Not Found").build())
        server.enqueue(MockResponse.Builder().code(200).body(
            """{"providers": [{"slug": "anthropic", "name": "Anthropic", "is_current": true, "models": ["claude-opus-5-5"]}]}"""
        ).build())

        val result = api(server).modelProviders()

        assertEquals(1, result.size)
        assertEquals("anthropic", result[0].slug)
        assertEquals("Anthropic", result[0].name)
        assertTrue(result[0].isCurrent)
        assertEquals(listOf("claude-opus-5-5"), result[0].models)

        assertEquals(2, server.requestCount)
        assertEquals("/api/providers", server.takeRequest().target)
        assertEquals("/api/model/options", server.takeRequest().target)
    }

    @Test fun modelProviders_returns_static_models_when_live_endpoint_returns_500() = runTest {
        val server = serverRule.server
        server.enqueue(MockResponse.Builder().code(200).body(
            """{
                "providers": [
                    {"id": "opencode-go", "display_name": "opencode-go", "has_key": true, "models": ["static-model-1"], "models_total": 1}
                ],
                "active_provider": "opencode-go"
            }"""
        ).build())
        server.enqueue(MockResponse.Builder().code(500).body("Internal Server Error").build())

        val result = api(server).modelProviders()

        assertEquals(1, result.size)
        assertEquals("opencode-go", result[0].slug)
        assertEquals(listOf("static-model-1"), result[0].models)
    }
}
