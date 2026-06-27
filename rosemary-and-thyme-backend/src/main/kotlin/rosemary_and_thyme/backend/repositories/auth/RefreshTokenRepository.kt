package rosemary_and_thyme.backend.repositories.auth

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import rosemary_and_thyme.backend.jooqgenerated.auth.tables.records.RefreshTokensRecord
import rosemary_and_thyme.backend.jooqgenerated.auth.tables.references.REFRESH_TOKENS
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@Repository
class RefreshTokenRepository(val dsl: DSLContext) {

    fun insertRefreshToken(userId: UUID, familyId: UUID, tokenHash:String) {

        dsl.insertInto(REFRESH_TOKENS)
            .set(REFRESH_TOKENS.USER_ID, userId)
            .set(REFRESH_TOKENS.FAMILY_ID, familyId)
            .set(REFRESH_TOKENS.TOKEN_HASH, tokenHash)
            .set(REFRESH_TOKENS.EXPIRES_AT, LocalDateTime.now().plus(30, ChronoUnit.DAYS))
            .execute()
    }

    fun selectRefreshToken(hashedToken:String): RefreshTokensRecord =
        dsl.selectFrom(REFRESH_TOKENS)
            .where(REFRESH_TOKENS.TOKEN_HASH.eq(hashedToken))
            .forUpdate()
            .fetchOne() ?:  throw NoSuchElementException("No refresh token found!")

    fun revokeRefreshTokenFamily(familyId: UUID) {

        dsl.update(REFRESH_TOKENS)
            .set(REFRESH_TOKENS.REVOKED,true)
            .where(REFRESH_TOKENS.FAMILY_ID.eq(familyId))
            .execute()
    }

    fun useRefreshToken(tokenHash:String){
        dsl.update(REFRESH_TOKENS)
            .set(REFRESH_TOKENS.USED,true)
            .where(REFRESH_TOKENS.TOKEN_HASH.eq(tokenHash))
            .execute()
    }

    fun revokeRefreshToken(tokenHash:String){
        dsl.update(REFRESH_TOKENS)
        .set(REFRESH_TOKENS.USED,false)
        .where(REFRESH_TOKENS.TOKEN_HASH.eq(tokenHash))
        .execute()
    }
    fun revokeAllRefreshTokensForUser(userId: UUID) {
        dsl.update(REFRESH_TOKENS)
            .set(REFRESH_TOKENS.REVOKED,true)
            .where(REFRESH_TOKENS.USER_ID.eq(userId))
            .execute()
    }

}