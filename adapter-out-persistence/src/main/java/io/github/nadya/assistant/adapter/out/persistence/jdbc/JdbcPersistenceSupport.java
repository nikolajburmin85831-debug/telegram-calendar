package io.github.nadya.assistant.adapter.out.persistence.jdbc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

final class JdbcPersistenceSupport {

    private final DataSource dataSource;
    private final String schema;

    JdbcPersistenceSupport(DataSource dataSource, String schema) {
        this.dataSource = dataSource;
        this.schema = normalizeSchema(schema);
    }

    Connection openConnection() throws SQLException {
        return dataSource.getConnection();
    }

    String schema() {
        return schema;
    }

    String table(String tableName) {
        return schema + "." + tableName;
    }

    IllegalStateException operationFailed(String action, SQLException exception) {
        return new IllegalStateException(action + " failed", exception);
    }

    boolean isDuplicateKey(SQLException exception) {
        if (exception instanceof SQLIntegrityConstraintViolationException) {
            return true;
        }
        return "23505".equals(exception.getSQLState());
    }

    private String normalizeSchema(String candidate) {
        String normalized = candidate == null || candidate.isBlank() ? "public" : candidate.trim();
        if (!normalized.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("schema must contain only letters, digits, and underscores");
        }
        return normalized;
    }
}
