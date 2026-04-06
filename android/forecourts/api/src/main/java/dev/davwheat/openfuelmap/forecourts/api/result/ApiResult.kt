package dev.davwheat.openfuelmap.forecourts.api.result

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>

    sealed interface Failure : ApiResult<Nothing> {
        val message: String
    }

    data class ApiError(override val message: String) : Failure

    data class NetworkError(override val message: String, val cause: Throwable? = null) : Failure
}
