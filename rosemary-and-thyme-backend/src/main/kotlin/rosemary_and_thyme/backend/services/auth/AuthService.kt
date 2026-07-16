package rosemary_and_thyme.backend.services.auth

import rosemary_and_thyme.backend.exceptions.UserCredentialsException
import org.springframework.stereotype.Service
import org.springframework.security.crypto.bcrypt.BCrypt
import rosemary_and_thyme.backend.jooqgenerated.auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.models.RegisterRequest
import rosemary_and_thyme.backend.repositories.auth.UserRepository

@Service
class AuthService(
    private val userRepository: UserRepository,
    val sessionService: SessionService
) {


    fun validateSession(sessionToken: String): UsersRecord =

        sessionService.validateSession(sessionToken)


    fun login(email: String, password: String): String {

        val user =
            userRepository.selectUserByEmail(email) ?: throw UserCredentialsException("Email or password not correct")


        if (!BCrypt.checkpw(password, user.password)) {
            throw UserCredentialsException("Email or password not correct")
        }

        val sessionToken = sessionService.createSession(user.id!!)

        return sessionToken
    }

    fun register(registerRequest: RegisterRequest): String {

        val passwordHash = BCrypt.hashpw(registerRequest.password, BCrypt.gensalt())
        val user = userRepository.insertUser(registerRequest.email, passwordHash, registerRequest.username)

        val sessionToken = sessionService.createSession(user.id!!)

        return sessionToken

    }

    fun logout(sessionToken: String) {
        sessionService.revokeSession(sessionToken)
    }

}