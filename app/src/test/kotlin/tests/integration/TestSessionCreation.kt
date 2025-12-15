package tests.integration

import com.fasterxml.jackson.module.kotlin.readValue
import helpers.SessionApi
import helpers.SessionResponse
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
import se.strawberry.common.Json
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import tests.setup.BaseIntegrationTest

/**
 * Integration tests for Session Creation API
 * Tests the full flow: API -> DynamoDB container
 */
class TestSessionCreation : BaseIntegrationTest() {

    @Test
    fun `should create session and return 201 Created`() {
        // Create session
        val response = SessionApi.createSession(http, apiBaseUrl())

        // Verify response status
        assertThat(response.code, equalTo(201))
        assertThat(response.header("Content-Type"), containsString("application/json"))

        // Parse session ID from response
        val body = response.body.string()
        val session = Json.mapper.readValue<SessionResponse>(body)

        // Verify record was created in DynamoDB
        val dbItem = dynamoClient.getItem { builder ->
            builder.tableName("proxy-sessions")
                .key(mapOf("sessionId" to AttributeValue.builder().s(session.id).build()))
        }

        assertThat(dbItem.hasItem(), equalTo(true))
        assertThat(dbItem.item()["sessionId"]?.s(), equalTo(session.id))
        assertThat(dbItem.item()["status"]?.s(), equalTo("ACTIVE"))
    }

    @Test
    fun `should create session with valid ID and ACTIVE status`() {
        // Create session
        SessionApi.createSession(http, apiBaseUrl()).use { response ->
            assertThat(response.code, equalTo(201))

            val body = response.body.string()
            val session = Json.mapper.readValue<SessionResponse>(body)

            // Verify session data
            assertThat(session.id, not(emptyString()))
            assertThat(session.status, equalTo("ACTIVE"))
            assertThat(session.createdAt, greaterThan(0L))
        }
    }

    @Test
    fun `should persist session to DynamoDB`() {
        // Create session
        val sessionId = SessionApi.createSession(http, apiBaseUrl()).use { response ->
            val body = response.body.string()
            Json.mapper.readValue<SessionResponse>(body).id
        }

        // Verify session can be retrieved
        SessionApi.getSession(http, apiBaseUrl(), sessionId).use { response ->
            assertThat(response.code, equalTo(200))

            val body = response.body.string()
            val session = Json.mapper.readValue<SessionResponse>(body)

            assertThat(session.id, equalTo(sessionId))
            assertThat(session.status, equalTo("ACTIVE"))
        }
    }

    @Test
    fun `should create multiple sessions with unique IDs`() {
        val sessionIds = mutableSetOf<String>()

        // Create 5 sessions
        repeat(5) {
            SessionApi.createSession(http, apiBaseUrl()).use { response ->
                assertThat(response.code, equalTo(201))

                val body = response.body.string()
                val session = Json.mapper.readValue<SessionResponse>(body)

                sessionIds.add(session.id)
            }
        }

        // Verify all IDs are unique
        assertThat(sessionIds.size, equalTo(5))

        // Verify all sessions are retrievable
        sessionIds.forEach { sessionId ->
            SessionApi.getSession(http, apiBaseUrl(), sessionId).use { response ->
                assertThat(response.code, equalTo(200))
            }
        }
    }

    @Test
    fun `should create session immediately available for retrieval`() {
        // Create session
        val sessionId = SessionApi.createSession(http, apiBaseUrl()).use { response ->
            val body = response.body.string()
            Json.mapper.readValue<SessionResponse>(body).id
        }

        // Immediately retrieve it (no delay)
        SessionApi.getSession(http, apiBaseUrl(), sessionId).use { response ->
            assertThat(response.code, equalTo(200))

            val body = response.body.string()
            val session = Json.mapper.readValue<SessionResponse>(body)

            assertThat(session.id, equalTo(sessionId))
            assertThat(session.status, equalTo("ACTIVE"))
        }
    }
}

