package dev.davwheat.openfuelmap.data.db

/**
 * Canonical fuel type IDs as returned by the API and stored in [FuelTypeEntity.id]. These are
 * stable identifiers used for API filtering and UI ordering.
 */
object FuelTypeIds {
    const val E10 = "E10"
    const val E5 = "E5"
    const val B7_STANDARD = "B7_STANDARD"

    /**
     * Canonical display/selection priority. Used both for ordering the fuel-type chips in the UI
     * and for picking the initial default when the user has not yet chosen one. Fuel types not in
     * this list sort after these, in their natural order.
     */
    val PRIORITY_ORDER: List<String> = listOf(E10, E5, B7_STANDARD)
}
