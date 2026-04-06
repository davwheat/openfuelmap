package dev.davwheat.openfuelmap.stats.api.model

enum class TimeRange(val queryParam: String, val label: String) {
    DAYS_7("7d", "7d"),
    DAYS_28("28d", "28d"),
    DAYS_60("60d", "60d"),
    DAYS_90("90d", "90d"),
    DAYS_180("180d", "6m"),
    DAYS_365("365d", "1y"),
}
