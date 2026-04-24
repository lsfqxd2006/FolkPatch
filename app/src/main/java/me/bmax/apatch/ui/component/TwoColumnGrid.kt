package me.bmax.apatch.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A two-column grid layout built on [LazyColumn].
 *
 * Items are permanently assigned to columns by index (even → left, odd → right),
 * so they never jump between columns when their height changes (e.g. during expand/collapse).
 *
 * @param items the data list to display
 * @param key stable key provider for each item
 * @param beforeItems slot for full-width items rendered above the grid (banners, empty states, etc.)
 * @param itemContent composable for each item
 */
@Composable
fun <T> TwoColumnGrid(
    items: List<T>,
    key: (item: T) -> Any,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    verticalSpacing: Dp = 16.dp,
    horizontalSpacing: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(),
    beforeItems: (LazyListScope.() -> Unit)? = null,
    itemContent: @Composable (item: T) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
    ) {
        beforeItems?.invoke(this)

        items.chunked(2).forEach { rowItems ->
            item(key = rowItems.map { key(it) }.joinToString("+")) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)
                ) {
                    rowItems.forEach { item ->
                        key(key(item)) {
                            Box(modifier = Modifier.weight(1f)) {
                                itemContent(item)
                            }
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
