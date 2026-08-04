package rosemary_and_thyme.backend.controllers

import jakarta.servlet.http.HttpServletRequest
import org.jooq.exception.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import rosemary_and_thyme.backend.exceptions.ForbiddenException
import rosemary_and_thyme.backend.exceptions.SessionException
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
    fun handleDataAccess(e: DataAccessException, request: HttpServletRequest): ProblemDetail =
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

    @ExceptionHandler(ForbiddenException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleForbiddenException(e: ForbiddenException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.message)
            .apply {
                title = "Invalid Authorization"
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
