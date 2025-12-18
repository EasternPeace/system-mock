package se.strawberry.support.servers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import se.strawberry.app.AppDependencies
import se.strawberry.app.KtorBootstrap
import se.strawberry.app.ServerBootstrap
import se.strawberry.config.AppConfig

class ServerManager {
    lateinit var upstream: WireMockServer
    lateinit var proxy: WireMockServer
    lateinit var ktorApp: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>

    fun startUpstream(port: Int) {
        upstream = WireMockServer(
            options()
                .port(port)
                .notifier(Slf4jNotifier(false))
                .disableRequestJournal()
        )
        upstream.start()
    }

    fun startProxy(cfg: AppConfig, deps: AppDependencies) {
        val appScope = CoroutineScope(Dispatchers.Default)
        deps.trafficPersister.start(appScope)
        proxy = ServerBootstrap.start(cfg, deps)
    }

    fun startKtor(cfg: AppConfig, deps: AppDependencies) {
        ktorApp = KtorBootstrap.start(cfg, deps)
    }

    fun stopAll() {
        tryStop { proxy.stop() }
        tryStop { upstream.stop() }
        tryStop { ktorApp.stop(1000, 2000) }
    }

    private fun tryStop(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
        }
    }
}
