package me.bmax.apatch.ui.component

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

private val CornerRadius = 16.dp
private val ConnectionRadius = 5.dp

data class SplicedItemData(
    val key: Any?,
    val visible: Boolean,
    val content: @Composable () -> Unit,
)

class SplicedGroupScope {
    val items = mutableListOf<SplicedItemData>()

    fun item(key: Any? = null, visible: Boolean = true, content: @Composable () -> Unit) {
        items.add(SplicedItemData(key ?: items.size, visible, content))
    }
}

@Composable
fun SplicedColumnGroup(
    modifier: Modifier = Modifier,
    title: String = "",
    flat: Boolean = false,
    content: SplicedGroupScope.() -> Unit,
) {
    val scope = SplicedGroupScope().apply(content)
    val allItems = scope.items

    if (allItems.isEmpty()) return

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
        }

        Column(verticalArrangement = Arrangement.Top) {
            val firstVisibleIndex = allItems.indexOfFirst { it.visible }
            val lastVisibleIndex = allItems.indexOfLast { it.visible }
            val sharedStiffness = Spring.StiffnessMediumLow
            val isAtLeastTiramisu = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

            allItems.forEachIndexed { index, itemData ->
                key(itemData.key) {
                    val zIndex = if (itemData.visible) 0f else 1f

                    AnimatedVisibility(
                        visible = itemData.visible,
                        modifier = Modifier.zIndex(zIndex),
                        enter = expandVertically(
                            animationSpec = spring(stiffness = sharedStiffness),
                            expandFrom = Alignment.Top,
                        ) + fadeIn(animationSpec = spring(stiffness = sharedStiffness)),
                        exit = shrinkVertically(
                            animationSpec = spring(stiffness = sharedStiffness),
                            shrinkTowards = Alignment.Top,
                        ) + fadeOut(animationSpec = spring(stiffness = sharedStiffness)),
                    ) {
                        val isFirst = index == firstVisibleIndex
                        val isLast = index == lastVisibleIndex

                        val targetTopRadius = if (isFirst) CornerRadius else ConnectionRadius
                        val targetBottomRadius = if (isLast) CornerRadius else ConnectionRadius

                        val currentTopRadius = if (isAtLeastTiramisu) {
                            animateDpAsState(
                                targetValue = targetTopRadius,
                                animationSpec = spring(stiffness = sharedStiffness),
                                label = "TopCornerRadius",
                            ).value
                        } else {
                            targetTopRadius
                        }

                        val currentBottomRadius = if (isAtLeastTiramisu) {
                            animateDpAsState(
                                targetValue = targetBottomRadius,
                                animationSpec = spring(stiffness = sharedStiffness),
                                label = "BottomCornerRadius",
                            ).value
                        } else {
                            targetBottomRadius
                        }

                        val shape = RoundedCornerShape(
                            topStart = currentTopRadius,
                            topEnd = currentTopRadius,
                            bottomStart = currentBottomRadius,
                            bottomEnd = currentBottomRadius,
                        )

                        val targetTopPadding = if (isFirst) 0.dp else 2.dp
                        val currentTopPadding = if (isAtLeastTiramisu) {
                            animateDpAsState(
                                targetValue = targetTopPadding,
                                animationSpec = spring(stiffness = sharedStiffness),
                                label = "TopPadding",
                            ).value
                        } else {
                            targetTopPadding
                        }

                        val containerColor = if (flat) {
                            MaterialTheme.colorScheme.surfaceContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }

                        Column(
                            modifier = Modifier
                                .padding(top = currentTopPadding)
                                .graphicsLayer {
                                    this.shape = shape
                                    this.clip = true
                                }
                                .background(containerColor),
                        ) {
                            itemData.content()
                        }
                    }
                }
            }
        }
    }
}
