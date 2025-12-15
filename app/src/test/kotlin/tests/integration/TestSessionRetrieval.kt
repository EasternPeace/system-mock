package tests.integration

import com.fasterxml.jackson.module.kotlin.readValue
import helpers.SessionApi
import helpers.SessionResponse
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
import se.strawberry.common.Json
import tests.setup.BaseIntegrationTest

/**
 * Integration tests for Session Retrieval API
 */
class TestSessionRetrieval : BaseIntegrationTest() {

    @Test
    fun `should retrieve existing session with 200 OK`() {
        // Create session
        val sessionId = SessionApi.createSession(http, apiBaseUrl()).use { response ->
            val body = response.body?.string() ?: ""
            Json.mapper.readValue<SessionResponse>(body).id
        }

        // Retrieve session
        SessionApi.getSession(http, apiBaseUrl(), sessionId).use { response ->
            assertThat(response.code, equalTo(200))
            assertThat(response.header("Content-Type"), containsString("application/json"))
        }
    }

    @Test
    fun `should return 404 for non-existent session`() {
        val nonExistentSessionId = "non-existent-session-id"

        SessionApi.getSession(http, apiBaseUrl(), nonExistentSessionId).use { response ->
            assertThat(response.code, equalTo(404))
        }
    }

    @Test
    fun `should return correct session data`() {
        // Create session
        val created = SessionApi.createSession(http, apiBaseUrl()).use { response ->
            val body = response.body.string()
            Json.mapper.readValue<SessionResponse>(body)
        }

        // Retrieve session
        SessionApi.getSession(http, apiBaseUrl(), created.id).use { response ->
            assertThat(response.code, equalTo(200))

            val body = response.body.string()
            val retrieved = Json.mapper.readValue<SessionResponse>(body)

            assertThat(retrieved.id, equalTo(created.id))
            assertThat(retrieved.status, equalTo(created.status))
            assertThat(retrieved.createdAt, equalTo(created.createdAt))
            assertThat(retrieved.expiresAt, equalTo(created.expiresAt))
        }
    }

    @Test
    fun `should retrieve session multiple times with consistent data`() {
        // Create session
        val sessionId = SessionApi.createSession(http, apiBaseUrl()).use { response ->
            val body = response.body.string()
            Json.mapper.readValue<SessionResponse>(body).id
        }

        val retrievedSessions = mutableListOf<SessionResponse>()

        // Retrieve session 3 times
        repeat(3) {
            SessionApi.getSession(http, apiBaseUrl(), sessionId).use { response ->
                assertThat(response.code, equalTo(200))

                val body = response.body.string()
                val session = Json.mapper.readValue<SessionResponse>(body)
                retrievedSessions.add(session)
            }
        }

        // Verify all retrievals return the same data
        assertThat(retrievedSessions.size, equalTo(3))
        assertThat(retrievedSessions.map { it.id }.toSet().size, equalTo(1))
        assertThat(retrievedSessions.map { it.status }.toSet().size, equalTo(1))
        assertThat(retrievedSessions.map { it.createdAt }.toSet().size, equalTo(1))
    }
}

