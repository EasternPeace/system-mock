package tests.integration

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
import se.strawberry.repository.RepositoryConstants.DYNAMO.SESSION_TABLE_NAME
import se.strawberry.repository.session.SessionRepository
import se.strawberry.support.base.BaseIntegrationApiTest
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

class TestApiSessionCrudOperations : BaseIntegrationApiTest() {

    @Test
    fun `should create session with valid ID and ACTIVE status`() {
        val session = sessionClient.create()

        assertThat(session.id, not(emptyString()))
        assertThat(session.status, equalTo(SessionRepository.Session.Status.ACTIVE.name))
        assertThat(session.createdAt, greaterThan(0L))

        val dbItem = dynamoClient().getItem { builder ->
            builder.tableName(SESSION_TABLE_NAME).key(
                mapOf("sessionId" to AttributeValue.builder()
                    .s(session.id)
                    .build()
                )
            )
        }

        assertThat(dbItem.hasItem(), equalTo(true))
        assertThat(dbItem.item()["sessionId"]?.s(), equalTo(session.id))
        assertThat(dbItem.item()["status"]?.s(), equalTo(SessionRepository.Session.Status.ACTIVE.name))
    }

    @Test
    fun `should persist session to DynamoDB`() {
        val sessionId = sessionClient.create().id

        val session = sessionClient.getBody(sessionId)

        assertThat(session.id, equalTo(sessionId))
        assertThat(session.status, equalTo(SessionRepository.Session.Status.ACTIVE.name))
    }

    @Test
    fun `should create multiple sessions with unique IDs`() {
        val sessionIds = mutableSetOf<String>()
        val iterations = 5

        repeat(iterations) {
            sessionIds.add(sessionClient.create().id)
        }

        assertThat(sessionIds.size, equalTo(iterations))

        sessionIds.forEach { sessionId ->
            val response = sessionClient.get(sessionId)
            assertThat(response.code, equalTo(200))
            response.close()
        }
    }

    @Test
    fun `should return 404 for non-existent session`() {
        val response = sessionClient.get("non-existent-session-id")
        assertThat(response.code, equalTo(404))
        response.close()
    }

    @Test
    fun `should return 204 for closing session`() {
        val sessionId = sessionClient.create().id

        val response = sessionClient.close(sessionId)
        assertThat(response.code, equalTo(204))
        response.close()
    }
}

