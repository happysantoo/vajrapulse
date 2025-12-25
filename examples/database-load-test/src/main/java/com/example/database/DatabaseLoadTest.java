package com.example.database;

import com.vajrapulse.api.task.TaskLifecycle;
import com.vajrapulse.api.task.TaskResult;
import com.vajrapulse.api.task.VirtualThreads;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Example database load test using JDBC with virtual threads.
 * 
 * <p>This task performs database operations (SELECT, INSERT, UPDATE) to test
 * database performance under load. It demonstrates:
 * <ul>
 *   <li>Using @VirtualThreads for I/O-bound database operations</li>
 *   <li>Connection pooling with HikariCP</li>
 *   <li>Proper resource management (try-with-resources)</li>
 *   <li>Database initialization and cleanup</li>
 * </ul>
 * 
 * <p>This example uses H2 in-memory database for simplicity, but can be
 * easily adapted for PostgreSQL, MySQL, or other databases.
 * 
 * @since 0.9.10
 */
@VirtualThreads
public class DatabaseLoadTest implements TaskLifecycle {
    
    private DataSource dataSource;
    private int testUserId = 1;
    
    /**
     * Default constructor for DatabaseLoadTest.
     * Initializes the task for use with VajraPulse execution engine.
     */
    public DatabaseLoadTest() {
        // Default constructor - initialization happens in init()
    }
    
    @Override
    public void init() throws Exception {
        // Initialize database connection pool
        dataSource = DatabaseConnectionFactory.createDataSource();
        
        // Create test schema and data
        initializeDatabase();
        
        System.out.println("DatabaseLoadTest init completed - database ready");
    }
    
    /**
     * Initializes the database schema and test data.
     */
    private void initializeDatabase() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            // Create users table
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INT PRIMARY KEY,
                    name VARCHAR(100),
                    email VARCHAR(100),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            
            // Insert initial test data
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO users (id, name, email) VALUES (?, ?, ?)")) {
                for (int i = 1; i <= 100; i++) {
                    stmt.setInt(1, i);
                    stmt.setString(2, "User " + i);
                    stmt.setString(3, "user" + i + "@example.com");
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
            
            conn.commit();
            System.out.println("Database initialized with 100 test users");
        }
    }
    
    @Override
    public TaskResult execute(long iteration) throws Exception {
        // Simulate a typical database workload:
        // 1. Read user by ID
        // 2. Update user email
        // 3. Query users by email pattern
        
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            
            // 1. SELECT - Read user
            User user = readUser(conn, testUserId);
            if (user == null) {
                return TaskResult.failure(new RuntimeException("User not found: " + testUserId));
            }
            
            // 2. UPDATE - Modify user
            updateUserEmail(conn, testUserId, "updated" + iteration + "@example.com");
            
            // 3. SELECT with pattern - Query users
            int count = queryUsersByPattern(conn, "user%");
            
            conn.commit();
            
            // Rotate test user ID for variety
            testUserId = (testUserId % 100) + 1;
            
            return TaskResult.success("User: " + user.name() + ", Pattern matches: " + count);
        } catch (SQLException e) {
            return TaskResult.failure(e);
        }
    }
    
    /**
     * Reads a user by ID.
     */
    private User readUser(Connection conn, int userId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
            "SELECT id, name, email FROM users WHERE id = ?")) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("email")
                    );
                }
            }
        }
        return null;
    }
    
    /**
     * Updates a user's email.
     */
    private void updateUserEmail(Connection conn, int userId, String newEmail) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
            "UPDATE users SET email = ? WHERE id = ?")) {
            stmt.setString(1, newEmail);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Queries users matching an email pattern.
     */
    private int queryUsersByPattern(Connection conn, String pattern) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
            "SELECT COUNT(*) FROM users WHERE email LIKE ?")) {
            stmt.setString(1, pattern);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
    
    @Override
    public void teardown() throws Exception {
        // Clean up test data (optional)
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("DROP TABLE IF EXISTS users");
            System.out.println("DatabaseLoadTest teardown completed - tables dropped");
        }
        
        // Close connection pool
        if (dataSource instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }
    
    /**
     * User record for test data.
     */
    private record User(int id, String name, String email) {}
}
