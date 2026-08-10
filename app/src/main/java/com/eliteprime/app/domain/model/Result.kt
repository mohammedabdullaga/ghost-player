package com.eliteprime.app.domain.model

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : Result<Nothing>()

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun error(message: String, throwable: Throwable? = null): Result<Nothing> = Error(message, throwable)
    }
}
