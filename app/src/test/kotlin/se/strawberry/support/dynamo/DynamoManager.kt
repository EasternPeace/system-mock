package se.strawberry.support.dynamo

import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import se.strawberry.repository.RepositoryConstants.DYNAMO.SESSION_TABLE_NAME
import se.strawberry.repository.RepositoryConstants.DYNAMO.TRAFFIC_TABLE_NAME
import se.strawberry.support.environment.EnvironmentManager
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

class DynamoManager(private val localstack: LocalStackContainer, private val envManager: EnvironmentManager) {

    private lateinit var dynamoClient: DynamoDbClient

    fun initialize() {
        dynamoClient = DynamoDbClient.builder()
            .endpointOverride(localstack.getEndpointOverride(Service.DYNAMODB))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        localstack.accessKey,
                        localstack.secretKey
                    )
                )
            )
            .region(Region.of(localstack.region))
            .build()

        createTables()
    }

    fun setupEnvironment() {
        envManager.set("DYNAMODB_ENDPOINT", localstack.getEndpointOverride(Service.DYNAMODB).toString())
        envManager.set("AWS_REGION", localstack.region)
        envManager.set("DYNAMODB_SESSIONS_TABLE", SESSION_TABLE_NAME)
        envManager.set("AWS_ACCESS_KEY_ID", localstack.accessKey)
        envManager.set("AWS_SECRET_ACCESS_KEY", localstack.secretKey)
    }

    fun cleanup() {
        try {
            cleanTables()
        } catch (_: Throwable) {
        }
    }

    fun close() {
        try {
            dynamoClient.close()
        } catch (_: Throwable) {
        }
    }

    fun getClient(): DynamoDbClient = dynamoClient

    private fun createTables() {
        createSessionsTable()
        createTrafficTable()
    }

    private fun createSessionsTable() {
        try {
            dynamoClient.createTable(
                CreateTableRequest.builder()
                    .tableName(SESSION_TABLE_NAME)
                    .attributeDefinitions(
                        AttributeDefinition.builder()
                            .attributeName("sessionId")
                            .attributeType(ScalarAttributeType.S)
                            .build()
                    )
                    .keySchema(
                        KeySchemaElement.builder()
                            .attributeName("sessionId")
                            .keyType(KeyType.HASH)
                            .build()
                    )
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build()
            )
        } catch (_: ResourceInUseException) {
        }
    }

    private fun createTrafficTable() {
        try {
            dynamoClient.createTable(
                CreateTableRequest.builder()
                    .tableName(TRAFFIC_TABLE_NAME)
                    .attributeDefinitions(
                        AttributeDefinition.builder()
                            .attributeName("sessionId")
                            .attributeType(ScalarAttributeType.S)
                            .build()
                    )
                    .keySchema(
                        KeySchemaElement.builder()
                            .attributeName("sessionId")
                            .keyType(KeyType.HASH)
                            .build()
                    )
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build()
            )
        } catch (_: ResourceInUseException) {
        }
    }

    private fun cleanTables() {
        cleanTable(SESSION_TABLE_NAME)
        cleanTable(TRAFFIC_TABLE_NAME)
    }

    private fun cleanTable(tableName: String) {
        val items = dynamoClient.scan(
            ScanRequest.builder()
                .tableName(tableName)
                .build()
        ).items()

        items.forEach { item ->
            dynamoClient.deleteItem(
                DeleteItemRequest.builder()
                    .tableName(tableName)
                    .key(mapOf("sessionId" to item["sessionId"]))
                    .build()
            )
        }
    }
}
