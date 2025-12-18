package tests.unit

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
import se.strawberry.common.Headers
import se.strawberry.common.Json
import se.strawberry.service.stub.StubServiceImpl
import se.strawberry.support.fakes.FakeStubRepository
import se.strawberry.support.fakes.FakeWireMockClient
import se.strawberry.support.fixtures.StubFixtures
import se.strawberry.wiremock.StubBuilder

class StubServiceImplTest {

    private val mapper = Json.mapper

    @Test
    fun `create should add session-scoped stub and return created payload`() {
        val client = FakeWireMockClient()
        val repo = FakeStubRepository()
        val service = StubServiceImpl(mapper, client, repo)

        val sessionId = "s-123"
        val dto = StubFixtures.createEphemeralStubRequest(uses = 2, ttlMs = 10_000)

        val resp = service.create(dto, sessionId)

        assertThat(resp.status, equalTo(201))
        assertThat(resp.headers.getHeader(Headers.CONTENT_TYPE).firstValue(), containsString("application/json"))

        assertThat(client.stubs.size, equalTo(1))
        val added = client.stubs.single()

        val mappingJson = mapper.writeValueAsString(added)
        assertThat(mappingJson, containsString(Headers.X_MOCK_SESSION_ID))
        assertThat(mappingJson, containsString(sessionId))

        assertThat(repo.stubs.size, equalTo(1))
        assertThat(repo.stubs[0].sessionId, equalTo(sessionId))

        val payload = resp.bodyAsString
        assertThat(payload, containsString("\"id\""))
        assertThat(payload, containsString("\"summary\""))
        assertThat(payload, containsString("\"usesLeft\":2"))
        assertThat(payload, containsString("\"expiresAt\""))
    }

    @Test
    fun `list should return all stubs as json`() {
        val client = FakeWireMockClient()
        val repo = FakeStubRepository()
        val service = StubServiceImpl(mapper, client, repo)

        client.addStub(StubBuilder.buildStubMapping(StubFixtures.createBasicStubRequest(path = "/a")))
        client.addStub(StubBuilder.buildStubMapping(StubFixtures.createBasicStubRequest(path = "/b")))

        val resp = service.list()

        assertThat(resp.status, equalTo(200))
        val body = resp.bodyAsString
        assertThat(body, startsWith("["))
        assertThat(body, containsString("/a"))
        assertThat(body, containsString("/b"))
    }

    @Test
    fun `delete should remove existing stub and return 204`() {
        val client = FakeWireMockClient()
        val repo = FakeStubRepository()
        val service = StubServiceImpl(mapper, client, repo)

        val dto = StubFixtures.createBasicStubRequest(path = "/x")
        val sessionId = "s-del"
        val createResp = service.create(dto, sessionId)

        val jsonParams = mapper.readTree(createResp.bodyAsString)
        val stubId = jsonParams.get("id").asText()

        val resp = service.delete(stubId)

        assertThat(resp.status, equalTo(204))
        assertThat(client.stubs, empty())
        assertThat(repo.stubs, empty())
    }

    @Test
    fun `delete should return 404 when stub not found`() {
        val client = FakeWireMockClient()
        val repo = FakeStubRepository()
        val service = StubServiceImpl(mapper, client, repo)

        val resp = service.delete("does-not-exist")

        assertThat(resp.status, equalTo(404))
        assertThat(resp.bodyAsString, containsString("not_found"))
    }

    @Test
    fun `delete should find stub in DB (GSI) if missing from memory and delete it`() {
        val client = FakeWireMockClient()
        val repo = FakeStubRepository()
        val service = StubServiceImpl(mapper, client, repo)

        val sessionId = "s-ghost"
        val stubId = "stub-ghost-1"
        repo.save(
            se.strawberry.repository.stub.StubRepository.Stub(
                sessionId = sessionId,
                stubId = stubId,
                mappingJson = "{}",
                createdAt = 0,
                updatedAt = 0,
                expiresAt = null,
                usesLeft = null,
                status = se.strawberry.repository.stub.StubRepository.Stub.Status.ACTIVE
            )
        )

        val resp = service.delete(stubId)

        assertThat(resp.status, equalTo(204))
        assertThat(repo.stubs, empty())
    }
}
