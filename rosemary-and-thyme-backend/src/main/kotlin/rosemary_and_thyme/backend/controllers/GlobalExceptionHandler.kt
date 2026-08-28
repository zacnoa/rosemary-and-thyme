package rosemary_and_thyme.backend.controllers

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import rosemary_and_thyme.backend.exceptions.EmailNotVerifiedException
import rosemary_and_thyme.backend.exceptions.ForbiddenException
import rosemary_and_thyme.backend.exceptions.GoogleAccountException
import rosemary_and_thyme.backend.exceptions.InvalidTokenException
import rosemary_and_thyme.backend.exceptions.RecipeValidationException
import rosemary_and_thyme.backend.exceptions.SessionException
import rosemary_and_thyme.backend.exceptions.TooManyRequestsException
import rosemary_and_thyme.backend.exceptions.UserAlreadyExistsException
import rosemary_and_thyme.backend.exceptions.UserCredentialsException
import java.net.URI


/**
 * Central exception -> HTTP response mapping for every controller. Applies to all
 * `@RestController`s (`@RestControllerAdvice` is global by default), so individual
 * controllers can just throw domain exceptions and never touch [ResponseEntity]/status
 * codes for the error path. Every response body is an RFC 7807 [ProblemDetail].
 *
 * Only covers exceptions raised during Spring MVC's own dispatch - a plain
 * `jakarta.servlet.Filter` (like [rosemary_and_thyme.backend.filters.RateLimitFilter],
 * which runs before dispatch even starts) is not covered and must write its own
 * response directly on error.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /** Wrong email/password on login. @return 401 problem-detail */
    @ExceptionHandler(UserCredentialsException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleUserCredentials(e: UserCredentialsException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.message)
            .apply {
                title = "Incorrect user credentials"
                instance = URI.create(request.requestURI)
            }

    /**
     * Registration with an email that already exists (unique constraint violation -
     * see [rosemary_and_thyme.backend.repositories.auth.UserRepository.insertUser]).
     * @return 409 problem-detail (note: [ResponseStatus] on this method says
     * BAD_REQUEST, but [ProblemDetail.forStatusAndDetail] below sets the actual HTTP
     * status, so the response is a 409 in practice)
     */
    @ExceptionHandler(UserAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleUserAlreadyExists(e: UserAlreadyExistsException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.message)
            .apply {
                title = "Conflict on submitted Email"
                instance = URI.create(request.requestURI)
            }

    /** A lookup (e.g. recipe by id) found nothing. @return 404 problem-detail */
    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(e: NoSuchElementException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.message)
            .apply {
                title = "Invalid recipe Id"
                instance = URI.create(request.requestURI)
            }

    /** Missing/expired/revoked session cookie. @return 403 problem-detail */
    @ExceptionHandler(SessionException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleSession(e: SessionException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.message)
            .apply {
                title = "Error while validating session"
                instance = URI.create(request.requestURI)
            }

    /** Invalid/expired/already-used email verification token. @return 400 problem-detail */
    @ExceptionHandler(InvalidTokenException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidToken(e: InvalidTokenException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message)
            .apply {
                title = "Invalid verification token"
                instance = URI.create(request.requestURI)
            }

    /** Login attempt on an account whose email hasn't been verified yet. @return 403 problem-detail */
    @ExceptionHandler(EmailNotVerifiedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleEmailNotVerified(e: EmailNotVerifiedException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.message)
            .apply {
                title = "Email not verified"
                instance = URI.create(request.requestURI)
            }

    /**
     * A password login (or change-password attempt) against an account that has no
     * password to check - a Google-only account. 422 rather than 401/403 specifically
     * so the frontend can tell this apart from "wrong password" and react differently
     * (`routes/auth/login.tsx` redirects into the Google sign-in flow instead of
     * showing an error) - see [GoogleAccountException]'s own KDoc.
     * @return 422 problem-detail
     */
    @ExceptionHandler(GoogleAccountException::class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    fun handleGoogleAccount(e: GoogleAccountException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.message)
            .apply {
                title = "Google account"
                instance = URI.create(request.requestURI)
            }

    /** Caller authenticated but not authorized for this resource (e.g. not a recipe's owner). @return 403 problem-detail */
    @ExceptionHandler(ForbiddenException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleForbiddenException(e: ForbiddenException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.message)
            .apply {
                title = "Invalid Authorization"
                instance = URI.create(request.requestURI)
            }

    /** Rate limit exceeded (see [rosemary_and_thyme.backend.services.ratelimit.RateLimiterService]). @return 429 problem-detail */
    @ExceptionHandler(TooManyRequestsException::class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    fun handleTooManyRequests(e: TooManyRequestsException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.message)
            .apply {
                title = "Too many requests"
                instance = URI.create(request.requestURI)
            }

    /**
     * A submitted recipe violates a save limit - too many ingredients/steps, or the
     * description/title too long (see [rosemary_and_thyme.backend.services.recipes.RecipeValidation]).
     * The server-side twin of the frontend's own pre-save checks: this is what
     * actually stops an over-limit recipe from being persisted, since the frontend's
     * check is bypassable by calling this API directly.
     *
     * @return 400 problem-detail, `detail` set to the first violated limit's message
     */
    @ExceptionHandler(RecipeValidationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleRecipeValidation(e: RecipeValidationException, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message)
            .apply {
                title = "Invalid recipe"
                instance = URI.create(request.requestURI)
            }

    /**
     * Malformed request bodies (bad JSON, wrong types) and bad path/query values
     * (e.g. a non-UUID string where a UUID is expected) are client mistakes, not
     * server failures - without this they'd otherwise fall through to the generic
     * 500 handler below.
     *
     * @return 400 problem-detail
     */
    @ExceptionHandler(HttpMessageNotReadableException::class, IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBadRequest(e: Exception, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "Malformed request")
            .apply {
                title = "Bad request"
                instance = URI.create(request.requestURI)
            }

    /**
     * Catch-all for anything not handled above. Logs the full exception server-side
     * (unlike the handlers above, which are all expected/domain errors) and returns a
     * detail-free body so internals never leak to the client.
     *
     * @return 500 problem-detail with a generic message
     */
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
