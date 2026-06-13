package rosemary_and_thyme.backend.models

/**
 * data Class that should be used for every request response
 * if there are no errors then leave error as null
 */
data class ApiResponse<T:ResponsePayload>(
    val payload:T,
)