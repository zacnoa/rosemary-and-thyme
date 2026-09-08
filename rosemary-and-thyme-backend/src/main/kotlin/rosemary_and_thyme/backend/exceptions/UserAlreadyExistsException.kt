package rosemary_and_thyme.backend.exceptions

/** Thrown by [rosemary_and_thyme.backend.repositories.auth.UserRepository.insertUser] when the email is already registered (translated from a Postgres… */
class UserAlreadyExistsException(message: String) : RuntimeException(message)
