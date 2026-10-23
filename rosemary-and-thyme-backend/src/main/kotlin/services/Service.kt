package rosemary_and_thyme.services

import org.jooq.DSLContext

object Service {
    private var dslContext: DSLContext? = null

    val recipeService=RecipeService() 
    val userService=UserService()
    val authService=AuthService()      

    val dsl: DSLContext
        get() = checkNotNull(dslContext) { "Service has not been initialized" }

    fun initialize(dsl: DSLContext) {
        check(dslContext == null) {
            "Service has already been initialized"
        }
        dslContext = dsl
    }

    fun clear() {
        dslContext = null
    }
} 


class RecipeService 
class UserService 
class AuthService 