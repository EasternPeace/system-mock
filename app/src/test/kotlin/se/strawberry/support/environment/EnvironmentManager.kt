package se.strawberry.support.environment

import io.github.cdimascio.dotenv.dotenv
import se.strawberry.config.EnvVar
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables

class EnvironmentManager(private val env: EnvironmentVariables) {

    fun loadFromDotEnvTest() {
        val dotenv = dotenv {
            filename = ".env.test"
            ignoreIfMalformed = true
            ignoreIfMissing = false
        }
        dotenv.entries().forEach { entry ->
            env.set(entry.key, entry.value)
        }
    }

    fun setPort(envVar: EnvVar, port: Int) {
        env.set(envVar.key, port.toString())
    }

    fun set(envVar: EnvVar, value: String) {
        env.set(envVar.key, value)
    }

    fun set(key: String, value: String) {
        env.set(key, value)
    }
}
