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
package dev.davwheat.openfuelmap.map.impl.viewmodel

import dev.davwheat.openfuelmap.forecourts.api.model.Forecourt
import dev.davwheat.openfuelmap.forecourts.api.model.ForecourtDetail

/**
 * UI state for the currently-selected station.
 *
 * [basic] is always present (populated immediately when the user taps a marker) so the detail sheet
 * can render with the information already on hand. [detail] is populated asynchronously once the
 * details endpoint responds; until then, the sheet shows skeleton placeholders for detail-only
 * fields.
 */
data class SelectedStation(val basic: Forecourt, val detail: ForecourtDetail? = null)
