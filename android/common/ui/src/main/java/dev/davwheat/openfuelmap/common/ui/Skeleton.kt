package dev.davwheat.openfuelmap.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.Shimmer
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer

/**
 * Remembers a [Shimmer] configured for skeleton loading placeholders.
 *
 * Create one instance per screen/sheet and share it across every [SkeletonBox] within so the
 * shimmer sweeps stay in sync across all placeholders.
 */
@Composable fun rememberSkeletonShimmer(): Shimmer = rememberShimmer(ShimmerBounds.View)

/**
 * A shimmering rectangular placeholder used while data is loading.
 *
 * The box has no intrinsic size; set width and height via [modifier] (e.g. `Modifier.size(...)` or
 * `Modifier.fillMaxWidth().height(...)`). Pass the [shimmer] from [rememberSkeletonShimmer] so
 * multiple skeletons animate together.
 */
@Composable
fun SkeletonBox(
    shimmer: Shimmer,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
) {
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .shimmer(shimmer)
    )
}
