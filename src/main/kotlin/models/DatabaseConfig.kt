package models

import dev.inmo.kslog.common.e
import dev.inmo.kslog.common.logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.postgresql.Driver
import java.sql.Connection

@Serializable
data class DatabaseConfig(
    val url: String = "jdbc:pgsql://localhost:12346/test",
    val driver: String = Driver::class.qualifiedName!!,
    val username: String = "",
    val password: String = "",
    val reconnectOptions: DBConnectOptions? = DBConnectOptions()
) {
    @Transient
    val database: Database = (0 until (reconnectOptions ?.attempts ?: 1)).firstNotNullOfOrNull {
        runCatching {
            Database.connect(
                url,
                driver,
                username,
                password
            ).also {
                it.transactionManager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE // Or Connection.TRANSACTION_READ_UNCOMMITTED
                it.connector().close()
            }
        }.onFailure {
            logger.e(it)
            Thread.sleep(reconnectOptions ?.delay ?: return@onFailure)
        }.getOrNull()
    } ?: error("Unable to create database")
}
