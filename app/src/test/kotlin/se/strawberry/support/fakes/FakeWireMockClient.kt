package se.strawberry.support.fakes

import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import se.strawberry.service.wiremock.WireMockClient

class FakeWireMockClient : WireMockClient {
    val stubs = mutableListOf<StubMapping>()

    override fun addStub(stub: StubMapping) {
        stubs.add(stub)
    }

    override fun removeStub(stub: StubMapping) {
        stubs.removeIf { it.id == stub.id }
    }

    override fun listStubs(): List<StubMapping> = stubs.toList()

    override fun resetRequests() {
        // not needed for stub service tests
    }

    override fun listServeEvents(): List<ServeEvent> = emptyList()
    override fun findServeEvent(id: String): ServeEvent? = null
}
