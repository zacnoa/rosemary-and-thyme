package rosemary_and_thyme.backend.services.auth

/**
 * Whitelists a client-supplied "return to this path after login" value down to a
 * safe, same-origin relative path, defaulting to `"/"` for anything else. Used by
 * both [rosemary_and_thyme.backend.controllers.AuthController.startGoogleLogin] (the
 * `redirect` query param on the way in) and
 * [rosemary_and_thyme.backend.configurations.GoogleOidcSuccessHandler] (the
 * `post_login_redirect` cookie value on the way back out) - re-validated on both
 * ends rather than trusted once, since the cookie is a plain string round-tripped
 * through the browser like any other client-supplied input.
 *
 * Guards against an open redirect: `?redirect=https://evil.example` or the
 * protocol-relative `?redirect=//evil.example` (which browsers still resolve as a
 * full external URL, not a path on this origin) would otherwise send a signed-in
 * user's browser straight to an attacker's site immediately after establishing a
 * trusted session cookie.
 *
 * Mirrors `utils/loginRedirect.ts`'s `sanitizeRedirect` on the frontend, which does
 * the same job for the plain (non-Google) login flow - kept in sync manually, same
 * as `RecipeValidation`'s frontend/backend pair.
 *
 * @param path the client-supplied value, or `null` if none was given
 * @return [path] if it's a safe relative path, `"/"` otherwise
 */
fun sanitizeRedirectPath(path: String?): String =
    if (path != null && path.startsWith("/") && !path.startsWith("//") && !path.contains("://")) {
        path
    } else {
        "/"
    }
