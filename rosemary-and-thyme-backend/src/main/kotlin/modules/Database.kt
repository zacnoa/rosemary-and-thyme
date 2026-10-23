package rosemary_and_thyme.modules

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

fun Application.configureDatabase(): DSLContext{

    val databaseConfig=environment.config.config("database")

    val hikariConfig= HikariConfig().apply{
        jdbcUrl = databaseConfig.property("url").getString()
        username = databaseConfig.property("user").getString()
        password = databaseConfig.property("password").getString()
        
        maximumPoolSize=10
    }
    val dataSource= HikariDataSource(hikariConfig)
    
    monitor.subscribe(ApplicationStopped){
        dataSource.close()
    }
    
    return DSL.using(dataSource, SQLDialect.POSTGRES)
}