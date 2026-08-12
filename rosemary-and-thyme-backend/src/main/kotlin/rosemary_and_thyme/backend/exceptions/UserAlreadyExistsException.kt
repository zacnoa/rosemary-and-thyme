package rosemary_and_thyme.backend.exceptions

/**
 * Thrown by [rosemary_and_thyme.backend.repositories.auth.UserRepository.insertUser]
 * when the email is already registered (translated from a Postgres unique-violation).
 * Mapped to a 409 problem-detail by
 * [rosemary_and_thyme.backend.controllers.GlobalExceptionHandler.handleUserAlreadyExists].
 */
class UserAlreadyExistsException(message: String) : RuntimeException(message)
