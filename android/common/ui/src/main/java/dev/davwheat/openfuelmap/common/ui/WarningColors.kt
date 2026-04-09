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

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val ColorScheme.warning: Color
    get() = if (surface.luminance() > 0.5f) Color(0xFFE65100) else Color(0xFFFFB74D)

val ColorScheme.warningContainer: Color
    get() = warning.copy(alpha = 0.12f)

val ColorScheme.onWarningContainer: Color
    get() = if (surface.luminance() > 0.5f) Color(0xFFBF360C) else Color(0xFFFFCC80)
