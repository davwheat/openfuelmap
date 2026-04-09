/**
 * Open Fuel Map
 * Copyright (C) 2026  David Wheatley
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.davwheat.openfuelmap.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun SkeletonBoxPreview() {
    MaterialExpressiveTheme {
        Surface {
            val shimmer = rememberSkeletonShimmer()
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkeletonBox(shimmer = shimmer, shape = CircleShape, modifier = Modifier.size(48.dp))
                SkeletonBox(shimmer = shimmer, modifier = Modifier.fillMaxWidth().height(20.dp))
                SkeletonBox(shimmer = shimmer, modifier = Modifier.fillMaxWidth(0.6f).height(16.dp))
            }
        }
    }
}
