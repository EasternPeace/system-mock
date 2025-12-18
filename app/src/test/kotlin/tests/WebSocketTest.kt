package tests

import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
import se.strawberry.config.Env
import se.strawberry.config.EnvVar
import se.strawberry.support.base.BaseApiTest
import se.strawberry.support.fixtures.SessionFixtures
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WebSocketTest : BaseApiTest() {

    @Test
    fun `should receive broadcasted traffic via websocket`() {
        val session = SessionFixtures.createActiveSession()
        createSessionInRepository(session.id)
        val endpoint = "/api/ws-test"

        val latch = CountDownLatch(1)
        val messages = mutableListOf<String>()

        val wsUrl = "ws://localhost:${Env.int(EnvVar.KtorApiPort)}/_proxy-api/ws/traffic"
        val request = okhttp3.Request.Builder().url(wsUrl).build()

        val wsListener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                messages.add(text)
                if (text.contains(session.id)) {
                    latch.countDown()
                }
            }
        }

        val ws = http.newWebSocket(request, wsListener)

        Thread.sleep(500)

        servers.upstream.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo(endpoint))
            .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(200)))

        proxyClient.callEndpoint(endpoint, session.id).close()

        val received = latch.await(5, TimeUnit.SECONDS)
        assertThat("Should have received traffic event", received, `is`(true))
        assertThat(messages.size, greaterThanOrEqualTo(1))
        assertThat(messages[0], containsString(session.id))
        assertThat(messages[0], containsString(endpoint))

        ws.close(1000, "done")
    }

    @Test
    fun `should filter traffic by sessionId`() {
        val targetSession = SessionFixtures.createActiveSession()
        val otherSession = SessionFixtures.createActiveSession()
        createSessionInRepository(targetSession.id)
        createSessionInRepository(otherSession.id)
        val endpoint = "/api/ws-filter"

        val messages = mutableListOf<String>()
        val latch = CountDownLatch(1)
        val wsUrl = "ws://localhost:${Env.int(EnvVar.KtorApiPort)}/_proxy-api/ws/traffic"

        val wsListener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                messages.add(text)
                if (text.contains(targetSession.id)) {
                    latch.countDown()
                }
            }
        }

        val ws = http.newWebSocket(okhttp3.Request.Builder().url(wsUrl).build(), wsListener)

        val filterCmd = """{"sessionId": "${targetSession.id}"}"""
        ws.send(filterCmd)

        Thread.sleep(500)

        servers.upstream.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(com.github.tomakehurst.wiremock.client.WireMock.urlMatching(".*"))
            .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(200)))

        proxyClient.callEndpoint(endpoint, otherSession.id).close()
        Thread.sleep(500)

        proxyClient.callEndpoint(endpoint, targetSession.id).close()

        val received = latch.await(5, TimeUnit.SECONDS)
        assertThat("Should have received target session traffic", received, `is`(true))

        assertThat(messages.toString(), not(containsString("error")))

        val leaked = messages.any { it.contains(otherSession.id) }
        assertThat("Should not receive other session traffic", leaked, `is`(false))

        ws.close(1000, "done")
    }

    @Test
    fun `should reject closed session filter`() {
        val session = SessionFixtures.createActiveSession()
        createSessionInRepository(session.id)

        deps.sessionRepository.close(session.id)

        val messages = mutableListOf<String>()
        val closeLatch = CountDownLatch(1)
        val wsUrl = "ws://localhost:${Env.int(EnvVar.KtorApiPort)}/_proxy-api/ws/traffic"

        val wsListener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                messages.add(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                closeLatch.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closeLatch.countDown()
            }
        }

        val ws = http.newWebSocket(okhttp3.Request.Builder().url(wsUrl).build(), wsListener)

        Thread.sleep(500)

        val filterCmd = """{"sessionId": "${session.id}"}"""
        ws.send(filterCmd)

        val closed = closeLatch.await(5, TimeUnit.SECONDS)
        assertThat("WebSocket should have closed", closed, `is`(true))

        val errorMsg = messages.firstOrNull { it.contains("error") }
        assertThat("Error message should exist", errorMsg, notNullValue())
        assertThat("Error should indicate session is closed", errorMsg, containsString("session_closed"))
    }
}
