package tr.girgin.backend.trading.analysis.platform.library.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/** Plain JDBC for the catalog checks: the catalog has no entities. */
final class CatalogQueries {

    private CatalogQueries() {}

    /**
     * Every row of the query as strings, with {@code parameters} bound to its placeholders.
     *
     * @throws SQLException when the query fails
     */
    static List<String[]> query(Connection connection, String sql, int columns, String... parameters)
            throws SQLException {
        List<String[]> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setString(i + 1, parameters[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String[] row = new String[columns];
                    for (int i = 0; i < columns; i++) {
                        row[i] = resultSet.getString(i + 1);
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    static void execute(DataSource dataSource, String sql) {
        try (Connection connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot run " + sql, e);
        }
    }
}
