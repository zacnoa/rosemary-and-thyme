package rosemaryandthyme.backend.controllers

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import rosemaryandthyme.backend.exceptions.EmailNotVerifiedException
import rosemaryandthyme.backend.exceptions.ForbiddenException
import rosemaryandthyme.backend.exceptions.GoogleAccountException
import rosemaryandthyme.backend.exceptions.InvalidTokenException
import rosemaryandthyme.backend.exceptions.RecipeValidationException
import rosemaryandthyme.backend.exceptions.RecipeVisionComputeException
import rosemaryandthyme.backend.exceptions.RecipeVisionException
import rosemaryandthyme.backend.exceptions.RecipeVisionTimeoutException
import rosemaryandthyme.backend.exceptions.RecipeVisionUnavailableException
import rosemaryandthyme.backend.exceptions.SessionException
import rosemaryandthyme.backend.exceptions.TooManyRequestsException
import rosemaryandthyme.backend.exceptions.UserAlreadyExistsException
import rosemaryandthyme.backend.exceptions.UserCredentialsException
import java.net.URI


/** Central exception -> HTTP response mapping for every controller. */
@RestControllerAdvice
class GlobalExceptionHandler(
    // Read straight from the same properties application.yaml's
    // spring.servlet.multipart.max-file-size/max-request-size set, purely so
    // handleMaxUploadSize's message can never drift out of sync with the actual
    // configured limits the way a hardcoded "8MB"/"30MB" string would.
    @Value($$"${spring.servlet.multipart.max-file-size}") private val maxFileSize: String,
    @Value($$"${spring.servlet.multipart.max-request-size}") private val maxRequestSize: String
) {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /** Handles wrong email or password errors and returns a 401 problem detail. */
    @ExceptionHandler(UserCredentialsException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleUserCredentials(e: UserCredentialsException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.message)
            .apply {
                title = "Incorrect user credentials"
                instance = URI.create(request.requestURI)
            }

    /** Registration with an email that already exists (unique constraint violation - see [rosemaryandthyme.backend.repositories.auth.UserRepository.insertUser]). */
    @ExceptionHandler(UserAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleUserAlreadyExists(e: UserAlreadyExistsException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.message)
            .apply {
                title = "Conflict on submitted Email"
                instance = URI.create(request.requestURI)
            }

    /** Handles missing resources and returns a 404 problem detail. */
    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(e: NoSuchElementException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.message)
            .apply {
                title = "Invalid recipe Id"
                instance = URI.create(request.requestURI)
            }

    /** Handles invalid sessions and returns a 403 problem detail. */
    @ExceptionHandler(SessionException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleSession(e: SessionException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.message)
            .apply {
                title = "Error while validating session"
                instance = URI.create(request.requestURI)
            }

    /** Handles invalid verification tokens and returns a 400 problem detail. */
    @ExceptionHandler(InvalidTokenException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidToken(e: InvalidTokenException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message)
            .apply {
                title = "Invalid verification token"
                instance = URI.create(request.requestURI)
            }

    /** Handles unverified accounts and returns a 403 problem detail. */
    @ExceptionHandler(EmailNotVerifiedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleEmailNotVerified(e: EmailNotVerifiedException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.message)
            .apply {
                title = "Email not verified"
                instance = URI.create(request.requestURI)
            }

    /** A password login (or change-password attempt) against an account that has no password to check - a Google-only account. */
    @ExceptionHandler(GoogleAccountException::class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    fun handleGoogleAccount(e: GoogleAccountException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.message)
            .apply {
                title = "Google account"
                instance = URI.create(request.requestURI)
            }

    /** Handles authorization failures and returns a 403 problem detail. */
    @ExceptionHandler(ForbiddenException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleForbiddenException(e: ForbiddenException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.message)
            .apply {
                title = "Invalid Authorization"
                instance = URI.create(request.requestURI)
            }

    /** Handles rate-limit violations and returns a 429 problem detail. */
    @ExceptionHandler(TooManyRequestsException::class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    fun handleTooManyRequests(e: TooManyRequestsException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.message)
            .apply {
                title = "Too many requests"
                instance = URI.create(request.requestURI)
            }

    /** A submitted recipe violates a save limit - too many ingredients/steps, or the description/title too long (see… */
    @ExceptionHandler(RecipeValidationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleRecipeValidation(e: RecipeValidationException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message)
            .apply {
                title = "Invalid recipe"
                instance = URI.create(request.requestURI)
            }

    /** Malformed request bodies (bad JSON, wrong types) and bad path/query values (e.g. */
    @ExceptionHandler(HttpMessageNotReadableException::class, IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBadRequest(e: Exception, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "Malformed request")
            .apply {
                title = "Bad request"
                instance = URI.create(request.requestURI)
            }

    /** A recipe image upload (or the whole `PUT /recipe/{id}` multipart request) exceeded the caps configured in `application.yaml`… */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    fun handleMaxUploadSize(e: MaxUploadSizeExceededException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "Uploaded file(s) exceed the maximum allowed size of $maxFileSize per image ($maxRequestSize total per recipe)."
        ).apply {
            title = "Upload too large"
            instance = URI.create(request.requestURI)
        }

    /** The upstream vision service rejected the request or failed during extraction. */
    @ExceptionHandler(RecipeVisionException::class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    fun handleRecipeVision(e: RecipeVisionException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.message ?: "Recipe extraction failed")
            .apply {
                title = "Recipe extraction failed"
                instance = URI.create(request.requestURI)
            }

    /** Recipe Vision was reachable but its model could not complete this extraction. */
    @ExceptionHandler(RecipeVisionComputeException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun handleRecipeVisionCompute(
        e: RecipeVisionComputeException,
        request: HttpServletRequest
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.message)
            .apply {
                title = "Recipe vision could not process image"
                instance = URI.create(request.requestURI)
            }

    /** The configured vision service cannot currently be reached. */
    @ExceptionHandler(RecipeVisionUnavailableException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun handleRecipeVisionUnavailable(
        e: RecipeVisionUnavailableException,
        request: HttpServletRequest
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.message)
            .apply {
                title = "Recipe vision unavailable"
                instance = URI.create(request.requestURI)
            }

    /** The configured vision service exceeded the extraction timeout. */
    @ExceptionHandler(RecipeVisionTimeoutException::class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    fun handleRecipeVisionTimeout(e: RecipeVisionTimeoutException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, e.message)
            .apply {
                title = "Recipe extraction timed out"
                instance = URI.create(request.requestURI)
            }

    /** Catch-all for anything not handled above. */
    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleGeneric(e: Exception, request: HttpServletRequest): ProblemDetail {
        // Without this, unhandled exceptions vanish - the client only sees the generic
        // detail below, and nothing at all shows up in the Railway logs to debug from.
        log.error("Unhandled exception on {} {}", request.method, request.requestURI, e)

        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred")
            .apply {
                title = "Internal Server Error"
                instance = URI.create(request.requestURI)
            }
    }

}
