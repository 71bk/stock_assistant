package tw.bk.apppersistence.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RagVectorSchemaContractTest {

    private static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(PGVECTOR_IMAGE)
                    .withDatabaseName("invest_assistant")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void ragVectorTables_shouldExposeExpectedColumns() throws SQLException {
        try (Connection connection = openConnection()) {
            Map<String, ColumnMetadata> documentColumns =
                    loadColumns(connection, "vector", "rag_documents");
            Map<String, ColumnMetadata> chunkColumns = loadColumns(connection, "vector", "rag_chunks");

            assertTrue(
                    documentColumns.keySet().containsAll(Set.of(
                            "id",
                            "user_id",
                            "source_type",
                            "source_id",
                            "title",
                            "meta",
                            "created_at")),
                    "vector.rag_documents columns: " + documentColumns.keySet());

            assertTrue(
                    chunkColumns.keySet().containsAll(Set.of(
                            "id",
                            "document_id",
                            "user_id",
                            "chunk_index",
                            "content",
                            "embedding",
                            "meta",
                            "created_at",
                            "embedding_model",
                            "embedding_version",
                            "dimensions")),
                    "vector.rag_chunks columns: " + chunkColumns.keySet());

            assertEquals(
                    "vector",
                    chunkColumns.get("embedding").udtName(),
                    "rag_chunks.embedding must use pgvector type");
            assertEquals(
                    "vector(1536)",
                    loadFormattedType(connection, "vector", "rag_chunks", "embedding"),
                    "rag_chunks.embedding must keep the exact vector dimension");
        }
    }

    @Test
    void ragChunks_shouldKeepForeignKeyAndIndexes() throws SQLException {
        try (Connection connection = openConnection()) {
            List<String> fkDefinitions = loadForeignKeyDefinitions(connection, "vector", "rag_chunks");
            Set<String> indexNames = loadIndexNames(connection, "vector", "rag_chunks");

            assertTrue(
                    fkDefinitions.stream().anyMatch(definition ->
                            normalize(definition).contains(
                                    "foreign key (document_id) references vector.rag_documents(id) on delete cascade")),
                    "vector.rag_chunks foreign keys: " + fkDefinitions);

            assertTrue(
                    indexNames.containsAll(Set.of(
                            "idx_rag_chunks_embedding",
                            "idx_rag_chunks_user",
                            "idx_rag_chunks_embedding_model")),
                    "vector.rag_chunks indexes: " + indexNames);
        }
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Map<String, ColumnMetadata> loadColumns(
            Connection connection, String schemaName, String tableName) throws SQLException {
        Map<String, ColumnMetadata> columns = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT column_name, data_type, udt_name
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ?
                ORDER BY ordinal_position
                """)) {
            statement.setString(1, schemaName);
            statement.setString(2, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    columns.put(
                            resultSet.getString("column_name"),
                            new ColumnMetadata(
                                    resultSet.getString("data_type"),
                                    resultSet.getString("udt_name")));
                }
            }
        }
        return columns;
    }

    private static List<String> loadForeignKeyDefinitions(
            Connection connection, String schemaName, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT pg_get_constraintdef(c.oid) AS definition
                FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE c.contype = 'f' AND n.nspname = ? AND t.relname = ?
                ORDER BY c.conname
                """)) {
            statement.setString(1, schemaName);
            statement.setString(2, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> definitions = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    definitions.add(resultSet.getString("definition"));
                }
                return definitions;
            }
        }
    }

    private static Set<String> loadIndexNames(
            Connection connection, String schemaName, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = ? AND tablename = ?
                ORDER BY indexname
                """)) {
            statement.setString(1, schemaName);
            statement.setString(2, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                Set<String> indexNames = new TreeSet<>();
                while (resultSet.next()) {
                    indexNames.add(resultSet.getString("indexname"));
                }
                return indexNames;
            }
        }
    }

    private static String loadFormattedType(
            Connection connection, String schemaName, String tableName, String columnName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT format_type(a.atttypid, a.atttypmod) AS formatted_type
                FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ?
                  AND c.relname = ?
                  AND a.attname = ?
                  AND a.attnum > 0
                  AND NOT a.attisdropped
                """)) {
            statement.setString(1, schemaName);
            statement.setString(2, tableName);
            statement.setString(3, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return resultSet.getString("formatted_type");
            }
        }
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private record ColumnMetadata(String dataType, String udtName) {}
}
