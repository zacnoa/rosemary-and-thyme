package rosemary_and_thyme.backend.models

data class ApiResponse<T:ResponsePayload>(
    val payload:T?,
    val error:String?
)