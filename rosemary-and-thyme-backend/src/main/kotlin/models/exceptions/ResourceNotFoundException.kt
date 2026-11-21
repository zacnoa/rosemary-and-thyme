package rosemary_and_thyme.models.exceptions

/** Indicates that a requested application resource does not exist. */
class ResourceNotFoundException(message:String):RuntimeException(message)
