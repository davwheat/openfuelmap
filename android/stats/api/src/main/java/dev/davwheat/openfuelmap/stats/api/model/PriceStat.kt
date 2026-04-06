package dev.davwheat.openfuelmap.stats.api.model

enum class PriceStat(val queryParam: String, val label: String) {
    MEDIAN("median", "Median"),
    TRIMMED_MEAN("trimmed_mean", "Trimmed mean"),
}
