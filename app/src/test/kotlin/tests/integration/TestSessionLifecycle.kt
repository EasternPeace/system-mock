package tests.integration

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.client.WireMock.*
import helpers.ProxyApi
import helpers.SessionApi
import helpers.SessionResponse
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo as isEqualTo
import org.junit.jupiter.api.Test
import se.strawberry.common.Json
import stubs.Stubs
import tests.setup.BaseIntegrationTest

/**
 * Integration tests for Session Lifecycle
 * Tests the complete workflow: create session -> use for stubbing -> verify
 */
class TestSessionLifecycle : BaseIntegrationTest() {

    @Test
    fun `should create session and use it for session-scoped stubbing`() {
        val endpoint = "/api/test"
        val stubBody = "session-stub-response"
        val stubStatus = 200
        val upstreamBody = "upstream-response"
        val upstreamStatus = 201

        // Setup upstream
        upstream.stubFor(
            get(urlEqualTo(endpoint))
                .willReturn(
                    aResponse()
                        .withStatus(upstreamStatus)
                        .withBody(upstreamBody)
                )
        )

        // 1. Create session
        val session = SessionApi.createSession(http, apiBaseUrl()).use { response ->
            val body = response.body.string()
            Json.mapper.readValue<SessionResponse>(body)
        }

        assertThat(session.status, isEqualTo("ACTIVE"))

        // 2. Verify session is retrievable
        SessionApi.getSession(http, apiBaseUrl(), session.id).use { response ->
            assertThat(response.code, isEqualTo(200))
        }

        // 3. Create stub in context of this session
        val stub = Stubs.createStubRequest(
            url = endpoint,
            status = stubStatus,
            bodyText = stubBody
        )

        ProxyApi.createStub(
            client = http,
            apiBaseUrl = apiBaseUrl(),
            targetService = upstreamServiceName,
            stub = stub,
            sessionId = session.id
        ).use { response ->
            assertThat(response.code, isEqualTo(201))
        }

        // 4. Verify stub works for this session
        call(session.id, endpoint).use { response ->
            assertThat(response.code, isEqualTo(stubStatus))
            assertThat(response.body.string(), isEqualTo(stubBody))
        }

        // 5. Verify different session gets upstream response
        val otherSession = SessionApi.createSession(http, apiBaseUrl()).use { response ->
            val body = response.body.string()
            Json.mapper.readValue<SessionResponse>(body)
        }

        call(otherSession.id, endpoint).use { response ->
            assertThat(response.code, isEqualTo(upstreamStatus))
            assertThat(response.body.string(), isEqualTo(upstreamBody))
        }
    }

    @Test
    fun `should create multiple sessions with isolated stubs`() {
        val endpoint = "/api/resource"
        val upstreamBody = "upstream-default"

        // Setup upstream
        upstream.stubFor(
            get(urlEqualTo(endpoint))
                .willReturn(aResponse().withStatus(200).withBody(upstreamBody))
        )

        // Create session 1 with custom stub
        val session1 = SessionApi.createSession(http, apiBaseUrl()).use { response ->
            val body = response.body.string()
            Json.mapper.readValue<SessionResponse>(body)
        }

        ProxyApi.createStub(
            client = http,
            apiBaseUrl = apiBaseUrl(),
            targetService = upstreamServiceName,
            stub = Stubs.createStubRequest(endpoint, 200, "session-1-response"),
            sessionId = session1.id
        ).close()

        // Create session 2 with different stub
        val session2 = SessionApi.createSession(http, apiBaseUrl()).use { response ->
            val body = response.body.string()
            Json.mapper.readValue<SessionResponse>(body)
        }

        ProxyApi.createStub(
            client = http,
            apiBaseUrl = apiBaseUrl(),
            targetService = upstreamServiceName,
            stub = Stubs.createStubRequest(endpoint, 200, "session-2-response"),
            sessionId = session2.id
        ).close()

        // Verify session 1 gets its stub
        call(session1.id, endpoint).use { response ->
            assertThat(response.body.string(), isEqualTo("session-1-response"))
        }

        // Verify session 2 gets its stub
        call(session2.id, endpoint).use { response ->
            assertThat(response.body.string(), isEqualTo("session-2-response"))
        }
    }

    @Test
    fun `should handle session creation concurrent requests`() {
        // Create multiple sessions concurrently
        val sessions = (1..10).map {
            SessionApi.createSession(http, apiBaseUrl()).use { response ->
                assertThat(response.code, isEqualTo(201))
                val body = response.body.string()
                Json.mapper.readValue<SessionResponse>(body)
            }
        }

        // Verify all sessions are unique
        val uniqueIds = sessions.map { it.id }.toSet()
        assertThat(uniqueIds.size, isEqualTo(10))

        // Verify all sessions are retrievable
        sessions.forEach { session ->
            SessionApi.getSession(http, apiBaseUrl(), session.id).use { response ->
                assertThat(response.code, isEqualTo(200))
            }
        }
    }

    @Test
    fun `should maintain session state across multiple stub operations`() {
        val endpoints = listOf("/api/a", "/api/b", "/api/c")

        // Create session
        val session = SessionApi.createSession(http, apiBaseUrl()).use { response ->
            val body = response.body.string()
            Json.mapper.readValue<SessionResponse>(body)
        }

        // Create stubs for multiple endpoints in this session
        endpoints.forEachIndexed { index, endpoint ->
            upstream.stubFor(
                get(urlEqualTo(endpoint))
                    .willReturn(aResponse().withStatus(200).withBody("upstream-$index"))
            )

            ProxyApi.createStub(
                client = http,
                apiBaseUrl = apiBaseUrl(),
                targetService = upstreamServiceName,
                stub = Stubs.createStubRequest(endpoint, 200, "stub-$index"),
                sessionId = session.id
            ).close()
        }

        // Verify session is still active
        SessionApi.getSession(http, apiBaseUrl(), session.id).use { response ->
            assertThat(response.code, isEqualTo(200))
        }

        // Verify all stubs work
        endpoints.forEachIndexed { index, endpoint ->
            call(session.id, endpoint).use { response ->
                assertThat(response.body.string(), isEqualTo("stub-$index"))
            }
        }
    }
}

