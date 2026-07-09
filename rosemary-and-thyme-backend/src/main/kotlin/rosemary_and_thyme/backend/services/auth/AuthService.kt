package rosemary_and_thyme.backend.services.auth

import rosemary_and_thyme.backend.exceptions.UserCredentialsException
import org.springframework.stereotype.Service
import org.springframework.security.crypto.bcrypt.BCrypt
import rosemary_and_thyme.backend.models.RegisterRequest
import rosemary_and_thyme.backend.repositories.auth.UserRepository
import utils.throwOnFalse

@Service
class AuthService(
    private val userRepository: UserRepository,
    val sessionService: SessionService
) {

    fun login(email: String, password: String): String {

        val user = userRepository.selectUserByEmail(email)

        throwOnFalse(user!=null,::UserCredentialsException){"Email or password not correct"}

        throwOnFalse(BCrypt.checkpw(password,user!!.password),::UserCredentialsException){"Email or password not correct"}

        val sessionToken = sessionService.createSession(user.id!!)

        return sessionToken
    }

    fun register(registerRequest: RegisterRequest):String {

        val passwordHash= BCrypt.hashpw(registerRequest.password, BCrypt.gensalt())
        val user=userRepository.insertUser(registerRequest.email,passwordHash,registerRequest.username)

        val sessionToken=sessionService.createSession(user.id!!)

        return sessionToken

    }

    fun logout(sessionToken:String) {
        sessionService.revokeSession(sessionToken)
    }
}