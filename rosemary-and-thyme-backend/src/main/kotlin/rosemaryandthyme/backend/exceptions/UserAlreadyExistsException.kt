package rosemaryandthyme.backend.exceptions

/** Thrown by [rosemaryandthyme.backend.repositories.auth.UserRepository.insertUser] when the email is already registered (translated from a Postgres… */
class UserAlreadyExistsException(message: String) : RuntimeException(message)
