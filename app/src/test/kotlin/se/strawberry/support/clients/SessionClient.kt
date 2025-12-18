package se.strawberry.support.clients

import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import se.strawberry.api.Endpoints
import se.strawberry.common.Json
import se.strawberry.repository.session.SessionRepository

data class SessionResponse(
    val id: String,
    val status: String,
    val createdAt: Long,
    val expiresAt: Long?
)

class SessionClient(private val client: OkHttpClient, private val apiBaseUrl: String) {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun create(name: String? = null, owner: String? = null): SessionResponse {
        val body = Json.mapper.writeValueAsString(
            buildMap {
                name?.let { put("name", it) }
                owner?.let { put("owner", it) }
            }
        ).toRequestBody(JSON)

        return client.newCall(
            Request.Builder()
                .url("$apiBaseUrl${Endpoints.Paths.SESSIONS}")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()
        ).execute().use { response ->
            check(response.code == 201) { "Expected 201, got ${response.code}" }
            Json.mapper.readValue<SessionResponse>(response.body.string())
        }
    }

    fun get(sessionId: String): Response {
        return client.newCall(
            Request.Builder()
                .url("$apiBaseUrl${Endpoints.Paths.SESSIONS}/$sessionId")
                .get()
                .build()
        ).execute()
    }

    fun getBody(sessionId: String): SessionResponse {
        return get(sessionId).use { response ->
            check(response.code == 200) { "Expected 200, got ${response.code}" }
            Json.mapper.readValue<SessionResponse>(response.body.string())
        }
    }

    fun close(sessionId: String): Response {
        val body = Json.mapper.writeValueAsString(mapOf("sessionId" to sessionId)).toRequestBody(JSON)
        return client.newCall(
            Request.Builder()
                .url("$apiBaseUrl${Endpoints.Paths.SESSIONS}/close")
                .patch(body)
                .build()
        ).execute()
    }
}
