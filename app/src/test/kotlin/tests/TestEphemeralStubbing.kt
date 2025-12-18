package tests

import annotations.DecisionTableId
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import se.strawberry.support.base.BaseApiTest
import se.strawberry.support.fixtures.SessionFixtures
import se.strawberry.support.fixtures.StubFixtures
import kotlin.random.Random

class TestEphemeralStubbing : BaseApiTest() {
    private val upstreamStatus = 201
    private val upstreamBody = "upstream-default-response"
    private val stubBody = "stub-response"
    private val endpoint = "/api/test"
    private val stubStatus = 200
    private lateinit var sessionId: String

    @BeforeEach
    fun createTestSession() {
        val session = SessionFixtures.createActiveSession("A-123")
        sessionId = session.id
        createSessionInRepository(sessionId)
    }

    @Test
    @DecisionTableId("EPH_1")
    fun shouldIgnoreTtlWhenUsesIsSetAndTtlIsNull() {
        servers.upstream.stubFor(
            get(urlEqualTo(endpoint))
                .willReturn(aResponse()
                    .withStatus(upstreamStatus)
                    .withBody(upstreamBody)
                )
        )

        val stub = StubFixtures.createEphemeralStubRequest(
            path = endpoint,
            status = stubStatus,
            bodyText = stubBody,
            uses = 3,
            ttlMs = null
        )

        proxyClient.createStub(stub, sessionId).close()

        repeat(3) {
            call(sessionId, endpoint).use { response ->
                assertThat(response.code, equalTo(stubStatus))
                assertThat(response.body.string(), equalTo(stubBody))
            }
        }

        call(sessionId, endpoint).use { response ->
            assertThat(response.code, equalTo(upstreamStatus))
            assertThat(response.body.string(), equalTo(upstreamBody))
        }
    }


    @Test
    @DecisionTableId("EPH_2")
    fun shouldApplyWhenBothSet() {
        servers.upstream.stubFor(
            get(urlEqualTo(endpoint))
                .willReturn(aResponse()
                    .withStatus(upstreamStatus)
                    .withBody(upstreamBody)
                )
        )

        val stub = StubFixtures.createEphemeralStubRequest(
            path = endpoint,
            status = stubStatus,
            bodyText = stubBody,
            uses = 5,
            ttlMs = 5000
        )

        proxyClient.createStub(stub, sessionId).close()

        repeat(5) {
            call(sessionId, endpoint).use { response ->
                assertThat(response.code, equalTo(stubStatus))
                assertThat(response.body.string(), equalTo(stubBody))
            }
        }
    }

    @Test
    @DecisionTableId("EPH_3")
    fun shouldStopMatchingWhenNoUsesLeftWhenBothSet() {
        servers.upstream.stubFor(
            get(urlEqualTo(endpoint))
                .willReturn(aResponse()
                    .withStatus(upstreamStatus)
                    .withBody(upstreamBody)
                )
        )

        val stub = StubFixtures.createEphemeralStubRequest(
            path = endpoint,
            status = stubStatus,
            bodyText = stubBody,
            uses = 3,
            ttlMs = 5000
        )

        proxyClient.createStub(stub, sessionId).close()

        repeat(3) {
            call(sessionId, endpoint).use { response ->
                assertThat(response.code, equalTo(stubStatus))
                assertThat(response.body.string(), equalTo(stubBody))
            }
        }

        call(sessionId, endpoint).use { response ->
            assertThat(response.code, equalTo(upstreamStatus))
            assertThat(response.body.string(), equalTo(upstreamBody))
        }
    }

    @Test
    @DecisionTableId("EPH_4")
    fun shouldStopMatchingWhenTtlExpiresWhenBothSet() {
        val ttl = 2000L

        servers.upstream.stubFor(
            get(urlEqualTo(endpoint))
                .willReturn(aResponse()
                    .withStatus(upstreamStatus)
                    .withBody(upstreamBody)
                )
        )

        val stub = StubFixtures.createEphemeralStubRequest(
            path = endpoint,
            status = stubStatus,
            bodyText = stubBody,
            uses = 5,
            ttlMs = ttl
        )

        proxyClient.createStub(stub, sessionId).close()

        val callsBeforeExpiry = Random.nextInt(1, 5)
        repeat(callsBeforeExpiry) {
            call(sessionId, endpoint).use { response ->
                assertThat(response.code, equalTo(stubStatus))
                assertThat(response.body.string(), equalTo(stubBody))
            }
        }

        Thread.sleep(ttl)

        call(sessionId, endpoint).use { response ->
            assertThat(response.code, equalTo(upstreamStatus))
            assertThat(response.body.string(), equalTo(upstreamBody))
        }
    }

    @Test
    @DecisionTableId("EPH_5")
    fun shouldIgnoreUsesWhenTtlIsSetAndUsesAreNull() {
        val ttl = 2000L

        servers.upstream.stubFor(
            get(urlEqualTo(endpoint))
                .willReturn(aResponse()
                    .withStatus(upstreamStatus)
                    .withBody(upstreamBody)
                )
        )

        val stub = StubFixtures.createEphemeralStubRequest(
            path = endpoint,
            status = stubStatus,
            bodyText = stubBody,
            uses = 1,
            ttlMs = ttl
        )

        proxyClient.createStub(stub, sessionId).close()

        repeat(Random.nextInt(1, 5)) {
            call(sessionId, endpoint).use { response ->
                assertThat(response.code, equalTo(stubStatus))
                assertThat(response.body.string(), equalTo(stubBody))
            }
        }

        Thread.sleep(ttl)

        call(sessionId, endpoint).use { response ->
            assertThat(response.code, equalTo(upstreamStatus))
            assertThat(response.body.string(), equalTo(upstreamBody))
        }
    }
}
