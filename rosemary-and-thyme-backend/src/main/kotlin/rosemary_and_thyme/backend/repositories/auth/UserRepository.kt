package rosemary_and_thyme.backend.repositories.auth

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import rosemary_and_thyme.backend.jooqgenerated.auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.jooqgenerated.auth.tables.references.USERS

@Repository
class UserRepository(val dsl: DSLContext) {

    fun selectUserByEmail(email: String): UsersRecord? =
        dsl.selectFrom(USERS)
            .where(USERS.EMAIL.eq(email))
            .fetchOne()

    //TODO: add existing email check
    fun insertUser(email:String,password:String,userName:String): UsersRecord =

        dsl.insertInto(USERS)
            .set(USERS.EMAIL, email)
            .set(USERS.PASSWORD, password)
            .set(USERS.USERNAME,userName)
            .returning()
            .fetchOne() ?: throw IllegalStateException("Failed to insert user")


}