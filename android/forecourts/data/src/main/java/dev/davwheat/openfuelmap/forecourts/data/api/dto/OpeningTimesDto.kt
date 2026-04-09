/**
 * Open Fuel Map
 * Copyright (C) 2026  David Wheatley
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.davwheat.openfuelmap.forecourts.data.api.dto

import dev.davwheat.openfuelmap.forecourts.api.model.BankHolidayHours
import dev.davwheat.openfuelmap.forecourts.api.model.DayHours
import dev.davwheat.openfuelmap.forecourts.api.model.OpeningTimes
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
