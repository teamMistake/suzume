package io.teammistake.suzume.controllers

import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.teammistake.suzume.data.APIError
import io.teammistake.suzume.exception.InferenceServerResponseException
import io.teammistake.suzume.exception.RequestTimeoutException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice


@RestControllerAdvice
class ExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class, MissingKotlinParameterException::class, DataIntegrityViolationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun onIllegalArgument(e: Exception): APIError {
        e.printStackTrace()
        return APIError(e.message);
    }

    @ExceptionHandler(RequestTimeoutException::class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    fun onTimeout(e: RequestTimeoutException): APIError {
        e.printStackTrace()
        return APIError(e.message, e.reqId, e.request)
    }

    @ExceptionHandler(InferenceServerResponseException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun onWeirdResponse(e: InferenceServerResponseException): APIError {
        e.printStackTrace()
        return APIError(e.message, e.reqId, e.request, e.resp)
    }

}