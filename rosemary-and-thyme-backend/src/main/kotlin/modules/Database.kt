package rosemary_and_thyme.modules

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/**
 * Creates a PostgreSQL [DSLContext] backed by a HikariCP pool of up to 20 connections.
 *
 * @return the jOOQ context used by the service layer
 */
fun Application.configureDatabase(): DSLContext{

    val databaseConfig=environment.config.config("database")

    val hikariConfig= HikariConfig().apply{
        jdbcUrl = databaseConfig.property("url").getString()
        username = databaseConfig.property("user").getString()
        password = databaseConfig.property("password").getString()
        
        maximumPoolSize=20
    }
    val dataSource= HikariDataSource(hikariConfig)

    return DSL.using(dataSource, SQLDialect.POSTGRES)
}
