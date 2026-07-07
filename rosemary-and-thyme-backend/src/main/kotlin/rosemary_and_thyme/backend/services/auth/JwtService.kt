package rosemary_and_thyme.backend.services.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import io.jsonwebtoken.Jwts
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID

@Service
class JwtService(
    private val privateKey: RSAPrivateKey,
    private val publicKey: RSAPublicKey,
    @Value("\${jwt.access-token-ttl-minutes}") private val tokenTtl:Long
) {


    fun generateJwtToken(userId: UUID): String{
        val now= Instant.now()

        return Jwts.builder()
            .subject(userId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(tokenTtl,ChronoUnit.MINUTES)))
            .signWith(privateKey,Jwts.SIG.RS256)
            .compact()
    }

    fun parseToken(token:String): Jws<Claims> =

        Jwts.parser()
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(token)

    fun extractUserId(token:String):UUID =
        UUID.fromString(parseToken(token).payload.subject)

}
