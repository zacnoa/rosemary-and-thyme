package rosemary_and_thyme.services.auth_service

import rosemary_and_thyme.jooq.app_auth.tables.references.USERS
import rosemary_and_thyme.models.exceptions.AuthenticationException
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.AuthService
import rosemary_and_thyme.services.CryptoService
import rosemary_and_thyme.services.Service.dsl

fun AuthService.loginUser(email: String, password: String): UserPrincipal {

    val hashedPassword = CryptoService.hashPassword(password)

    return dsl.selectFrom(USERS)
        .where(USERS.EMAIL.eq(email))
        .and(USERS.PASSWORD.eq(hashedPassword))
        .fetchOne()?.let { record ->
            UserPrincipal(
                id = checkNotNull(record.getValue(USERS.ID)),
                username = checkNotNull(record.getValue(USERS.USERNAME)),
                email = checkNotNull(record.getValue(USERS.EMAIL))
            )

        } ?: throw AuthenticationException("The email or password is not correct")
}