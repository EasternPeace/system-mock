package se.strawberry.support.base

import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import se.strawberry.app.AppDependencies
import se.strawberry.app.buildDependencies
import se.strawberry.config.AppConfig
import se.strawberry.config.AppConfigLoader
import se.strawberry.config.EnvVar
import se.strawberry.support.clients.ProxyClient
import se.strawberry.support.clients.SessionClient
import se.strawberry.support.environment.EnvironmentManager
import se.strawberry.support.fakes.FakeSessionRepository
import se.strawberry.support.fakes.FakeStubRepository
import se.strawberry.support.ports.PortManager
import se.strawberry.support.servers.ServerManager
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.util.concurrent.TimeUnit

@ExtendWith(SystemStubsExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseApiTest {

    @SystemStub
    protected val env = EnvironmentVariables()

    protected lateinit var http: OkHttpClient
    protected lateinit var envManager: EnvironmentManager
    protected lateinit var servers: ServerManager
    protected lateinit var deps: AppDependencies

    protected lateinit var sessionClient: SessionClient
    protected lateinit var proxyClient: ProxyClient

    protected lateinit var upstreamServiceName: String

    @BeforeAll
    fun initializeHttpClient() {
        http = OkHttpClient.Builder()
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @BeforeEach
    fun setUpTest() {
        envManager = EnvironmentManager(env)
        servers = ServerManager()

        envManager.loadFromDotEnvTest()

        val ports = PortManager.findFreePorts(3)
        val wmPort = ports[0]
        val apiPort = ports[1]
        val upstreamPort = ports[2]

        envManager.setPort(EnvVar.WireMockServerPort, wmPort)
        envManager.setPort(EnvVar.KtorApiPort, apiPort)
        envManager.set(EnvVar.DynAllowedPorts.key, "$upstreamPort,80,443")

        servers.startUpstream(upstreamPort)

        upstreamServiceName = "testInstanceOfWireMockServer"
        envManager.set(EnvVar.ServiceMap.key, "$upstreamServiceName=${servers.upstream.baseUrl()}")

        val cfg = AppConfigLoader.load()
        deps = buildDependencies(cfg)

        val fakeStubRepo = FakeStubRepository()
        val fakeSessionRepo = FakeSessionRepository()

        val testStubService = se.strawberry.service.stub.StubServiceImpl(
            se.strawberry.common.Json.mapper,
            deps.wireMockClient,
            fakeStubRepo
        )

        deps = deps.copy(
            stubService = testStubService,
            sessionRepository = fakeSessionRepo
        )

        servers.startProxy(cfg, deps)
        servers.startKtor(cfg, deps)

        sessionClient = SessionClient(http, apiBaseUrl())
        proxyClient = ProxyClient(http, proxyBaseUrl(), upstreamServiceName)
    }

    @AfterEach
    fun tearDownTest() {
        servers.stopAll()
    }

    protected fun proxyBaseUrl(): String = servers.proxy.baseUrl()
    protected fun upstreamBaseUrl(): String = servers.upstream.baseUrl()
    protected fun apiBaseUrl(): String = "http://localhost:${se.strawberry.config.Env.int(EnvVar.KtorApiPort)}"

    protected fun createSessionInRepository(id: String) {
        val session = se.strawberry.repository.session.SessionRepository.Session(
            id = id,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 3600_000,
            status = se.strawberry.repository.session.SessionRepository.Session.Status.ACTIVE
        )
        deps.sessionRepository.create(session)
    }

    protected fun call(sessionId: String?, path: String) = proxyClient.callEndpoint(path, sessionId)
}
