package com.noom.interview.fullstack.sleep.controller

import com.noom.interview.fullstack.sleep.api.model.ErrorResponse
import com.noom.interview.fullstack.sleep.exception.ResourceConflictException
import com.noom.interview.fullstack.sleep.exception.ResourceNotFoundException
import com.noom.interview.fullstack.sleep.exception.SleepLogInvalidException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import javax.validation.ConstraintViolationException

private val logger = KotlinLogging.logger {}

/**
 * Centralized exception-to-HTTP-response mapping for the REST API.
 *
 * Domain exceptions are translated to appropriate status codes.
 * Any unhandled exception falls through to a generic 500 response.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(ex: ResourceNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn { "Resource not found: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(error = HttpStatus.NOT_FOUND.reasonPhrase, message = ex.message ?: "Resource not found.")
        )
    }

    @ExceptionHandler(ResourceConflictException::class)
    fun handleConflict(ex: ResourceConflictException): ResponseEntity<ErrorResponse> {
        logger.warn { "Resource conflict: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(error = HttpStatus.CONFLICT.reasonPhrase, message = ex.message ?: "Resource already exists.")
        )
    }

    @ExceptionHandler(SleepLogInvalidException::class)
    fun handleInvalid(ex: SleepLogInvalidException): ResponseEntity<ErrorResponse> {
        logger.warn { "Invalid sleep log: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(error = HttpStatus.BAD_REQUEST.reasonPhrase, message = ex.message ?: "Invalid sleep log.")
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleBadRequest(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        logger.warn { "Malformed request body: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(error = HttpStatus.BAD_REQUEST.reasonPhrase, message = "Invalid request body.")
        )
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException): ResponseEntity<ErrorResponse> {
        logger.warn { "Missing request header: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(error = HttpStatus.BAD_REQUEST.reasonPhrase, message = "Required header '${ex.headerName}' is missing.")
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        logger.warn { "Method argument not valid: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(error = HttpStatus.BAD_REQUEST.reasonPhrase, message = "Invalid request parameters.")
        )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        logger.warn { "Constraint violation: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(error = HttpStatus.BAD_REQUEST.reasonPhrase, message = "Invalid request parameters.")
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error(ex) { "Unexpected error" }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(error = HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase, message = "An unexpected error occurred.")
        )
    }
}
