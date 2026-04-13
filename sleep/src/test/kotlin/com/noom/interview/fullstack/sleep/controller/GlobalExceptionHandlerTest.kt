package com.noom.interview.fullstack.sleep.controller

import com.noom.interview.fullstack.sleep.exception.ResourceConflictException
import com.noom.interview.fullstack.sleep.exception.ResourceNotFoundException
import com.noom.interview.fullstack.sleep.exception.SleepLogInvalidException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import javax.validation.ConstraintViolationException

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `maps SleepLogInvalidException to 400 Bad Request`() {
        val ex = SleepLogInvalidException("Bed time must be before wake time")

        val response = handler.handleInvalid(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body!!.error).isEqualTo("Bad Request")
        assertThat(response.body!!.message).isEqualTo("Bed time must be before wake time")
    }

    @Test
    fun `maps unexpected Exception to 500 Internal Server Error`() {
        val ex = RuntimeException("something broke")

        val response = handler.handleUnexpected(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body!!.error).isEqualTo("Internal Server Error")
        assertThat(response.body!!.message).isEqualTo("An unexpected error occurred.")
    }

    @Test
    fun `maps ResourceNotFoundException to 404 Not Found`() {
        val ex = ResourceNotFoundException("Sleep log not found")

        val response = handler.handleNotFound(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body!!.error).isEqualTo("Not Found")
        assertThat(response.body!!.message).isEqualTo("Sleep log not found")
    }

    @Test
    fun `maps ResourceConflictException to 409 Conflict`() {
        val ex = ResourceConflictException("User already exists")

        val response = handler.handleConflict(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body!!.error).isEqualTo("Conflict")
        assertThat(response.body!!.message).isEqualTo("User already exists")
    }

    @Test
    fun `maps HttpMessageNotReadableException to 400 Bad Request`() {
        val ex = HttpMessageNotReadableException(
            "JSON parse error",
            MockHttpInputMessage(ByteArray(0))
        )

        val response = handler.handleBadRequest(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body!!.error).isEqualTo("Bad Request")
        assertThat(response.body!!.message).isEqualTo("Invalid request body.")
    }

    @Test
    fun `maps MissingRequestHeaderException to 400 Bad Request`() {
        val method = GlobalExceptionHandler::class.java.getMethod(
            "handleMissingHeader",
            MissingRequestHeaderException::class.java
        )
        val ex = MissingRequestHeaderException(
            "X-User-Id",
            org.springframework.core.MethodParameter(method, -1)
        )

        val response = handler.handleMissingHeader(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body!!.error).isEqualTo("Bad Request")
        assertThat(response.body!!.message).isEqualTo("Required header 'X-User-Id' is missing.")
    }

    @Test
    fun `maps MethodArgumentNotValidException to 400 Bad Request`() {
        val bindingResult = BeanPropertyBindingResult(null, "request")
        val method = GlobalExceptionHandler::class.java.getMethod(
            "handleMethodArgumentNotValid",
            MethodArgumentNotValidException::class.java
        )
        val ex = MethodArgumentNotValidException(
            org.springframework.core.MethodParameter(method, -1),
            bindingResult
        )

        val response = handler.handleMethodArgumentNotValid(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body!!.error).isEqualTo("Bad Request")
        assertThat(response.body!!.message).isEqualTo("Invalid request parameters.")
    }

    @Test
    fun `maps ConstraintViolationException to 400 Bad Request`() {
        val ex = ConstraintViolationException("must be greater than or equal to 1", emptySet())

        val response = handler.handleConstraintViolation(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body!!.error).isEqualTo("Bad Request")
        assertThat(response.body!!.message).isEqualTo("Invalid request parameters.")
    }
}
