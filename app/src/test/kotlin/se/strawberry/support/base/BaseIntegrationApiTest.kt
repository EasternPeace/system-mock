package se.strawberry.support.base

import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import se.strawberry.app.AppDependencies
import se.strawberry.app.buildDependencies
import se.strawberry.config.AppConfig
import se.strawberry.config.AppConfigLoader
import se.strawberry.config.EnvVar
import se.strawberry.support.clients.ProxyClient
import se.strawberry.support.clients.SessionClient
import se.strawberry.support.dynamo.DynamoManager
import se.strawberry.support.environment.EnvironmentManager
import se.strawberry.support.ports.PortManager
import se.strawberry.support.servers.ServerManager
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.util.concurrent.TimeUnit

@Testcontainers
@ExtendWith(SystemStubsExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseIntegrationApiTest {

    companion object {
        @Container
        @JvmStatic
        val localstack: LocalStackContainer = LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0")
        ).withServices(Service.DYNAMODB)
    }

    @SystemStub
    protected val env = EnvironmentVariables()

    protected lateinit var http: OkHttpClient
    protected lateinit var envManager: EnvironmentManager
    protected lateinit var servers: ServerManager
    protected lateinit var dynamoManager: DynamoManager
    protected lateinit var deps: AppDependencies

    protected lateinit var sessionClient: SessionClient
    protected lateinit var proxyClient: ProxyClient

    protected lateinit var upstreamServiceName: String

    fun dynamoClient(): DynamoDbClient = dynamoManager.getClient()

    @BeforeAll
    fun initializeSuite() {
        http = OkHttpClient.Builder()
            .callTimeout(10, TimeUnit.SECONDS)
            .build()

        envManager = EnvironmentManager(env)
        dynamoManager = DynamoManager(localstack, envManager)
        dynamoManager.initialize()
    }

    @BeforeEach
    fun setUpTest() {
        servers = ServerManager()

        envManager.loadFromDotEnvTest()
        dynamoManager.setupEnvironment()

        val upstreamPort = PortManager.findFreePort()
        envManager.setPort(EnvVar.DynAllowedPorts, upstreamPort)
        envManager.set(EnvVar.DynAllowedPorts.key, "$upstreamPort,80,443")

        servers.startUpstream(443)

        upstreamServiceName = "testInstanceOfWireMockServer"
        envManager.set(EnvVar.ServiceMap.key, "$upstreamServiceName=${servers.upstream.baseUrl()}")

        val cfg = AppConfigLoader.load()
        deps = buildDependencies(cfg)

        servers.startProxy(cfg, deps)
        servers.startKtor(cfg, deps)

        sessionClient = SessionClient(http, apiBaseUrl())
        proxyClient = ProxyClient(http, proxyBaseUrl(), upstreamServiceName)
    }

    @AfterEach
    fun tearDownTest() {
        servers.stopAll()
        dynamoManager.cleanup()
    }

    @AfterAll
    fun tearDownSuite() {
        dynamoManager.close()
    }

    protected fun proxyBaseUrl(): String = servers.proxy.baseUrl()
    protected fun upstreamBaseUrl(): String = servers.upstream.baseUrl()
    protected fun apiBaseUrl(): String = "http://localhost:${se.strawberry.config.Env.int(EnvVar.KtorApiPort)}"
}
