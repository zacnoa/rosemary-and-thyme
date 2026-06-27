package rosemary_and_thyme.backend.services.auth

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.bcrypt.BCrypt
import org.springframework.stereotype.Service
import rosemary_and_thyme.backend.models.LoginResponse
import rosemary_and_thyme.backend.models.RawRefreshToken
import rosemary_and_thyme.backend.repositories.auth.UserRepository

@Service
class UserService(val userRepository: UserRepository,
    val jwtService: JwtService,
    val refreshTokenService: RefreshTokenService,) {


    fun loginUser(email: String, password:String): LoginResponse{

        val user=userRepository.selectUser(email, password)

        check(user!=null){"User email or password does not match"}

        check(BCrypt.checkpw(password,user.password)){"User email or password does not match"}

        val accessToken=jwtService.generateJwtToken(user.id!!,user.email!!)
        val refreshToken=refreshTokenService.issue(user.id!!)

        return LoginResponse(accessToken,refreshToken)

    }

    fun refreshToken(refreshToken: RawRefreshToken){

    }



}