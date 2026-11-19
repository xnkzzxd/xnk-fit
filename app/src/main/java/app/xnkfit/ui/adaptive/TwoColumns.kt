package app.xnkfit.ui.adaptive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Widest a single column of content gets, so a sheet-like screen is not stretched across a tablet. */
val MaxReadingWidth: Dp = 640.dp

/**
 * A screen body in two parts (PRD N-4). Under 600 dp it is one list, [left] then [right], exactly as before.
 * At 600 dp and wider, [left] and [right] sit side by side, each scrolling on its own (list left, detail or summary right).
 */
@Composable
fun TwoColumns(
    modifier: Modifier = Modifier,
    split: Boolean = currentTwoColumns(),
    spacing: Dp = 12.dp,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp),
    left: LazyListScope.() -> Unit,
    right: LazyListScope.() -> Unit,
) {
    if (split) {
        Row(modifier.fillMaxSize().padding(padding), Arrangement.spacedBy(16.dp)) {
            LazyColumn(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing), content = left)
            LazyColumn(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing), content = right)
        }
    } else {
        LazyColumn(modifier.fillMaxWidth().padding(padding), verticalArrangement = Arrangement.spacedBy(spacing)) {
            left()
            right()
        }
    }
}
