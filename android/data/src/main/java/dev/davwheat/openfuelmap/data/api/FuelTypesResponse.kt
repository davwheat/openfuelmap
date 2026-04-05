package dev.davwheat.openfuelmap.data.api

import kotlinx.serialization.Serializable

@Serializable
data class FuelTypesResponse(val success: Boolean, val result: FuelTypesResultDto? = null)

@Serializable data class FuelTypesResultDto(val fuel_types: List<FuelTypeDto>)

@Serializable data class FuelTypeDto(val id: String, val name: String)
