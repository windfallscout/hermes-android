package com.hermes.client.ui.models

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.hermes.client.ui.theme.LocalProfileAccent
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hermes.client.data.network.ModelProviderDto
import com.hermes.client.data.repository.favKey

/**
 * What the catalog area of the picker should render. An error always wins over the spinner, so a
 * failed catalog fetch can never present as endless loading.
 */
sealed interface CatalogStatus {
    data object Loading : CatalogStatus
    data class Failed(val message: String) : CatalogStatus
    data object Ready : CatalogStatus
}

fun catalogStatus(loading: Boolean, error: String?): CatalogStatus = when {
    !error.isNullOrBlank() -> CatalogStatus.Failed(error)
    loading -> CatalogStatus.Loading
    else -> CatalogStatus.Ready
}

/** Which model slot a selection applies to. */
enum class ModelScope { SESSION, DEFAULT }

data class ModelRow(
    val provider: String,       // real provider slug
    val model: String,
    val isFavorite: Boolean,
    val isCurrent: Boolean,
    val effort: String? = null,
)

sealed interface ModelListItem {
    data class Header(val title: String, val isCurrent: Boolean = false) : ModelListItem
    data class Row(val row: ModelRow) : ModelListItem
}

/**
 * Pure: flattens providers into a display list — a pinned Favorites section (only favorites that
 * survive the query), then one header + rows per provider that has >=1 surviving row. A favorited
 * model appears in both places. Deterministic: providers and models keep their input order.
 */
fun modelSelectorRows(
    providers: List<ModelProviderDto>,
    favorites: Set<String>,
    query: String,
    currentProvider: String?,
    currentModel: String?,
): List<ModelListItem> {
    val q = query.trim().lowercase()
    fun matches(provider: String, model: String) =
        q.isEmpty() ||
            model.lowercase().contains(q) ||
            provider.lowercase().contains(q) ||
            "$provider:$model".lowercase().contains(q)
    fun rowOf(provider: String, model: String) = ModelRow(
        provider = provider,
        model = model,
        isFavorite = favKey(provider, model) in favorites,
        isCurrent = provider == currentProvider && model == currentModel,
        effort = parseEffortSuffix(model)?.effort,
    )

    val items = mutableListOf<ModelListItem>()

    val favRows = providers
        .flatMap { p -> p.models.map { m -> p.slug to m } }
        .filter { (prov, m) -> favKey(prov, m) in favorites && matches(prov, m) }
        .map { (prov, m) -> rowOf(prov, m) }
    if (favRows.isNotEmpty()) {
        items += ModelListItem.Header("Favorites")
        favRows.forEach { items += ModelListItem.Row(it) }
    }

    for (p in providers) {
        val rows = p.models.filter { matches(p.slug, it) }.map { rowOf(p.slug, it) }
        if (rows.isEmpty()) continue
        items += ModelListItem.Header(p.name ?: p.slug, isCurrent = p.isCurrent)
        rows.forEach { items += ModelListItem.Row(it) }
    }
    return items
}

// ---- UI (stateless) ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorContent(
    items: List<ModelListItem>,
    query: String,
    onQueryChange: (String) -> Unit,
    scope: ModelScope?,
    onScopeChange: (ModelScope) -> Unit,
    onToggleFavorite: (provider: String, model: String) -> Unit,
    onSelect: (provider: String, model: String) -> Unit,
    pending: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
    effort: String? = null,
    onEffortChange: ((String) -> Unit)? = null,
    catalogLoading: Boolean = false,
    catalogError: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (scope != null) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                SegmentedButton(
                    selected = scope == ModelScope.SESSION,
                    onClick = { onScopeChange(ModelScope.SESSION) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("This chat") }
                SegmentedButton(
                    selected = scope == ModelScope.DEFAULT,
                    onClick = { onScopeChange(ModelScope.DEFAULT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Default") }
            }
        }

        if (onEffortChange != null) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(
                    "Thinking effort",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    items(SELECTABLE_EFFORTS) { level ->
                        FilterChip(
                            selected = level == effort,
                            onClick = { onEffortChange(level) },
                            label = { Text(effortLabel(level)) },
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search models…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )

        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
        val status = catalogStatus(catalogLoading, catalogError)
        // One spinner at a time: a catalog fetch is already rendering the loading row above.
        if (pending && status != CatalogStatus.Loading) {
            CircularProgressIndicator(Modifier.padding(vertical = 8.dp))
        }

        when (status) {
            is CatalogStatus.Failed -> Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = status.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                if (onRetry != null) {
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
            CatalogStatus.Loading -> CircularProgressIndicator(Modifier.padding(vertical = 8.dp))
            CatalogStatus.Ready -> if (items.isEmpty()) {
                Text(
                    text = "No models found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        LazyColumn(Modifier.fillMaxWidth()) {
            items(items) { item ->
                when (item) {
                    is ModelListItem.Header -> Text(
                        text = item.title + if (item.isCurrent) "  (current)" else "",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (item.isCurrent) LocalProfileAccent.current.accent
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, top = 14.dp, bottom = 4.dp),
                    )
                    is ModelListItem.Row -> ModelRowItem(item.row, onToggleFavorite, onSelect)
                }
            }
        }
    }
}

@Composable
private fun ModelRowItem(
    row: ModelRow,
    onToggleFavorite: (String, String) -> Unit,
    onSelect: (String, String) -> Unit,
) {
    val accent = LocalProfileAccent.current.accent
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onSelect(row.provider, row.model) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Allow full model IDs (often 40-80 chars, e.g. provider:org/model:variant) to wrap
            // cleanly rather than ellipsizing mid-name or hiding the current-model indicator.
            Text(
                text = row.model,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (row.isCurrent) FontWeight.Medium else null,
            )
            Text(
                text = buildAnnotatedString {
                    append(row.provider)
                    if (row.effort != null) {
                        append("  ·  ")
                        append(row.effort)
                    }
                    if (row.isCurrent) {
                        append("  ·  ")
                        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium)) {
                            append("current")
                        }
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onToggleFavorite(row.provider, row.model) }) {
            Icon(
                imageVector = if (row.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = if (row.isFavorite) "Unfavorite" else "Favorite",
                tint = if (row.isFavorite) accent
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorSheet(
    items: List<ModelListItem>,
    query: String,
    onQueryChange: (String) -> Unit,
    scope: ModelScope?,
    onScopeChange: (ModelScope) -> Unit,
    onToggleFavorite: (provider: String, model: String) -> Unit,
    onSelect: (provider: String, model: String) -> Unit,
    pending: Boolean,
    error: String?,
    catalogLoading: Boolean = false,
    catalogError: String? = null,
    onRetry: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    effort: String? = null,
    onEffortChange: ((String) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            "Select model",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp),
        )
        ModelSelectorContent(
            items = items, query = query, onQueryChange = onQueryChange,
            scope = scope, onScopeChange = onScopeChange,
            onToggleFavorite = onToggleFavorite, onSelect = onSelect,
            pending = pending, error = error,
            modifier = Modifier.padding(bottom = 24.dp),
            effort = effort,
            onEffortChange = onEffortChange,
            catalogLoading = catalogLoading,
            catalogError = catalogError,
            onRetry = onRetry,
        )
    }
}
