package rosemary_and_thyme.extensions

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat


fun String.sha256(): String {
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(StandardCharsets.UTF_8))
    return HexFormat.of().formatHex(bytes)

}