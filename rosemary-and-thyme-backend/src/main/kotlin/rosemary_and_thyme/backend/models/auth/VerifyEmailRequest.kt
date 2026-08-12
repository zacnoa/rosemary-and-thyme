package rosemary_and_thyme.backend.models.auth

/**
 * Request body for `POST /auth/verify-email`.
 *
 * @property token the raw verification token from the link emailed to the user
 * (see [rosemary_and_thyme.backend.services.auth.EmailVerificationService.issueVerificationToken]).
 * Only its SHA-256 hash is ever stored, so this raw value is checked against that hash.
 */
data class VerifyEmailRequest(val token: String)
