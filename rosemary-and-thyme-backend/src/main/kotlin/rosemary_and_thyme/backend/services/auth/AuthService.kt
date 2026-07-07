package rosemary_and_thyme.backend.services.auth

import rosemary_and_thyme.backend.exceptions.UserCredentialsException
import org.springframework.stereotype.Service
import org.springframework.security.crypto.bcrypt.BCrypt
import rosemary_and_thyme.backend.models.JWTRefreshPair
import rosemary_and_thyme.backend.models.RegisterRequest
import rosemary_and_thyme.backend.repositories.auth.UserRepository
import utils.throwOnFalse

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService
) {

    fun login(email: String, password: String): JWTRefreshPair {

        val user = userRepository.selectUserByEmail(email)

        throwOnFalse(user!=null,::UserCredentialsException){"Email or password not correct"}

        throwOnFalse(BCrypt.checkpw(password,user!!.password),::UserCredentialsException){"Email or password not correct"}

        val accessToken = jwtService.generateJwtToken(user.id!!)

        val refreshToken = refreshTokenService.issue(user.id!!)

        return JWTRefreshPair(accessToken, refreshToken)
    }

    fun register(registerRequest: RegisterRequest): JWTRefreshPair{

        val passwordHash= BCrypt.hashpw(registerRequest.password, BCrypt.gensalt())
        val user=userRepository.insertUser(registerRequest.email,passwordHash,registerRequest.username)

        val accessToken = jwtService.generateJwtToken(user.id!!)
        val refreshToken = refreshTokenService.issue(user.id!!)

        return JWTRefreshPair(accessToken,refreshToken)


    }

    fun refresh(rawRefreshToken: String): JWTRefreshPair =

        refreshTokenService.rotate(rawRefreshToken)



    fun logout(refreshToken:String) {
        refreshTokenService.logoutUser(refreshToken)
    }
}