package rosemary_and_thyme.backend.configurations

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

@Configuration
class RsaKeyConfig(
    @Value("\${jwt.private-key}") private val privateKeyBase64: String,
    @Value("\${jwt.public-key}") private val publicKeyBase64: String
) {
    @Bean
    fun rsaPrivateKey(): RSAPrivateKey {
        val decoded = Base64.getDecoder().decode(privateKeyBase64.trim())
        val keySpec = PKCS8EncodedKeySpec(decoded)
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec) as RSAPrivateKey
    }

    @Bean
    fun rsaPublicKey(): RSAPublicKey {
        val decoded = Base64.getDecoder().decode(publicKeyBase64.trim())
        val keySpec = X509EncodedKeySpec(decoded)
        return KeyFactory.getInstance("RSA").generatePublic(keySpec) as RSAPublicKey
    }
}
