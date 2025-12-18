package se.strawberry.support.clients

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import se.strawberry.api.Endpoints
import se.strawberry.common.Headers
import se.strawberry.common.Json
import se.strawberry.api.models.stub.CreateStubRequest

class ProxyClient(
    private val client: OkHttpClient,
    private val proxyBaseUrl: String,
    private val upstreamServiceName: String
) {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun createStub(stub: CreateStubRequest, sessionId: String? = null): Response {
        val body = Json.mapper.writeValueAsString(stub).toRequestBody(JSON)

        val req = Request.Builder()
            .url("$proxyBaseUrl${Endpoints.Paths.STUBS}")
            .addHeader("Content-Type", "application/json")
            .addHeader(Headers.X_MOCK_TARGET_SERVICE, upstreamServiceName)
            .apply { if (sessionId != null) addHeader(Headers.X_MOCK_SESSION_ID, sessionId) }
            .post(body)
            .build()

        return client.newCall(req).execute()
    }

    fun callEndpoint(path: String, sessionId: String? = null): Response {
        val req = Request.Builder()
            .url("$proxyBaseUrl$path")
            .addHeader(Headers.X_MOCK_TARGET_SERVICE, upstreamServiceName)
            .apply { if (sessionId != null) addHeader(Headers.X_MOCK_SESSION_ID, sessionId) }
            .build()

        return client.newCall(req).execute()
    }
}
