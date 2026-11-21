package rosemary_and_thyme.models.exceptions

/** Indicates that supplied credentials could not authenticate a user. */
class AuthenticationException(message:String):RuntimeException(message)
