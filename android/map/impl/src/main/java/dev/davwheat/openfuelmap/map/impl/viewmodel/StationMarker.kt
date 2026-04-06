package dev.davwheat.openfuelmap.map.impl.viewmodel

import dev.davwheat.openfuelmap.forecourts.api.model.Forecourt

/**
 * Pre-computed display data for a single station marker on the map.
 *
 * All fields are derived on a background dispatcher in [MapViewModel] so the UI can render markers
 * without doing any per-station processing on the main thread.
 */
data class StationMarker(
    val station: Forecourt,
    val label: String,
    /**
     * Normalised position of this station's price within the 10th–90th percentile range returned by
     * the API: `0f` for cheapest, `1f` for most expensive, `null` when the station has no price.
     */
    val colorPosition: Float?,
    val zIndex: Float,
)
