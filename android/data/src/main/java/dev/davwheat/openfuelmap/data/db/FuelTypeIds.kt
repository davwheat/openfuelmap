package dev.davwheat.openfuelmap.data.db

/**
 * Canonical fuel type IDs as returned by the API and stored in [FuelTypeEntity.id]. These are
 * stable identifiers used for API filtering and UI ordering.
 */
object FuelTypeIds {
    const val E10 = "E10"
    const val E5 = "E5"
    const val B7_STANDARD = "B7_STANDARD"
}
