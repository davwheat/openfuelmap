package dev.davwheat.openfuelmap.data

/** User preference for how distances and radii are displayed. */
enum class DistanceUnit {
    MILES,
    KILOMETERS;

    companion object {
        const val MILES_PER_KM: Double = 0.621371
        const val KM_PER_MILE: Double = 1.60934

        fun fromStoredValue(value: String?): DistanceUnit =
            entries.firstOrNull { it.name == value } ?: MILES
    }
}
