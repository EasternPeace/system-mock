package se.strawberry.support.fixtures

import se.strawberry.api.models.stub.CreateStubRequest
import se.strawberry.api.models.stub.Ephemeral
import se.strawberry.api.models.stub.ReqMatch
import se.strawberry.api.models.stub.ReqMatchMethods
import se.strawberry.api.models.stub.RespDef
import se.strawberry.api.models.stub.RespMode
import se.strawberry.api.models.stub.UrlMatch
import se.strawberry.api.models.stub.UrlMatchType

object StubFixtures {

    fun createBasicStubRequest(
        path: String = "/api/test",
        status: Int = 200,
        bodyText: String = "ok",
        contentType: String = "text/plain; charset=utf-8",
        ephemeral: Ephemeral? = null
    ): CreateStubRequest {
        return CreateStubRequest(
            request = ReqMatch(
                method = ReqMatchMethods.GET,
                url = UrlMatch(UrlMatchType.EXACT, path),
                headers = emptyMap(),
                body = null
            ),
            response = RespDef(
                mode = RespMode.STATIC,
                status = status,
                headers = mapOf("Content-Type" to contentType),
                bodyText = bodyText,
                bodyJson = null,
                patch = null
            ),
            ephemeral = ephemeral
        )
    }

    fun createEphemeralStubRequest(
        path: String = "/api/test",
        status: Int = 200,
        bodyText: String = "ok",
        uses: Int = 1,
        ttlMs: Long? = null
    ): CreateStubRequest {
        return createBasicStubRequest(
            path = path,
            status = status,
            bodyText = bodyText,
            ephemeral = Ephemeral(uses = uses, ttlMs = ttlMs)
        )
    }

    fun createJsonStubRequest(
        path: String = "/api/test",
        status: Int = 200,
        bodyJson: String = """{"status":"ok"}""",
        ephemeral: Ephemeral? = null
    ): CreateStubRequest {
        return CreateStubRequest(
            request = ReqMatch(
                method = ReqMatchMethods.GET,
                url = UrlMatch(UrlMatchType.EXACT, path),
                headers = emptyMap(),
                body = null
            ),
            response = RespDef(
                mode = RespMode.STATIC,
                status = status,
                headers = mapOf("Content-Type" to "application/json"),
                bodyText = null,
                bodyJson = bodyJson,
                patch = null
            ),
            ephemeral = ephemeral
        )
    }
}
