package tests

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import se.strawberry.support.base.BaseApiTest

class TestSessionHeaderRequired : BaseApiTest() {

    @Test
    fun shouldRejectWhenSessionHeaderMissing() {
        val endpoint = "/api/test"
        val upstreamStatus = 200
        servers.upstream.stubFor(
            get(urlEqualTo(endpoint))
                .willReturn(
                    aResponse()
                        .withStatus(upstreamStatus)
                        .withBody("ok")
                )
        )

        call(sessionId = null, path = endpoint).use { resp ->
            assertThat(resp.code, equalTo(400))
        }
    }
}

