package dao;

import model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.HexFormat;

public class UserDao {

    public void createTable() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            boolean hasUsers = false;
            boolean hasUserId = false;

            try (Statement statement = connection.createStatement();
                 ResultSet tables = statement.executeQuery(
                         "SELECT name FROM sqlite_master WHERE type='table' AND name='users'")) {
                hasUsers = tables.next();
            }

            if (hasUsers) {
                try (Statement statement = connection.createStatement();
                     ResultSet columns = statement.executeQuery("PRAGMA table_info(users)")) {
                    while (columns.next()) {
                        if ("user_id".equalsIgnoreCase(columns.getString("name"))) {
                            hasUserId = true;
                            break;
                        }
                    }
                }
            }

            // The uploaded project used an older TEACHER/STUDENT schema.
            // Rename that table so the system can use the pet grooming schema.
            if (hasUsers && !hasUserId) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE users RENAME TO users_legacy");
                }
            }

            String sql = """
                    CREATE TABLE IF NOT EXISTS users (
                        user_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL UNIQUE,
                        password_hash TEXT NOT NULL,
                        full_name TEXT NOT NULL,
                        role TEXT NOT NULL CHECK (role IN
                            ('ADMINISTRATOR', 'RECEPTIONIST', 'GROOMER', 'CUSTOMER')),
                        email TEXT,
                        account_status TEXT NOT NULL DEFAULT 'PENDING'
                            CHECK (account_status IN
                            ('PENDING', 'APPROVED', 'REJECTED', 'INACTIVE')),
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """;

            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    public boolean register(
            String username,
            String password,
            String fullName,
            String email
    ) throws SQLException {
        String sql = """
                INSERT INTO users
                    (username, password_hash, full_name, role, email, account_status)
                VALUES (?, ?, ?, 'CUSTOMER', ?, 'PENDING')
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, hashPassword(password));
            statement.setString(3, fullName);
            statement.setString(4, email == null || email.isBlank() ? null : email);
            statement.executeUpdate();
            return true;
        } catch (SQLException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().toUpperCase().contains("UNIQUE")) {
                return false;
            }
            throw exception;
        }
    }

    public User login(String username, String password) throws SQLException {
        String sql = """
                SELECT user_id, username, full_name, role, account_status
                FROM users
                WHERE username = ? AND password_hash = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, hashPassword(password));

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return new User(
                            result.getInt("user_id"),
                            result.getString("username"),
                            result.getString("full_name"),
                            result.getString("role"),
                            result.getString("account_status")
                    );
                }
            }
        }
        return null;
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash password.", exception);
        }
    }
}
