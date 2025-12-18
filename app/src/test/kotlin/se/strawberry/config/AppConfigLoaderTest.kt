package se.strawberry.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.net.URI

@ExtendWith(SystemStubsExtension::class)
class AppConfigLoaderTest {

    @SystemStub
    lateinit var envVars: EnvironmentVariables

    @Test
    fun `load should strip trailing slashes from SERVICE_MAP urls`() {
        // Given
        envVars.set("WIREMOCK_SERVER_PORT", "10001")
        envVars.set("KTOR_API_PORT", "10002")
        envVars.set("DYN_ALLOWED_PORTS", "80")
        // Trailing slash here
        envVars.set("SERVICE_MAP", "my-service=http://example.com/api/")
        envVars.set("DYNAMO_ENDPOINT", "http://dynamo:8000")
        envVars.set("AWS_REGION", "eu-north-1")
        envVars.set("AWS_ACCESS_KEY_ID", "test")
        envVars.set("AWS_SECRET_ACCESS_KEY", "test")

        // When
        val config = AppConfigLoader.load()

        // Then
        // The URI should not have a trailing slash
        val expectedUri = URI("http://example.com/api")
        assertEquals(expectedUri, config.services["my-service"])
    }
    
    @Test
    fun `load should handle URLs without trailing slashes correctly`() {
        // Given
        envVars.set("WIREMOCK_SERVER_PORT", "10003")
        envVars.set("KTOR_API_PORT", "10004")
        envVars.set("DYN_ALLOWED_PORTS", "80")
        // No trailing slash
        envVars.set("SERVICE_MAP", "other-service=http://example.org/v1")
        envVars.set("DYNAMO_ENDPOINT", "http://dynamo:8000")
        envVars.set("AWS_REGION", "eu-north-1")
        envVars.set("AWS_ACCESS_KEY_ID", "test")
        envVars.set("AWS_SECRET_ACCESS_KEY", "test")

        // When
        val config = AppConfigLoader.load()

        // Then
        val expectedUri = URI("http://example.org/v1")
        assertEquals(expectedUri, config.services["other-service"])
    }
}
