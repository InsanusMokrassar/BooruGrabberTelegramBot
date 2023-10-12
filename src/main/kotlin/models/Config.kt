package models

import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val token: String,
    val database: DatabaseConfig,
    val client: HttpClientConfig? = null
)
