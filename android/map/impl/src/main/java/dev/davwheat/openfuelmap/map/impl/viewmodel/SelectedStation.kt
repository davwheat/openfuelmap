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
