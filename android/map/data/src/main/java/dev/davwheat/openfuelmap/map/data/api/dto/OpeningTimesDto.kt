package dev.davwheat.openfuelmap.map.data.api.dto

import dev.davwheat.openfuelmap.map.api.model.BankHolidayHours
import dev.davwheat.openfuelmap.map.api.model.DayHours
import dev.davwheat.openfuelmap.map.api.model.OpeningTimes
import java.time.DayOfWeek
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpeningTimesDto(
    @SerialName("usual_days") val usualDays: UsualDaysDto? = null,
    @SerialName("bank_holiday") val bankHoliday: BankHolidayHoursDto? = null,
) {
    fun toDomain(): OpeningTimes =
        OpeningTimes(
            usualDays = usualDays?.toDomain().orEmpty(),
            bankHoliday = bankHoliday?.toDomain(),
        )
}

@Serializable
data class UsualDaysDto(
    val monday: DayHoursDto? = null,
    val tuesday: DayHoursDto? = null,
    val wednesday: DayHoursDto? = null,
    val thursday: DayHoursDto? = null,
    val friday: DayHoursDto? = null,
    val saturday: DayHoursDto? = null,
    val sunday: DayHoursDto? = null,
) {
    fun toDomain(): Map<DayOfWeek, DayHours> = buildMap {
        monday?.toDomain()?.let { put(DayOfWeek.MONDAY, it) }
        tuesday?.toDomain()?.let { put(DayOfWeek.TUESDAY, it) }
        wednesday?.toDomain()?.let { put(DayOfWeek.WEDNESDAY, it) }
        thursday?.toDomain()?.let { put(DayOfWeek.THURSDAY, it) }
        friday?.toDomain()?.let { put(DayOfWeek.FRIDAY, it) }
        saturday?.toDomain()?.let { put(DayOfWeek.SATURDAY, it) }
        sunday?.toDomain()?.let { put(DayOfWeek.SUNDAY, it) }
    }
}

@Serializable
data class DayHoursDto(
    val open: String,
    val close: String,
    @SerialName("is_24_hours") val is24Hours: Boolean,
) {
    fun toDomain(): DayHours = DayHours(open = open, close = close, is24Hours = is24Hours)
}

@Serializable
data class BankHolidayHoursDto(
    val type: String,
    @SerialName("open_time") val openTime: String,
    @SerialName("close_time") val closeTime: String,
    @SerialName("is_24_hours") val is24Hours: Boolean,
) {
    fun toDomain(): BankHolidayHours =
        BankHolidayHours(
            type = type,
            openTime = openTime,
            closeTime = closeTime,
            is24Hours = is24Hours,
        )
}
