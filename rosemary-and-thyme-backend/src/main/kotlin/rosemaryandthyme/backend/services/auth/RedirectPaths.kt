package rosemaryandthyme.backend.services.auth

/** Restricts a client-supplied redirect to a safe relative frontend path.
 * @param path the requested post-login path.
 * @return the safe path, or `"/"` for an invalid value.
 */
fun sanitizeRedirectPath(path: String?): String =
    if (path != null && path.startsWith("/") && !path.startsWith("//") && !path.contains("://")) {
        path
    } else {
        "/"
    }
