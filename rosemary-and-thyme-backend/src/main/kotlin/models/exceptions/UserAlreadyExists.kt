package rosemary_and_thyme.models.exceptions

/** Indicates that registration conflicts with an existing user. */
class UserAlreadyExists(message:String): RuntimeException(message) 
