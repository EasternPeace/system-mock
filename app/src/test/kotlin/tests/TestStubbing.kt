package tests

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import se.strawberry.support.base.BaseApiTest
import se.strawberry.support.fixtures.SessionFixtures
import se.strawberry.support.fixtures.StubFixtures

class TestStubbingOnlyWorksWithinTheSameSession : BaseApiTest() {

    @Test
    fun test() {
        val upstreamStatus = 201
        val upstreamBody = "upstream-default-response"
        val stubBody = "stub-response"
        val endpoint = "/api/test"
        val stubStatus = 200

        val session = SessionFixtures.createActiveSession()
        createSessionInRepository(session.id)

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
            uses = 3
        )

        val createResp = proxyClient.createStub(stub, session.id)
        assertThat("Stub creation should be successful (${createResp.code})",
            createResp.code in listOf(200, 201), equalTo(true))
        createResp.close()

        proxyClient.callEndpoint(endpoint, session.id).use { response ->
            assertThat(response.code, equalTo(stubStatus))
            assertThat(response.body.string(), equalTo(stubBody))
        }

        proxyClient.callEndpoint(endpoint, session.id).use { response ->
            assertThat(response.code, equalTo(stubStatus))
            assertThat(response.body.string(), equalTo(stubBody))
        }

        proxyClient.callEndpoint(endpoint, "unknown").use { response ->
            assertThat(response.code, equalTo(403))
        }
    }
}
