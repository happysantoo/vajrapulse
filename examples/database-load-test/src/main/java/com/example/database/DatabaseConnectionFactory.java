package com.example.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.util.concurrent.TimeUnit;

/**
 * Factory for creating database connection pools.
 * 
 * <p>This factory supports both H2 (in-memory) and PostgreSQL databases.
 * Configure via environment variables or system properties.
 * 
 * @since 0.9.10
 */
public final class DatabaseConnectionFactory {
    
    /**
     * Environment variable for database URL.
     */
    private static final String DB_URL_ENV = "DATABASE_URL";
    
    /**
     * Environment variable for database user.
     */
    private static final String DB_USER_ENV = "DATABASE_USER";
    
    /**
     * Environment variable for database password.
     */
    private static final String DB_PASSWORD_ENV = "DATABASE_PASSWORD";
    
    /**
     * Default H2 in-memory database URL.
     */
    private static final String DEFAULT_H2_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
    
    /**
     * Creates a data source with appropriate configuration.
     * 
     * <p>Configuration priority:
     * <ol>
     *   <li>Environment variables (DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD)</li>
     *   <li>System properties</li>
     *   <li>Default H2 in-memory database</li>
     * </ol>
     * 
     * @return configured data source
     */
    public static DataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        
        // Get configuration from environment or use defaults
        String dbUrl = getEnvOrProperty(DB_URL_ENV, "database.url", DEFAULT_H2_URL);
        String dbUser = getEnvOrProperty(DB_USER_ENV, "database.user", "sa");
        String dbPassword = getEnvOrProperty(DB_PASSWORD_ENV, "database.password", "");
        
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUser);
        config.setPassword(dbPassword);
        
        // Connection pool settings optimized for virtual threads
        config.setMaximumPoolSize(20);  // Virtual threads allow higher concurrency
        config.setMinimumIdle(5);
        config.setConnectionTimeout(TimeUnit.SECONDS.toMillis(10));
        config.setIdleTimeout(TimeUnit.MINUTES.toMillis(10));
        config.setMaxLifetime(TimeUnit.MINUTES.toMillis(30));
        config.setLeakDetectionThreshold(TimeUnit.SECONDS.toMillis(5));
        
        // Connection test query
        if (dbUrl.contains("h2")) {
            config.setConnectionTestQuery("SELECT 1");
        } else if (dbUrl.contains("postgresql")) {
            config.setConnectionTestQuery("SELECT 1");
        }
        
        // Pool name for monitoring
        config.setPoolName("VajraPulse-DB-Pool");
        
        return new HikariDataSource(config);
    }
    
    /**
     * Gets value from environment variable or system property, with fallback.
     */
    private static String getEnvOrProperty(String envKey, String propKey, String defaultValue) {
        String value = System.getenv(envKey);
        if (value == null || value.isBlank()) {
            value = System.getProperty(propKey);
        }
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}
