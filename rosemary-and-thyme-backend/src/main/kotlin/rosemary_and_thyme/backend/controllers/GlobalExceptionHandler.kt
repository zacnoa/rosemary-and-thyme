package rosemary_and_thyme.backend.controllers

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import rosemary_and_thyme.backend.exceptions.EmailNotVerifiedException
import rosemary_and_thyme.backend.exceptions.ForbiddenException
import rosemary_and_thyme.backend.exceptions.InvalidTokenException
import rosemary_and_thyme.backend.exceptions.SessionException
import rosemary_and_thyme.backend.exceptions.TooManyRequestsException
import rosemary_and_thyme.backend.exceptions.UserAlreadyExistsException
import rosemary_and_thyme.backend.exceptions.UserCredentialsException
import java.net.URI


@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(UserCredentialsException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleUserCredentials(e: UserCredentialsException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.message)
            .apply {
                title = "Incorrest user credentials"
                instance = URI.create(request.requestURI)
            }

    @ExceptionHandler(UserAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleDataAccess(e: UserAlreadyExistsException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.message)
            .apply {
                title = "Conflict on submitted Email"
                instance = URI.create(request.requestURI)
            }

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(e: NoSuchElementException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.message)
            .apply {
                title = "Invalid recipe Id"
                instance = URI.create(request.requestURI)
            }

    @ExceptionHandler(SessionException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleSession(e: SessionException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.message)
            .apply {
                title = "Error while validating session"
                instance = URI.create(request.requestURI)
            }

    @ExceptionHandler(InvalidTokenException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidToken(e: InvalidTokenException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message)
            .apply {
                title = "Invalid verification token"
                instance = URI.create(request.requestURI)
            }

    @ExceptionHandler(EmailNotVerifiedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleEmailNotVerified(e: EmailNotVerifiedException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.message)
            .apply {
                title = "Email not verified"
                instance = URI.create(request.requestURI)
            }

    @ExceptionHandler(ForbiddenException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleForbiddenException(e: ForbiddenException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.message)
            .apply {
                title = "Invalid Authorization"
                instance = URI.create(request.requestURI)
            }

    @ExceptionHandler(TooManyRequestsException::class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    fun handleTooManyRequests(e: TooManyRequestsException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.message)
            .apply {
                title = "Too many requests"
                instance = URI.create(request.requestURI)
            }

    /**
     * Malformed request bodies (bad JSON, wrong types) and bad path/query values
     * (e.g. a non-UUID string where a UUID is expected) are client mistakes, not
     * server failures - without this they'd otherwise fall through to the generic
     * 500 handler below.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class, IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBadRequest(e: Exception, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "Malformed request")
            .apply {
                title = "Bad request"
                instance = URI.create(request.requestURI)
            }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleGeneric(e: Exception, request: HttpServletRequest): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred")
            .apply {
                title = "Internal Server Error"
                instance = URI.create(request.requestURI)
            }
    }

}
