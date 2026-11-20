package com.hermes.client.ui.models

import com.hermes.client.data.network.ModelProviderDto
import com.hermes.client.data.repository.ModelFavoritesStore
import com.hermes.client.data.repository.ModelRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelsViewModelTest {
    private val modelsRepo = mockk<ModelRepository>(relaxed = true)
    private val favoritesStore = mockk<ModelFavoritesStore>(relaxed = true)

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { favoritesStore.favorites } returns MutableStateFlow(emptySet())
    }

    @Test fun load_failure_clears_loading_and_sets_error() = runTest {
        coEvery { modelsRepo.providers() } throws RuntimeException("boom")
        val vm = ModelsViewModel(modelsRepo, favoritesStore)
        advanceUntilIdle()

        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.error != null)
    }

    @Test fun retry_after_failure_recovers() = runTest {
        coEvery { modelsRepo.providers() } throws RuntimeException("boom")
        val vm = ModelsViewModel(modelsRepo, favoritesStore)
        advanceUntilIdle()

        assertTrue(vm.state.value.error != null)

        // Retry (the screen's ErrorState action) must clear the error and show the catalog.
        coEvery { modelsRepo.providers() } returns listOf(
            ModelProviderDto(
                slug = "opencode-go",
                name = "opencode-go",
                models = listOf("deepseek-v4.1-flash"),
            ),
        )
        vm.load()
        advanceUntilIdle()

        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.loading)
        assertEquals(1, vm.state.value.providers.size)
    }
}
