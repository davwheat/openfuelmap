package dev.davwheat.openfuelmap.map.api.model

import java.time.DayOfWeek

data class OpeningTimes(val usualDays: Map<DayOfWeek, DayHours>, val bankHoliday: BankHolidayHours?)

data class DayHours(val open: String, val close: String, val is24Hours: Boolean)

data class BankHolidayHours(
    val type: String,
    val openTime: String,
    val closeTime: String,
    val is24Hours: Boolean,
)
