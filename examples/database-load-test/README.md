# Database Load Test Example

This example demonstrates how to perform load testing on databases using VajraPulse with JDBC and connection pooling.

## Features

- **Virtual Threads**: Uses `@VirtualThreads` for efficient I/O-bound database operations
- **Connection Pooling**: HikariCP for optimal connection management
- **Multiple Databases**: Supports H2 (in-memory) and PostgreSQL
- **Realistic Workload**: Simulates SELECT, UPDATE, and pattern queries

## Quick Start

### Using H2 (In-Memory) - No Setup Required

```bash
./gradlew :examples:database-load-test:run
```

This uses H2 in-memory database - no installation or configuration needed.

### Using PostgreSQL

1. **Start PostgreSQL** (using Docker):
```bash
docker run -d \
  --name postgres-test \
  -e POSTGRES_PASSWORD=password \
  -e POSTGRES_DB=testdb \
  -p 5432:5432 \
  postgres:16
```

2. **Set Environment Variables**:
```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/testdb"
export DATABASE_USER="postgres"
export DATABASE_PASSWORD="password"
```

3. **Run the Test**:
```bash
./gradlew :examples:database-load-test:run
```

## Custom Configuration

### Command-Line Arguments

```bash
# Run at 100 TPS for 60 seconds
./gradlew :examples:database-load-test:run --args "100"
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DATABASE_URL` | JDBC connection URL | `jdbc:h2:mem:testdb;...` |
| `DATABASE_USER` | Database username | `sa` |
| `DATABASE_PASSWORD` | Database password | (empty) |

### System Properties

Alternatively, use system properties:
```bash
./gradlew :examples:database-load-test:run \
  -Ddatabase.url="jdbc:postgresql://localhost:5432/testdb" \
  -Ddatabase.user="postgres" \
  -Ddatabase.password="password"
```

## What It Tests

The example performs a realistic database workload:

1. **SELECT**: Reads a user by ID
2. **UPDATE**: Updates user email
3. **SELECT with Pattern**: Queries users matching an email pattern

Each iteration:
- Uses a connection from the pool
- Executes multiple SQL operations
- Commits the transaction
- Rotates through test user IDs

## Connection Pool Configuration

The example uses HikariCP with settings optimized for virtual threads:

- **Maximum Pool Size**: 20 connections
- **Minimum Idle**: 5 connections
- **Connection Timeout**: 10 seconds
- **Leak Detection**: Enabled (5 second threshold)

## Expected Output

```
Starting database load test:
  TPS: 50.0
  Duration: PT30S
  Database: H2 (in-memory)

DatabaseLoadTest init completed - database ready
Database initialized with 100 test users
Starting load test runId=... pattern=StaticLoad duration=PT30S
...
=== Final Results ===
[Console metrics output]
```

## Performance Tips

1. **Virtual Threads**: The `@VirtualThreads` annotation ensures efficient I/O handling
2. **Connection Pooling**: HikariCP manages connections efficiently
3. **Batch Operations**: The initialization uses batch inserts for efficiency
4. **Transaction Management**: Each iteration uses explicit transactions

## Troubleshooting

### Connection Pool Exhausted

If you see "Connection pool exhausted" errors:
- Increase `maximumPoolSize` in `DatabaseConnectionFactory`
- Reduce TPS to match database capacity
- Check database connection limits

### PostgreSQL Connection Issues

```bash
# Verify PostgreSQL is running
docker ps | grep postgres

# Check connection
psql -h localhost -U postgres -d testdb
```

### H2 Database Issues

H2 is in-memory and resets on each run. This is expected behavior for testing.

## Extending the Example

### Add More Operations

```java
// Add DELETE operation
private void deleteUser(Connection conn, int userId) throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement(
        "DELETE FROM users WHERE id = ?")) {
        stmt.setInt(1, userId);
        stmt.executeUpdate();
    }
}
```

### Use Different Database

Modify `DatabaseConnectionFactory.createDataSource()` to support:
- MySQL: `jdbc:mysql://localhost:3306/testdb`
- MariaDB: `jdbc:mariadb://localhost:3306/testdb`
- SQL Server: `jdbc:sqlserver://localhost:1433;databaseName=testdb`

## See Also

- [HTTP Load Test Example](../http-load-test/README.md)
- [VajraPulse Main Documentation](../../README.md)
- [HikariCP Documentation](https://github.com/brettwooldridge/HikariCP)
