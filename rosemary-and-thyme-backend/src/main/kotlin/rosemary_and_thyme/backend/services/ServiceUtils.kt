package rosemary_and_thyme.backend.services
class ServiceUtils(){

    companion object {
        fun extractPublicId(url:String): String=
            url.substringAfter("/upload/")
                .substringAfter("/")
                .substringBeforeLast(".")
    }

}