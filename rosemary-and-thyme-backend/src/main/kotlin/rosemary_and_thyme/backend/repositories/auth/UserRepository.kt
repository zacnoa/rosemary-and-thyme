package rosemary_and_thyme.backend.repositories.auth

import org.jooq.DSLContext
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Repository
import rosemary_and_thyme.backend.exceptions.UserAlreadyExistsException
import rosemary_and_thyme.backend.jooqgenerated.auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.jooqgenerated.auth.tables.references.USERS
import java.sql.SQLException
import java.util.UUID

@Repository
class UserRepository(val dsl: DSLContext) {

    fun selectUserByEmail(email: String): UsersRecord? =
        dsl.selectFrom(USERS)
            .where(USERS.EMAIL.eq(email))
            .fetchOne()

    //TODO: add existing email check
    fun insertUser(email: String, password: String, userName: String): UsersRecord {

        try {

            return dsl.insertInto(USERS)
                .set(USERS.EMAIL, email)
                .set(USERS.PASSWORD, password)
                .set(USERS.USERNAME, userName)
                .returning()
                .fetchOne()!!
        } catch (ex: DataAccessException) {
            val sqlEx = ex.cause as SQLException
            if (sqlEx.sqlState == "23505") {
                throw UserAlreadyExistsException("A user with that email already exists")
            }
            throw ex
        }
    }

    fun selectUserById(userId: UUID): UsersRecord? =
        dsl.selectFrom(USERS)
            .where(USERS.ID.eq(userId))
            .fetchOne()


}