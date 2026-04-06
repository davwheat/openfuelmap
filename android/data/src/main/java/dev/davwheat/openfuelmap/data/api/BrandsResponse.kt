package dev.davwheat.openfuelmap.data.api

import kotlinx.serialization.Serializable

@Serializable data class BrandsResponse(val success: Boolean, val result: BrandsResultDto? = null)

@Serializable data class BrandsResultDto(val brands: List<BrandDto>)

@Serializable data class BrandDto(val name: String, val forecourt_count: Int)
