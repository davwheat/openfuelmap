package dev.davwheat.openfuelmap.forecourts.api.model

/**
 * A [Forecourt] paired with its straight-line (great-circle) distance from a reference point. Used
 * by radius-based queries where the caller cares about how far each result is from a search centre.
 */
data class ForecourtWithDistance(val forecourt: Forecourt, val distanceMiles: Double)
