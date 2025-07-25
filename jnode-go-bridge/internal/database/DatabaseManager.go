package database

import (
	"database/sql"
	"fmt"
	"strings"
	
	"jnode-go-bridge/internal/config"
)

func ConnectToJnodeDatabase(config *config.JnodeConfig) (*sql.DB, error) {
	driver, dsn := parseJnodeDatabaseURL(config.DatabaseURL, config.DatabaseUser, config.DatabasePassword)
	
	db, err := sql.Open(driver, dsn)
	if err != nil {
		return nil, fmt.Errorf("failed to open database: %w", err)
	}
	
	if err := db.Ping(); err != nil {
		return nil, fmt.Errorf("failed to ping database: %w", err)
	}
	
	return db, nil
}

func parseJnodeDatabaseURL(jdbcURL, username, password string) (driver, dsn string) {
	// Parse jNode's JDBC URL format to Go database/sql format
	
	if strings.HasPrefix(jdbcURL, "jdbc:postgresql:") {
		// PostgreSQL: jdbc:postgresql://host:port/database
		url := strings.TrimPrefix(jdbcURL, "jdbc:postgresql:")
		if !strings.HasPrefix(url, "//") {
			// Local format: jdbc:postgresql:database
			dsn = fmt.Sprintf("user=%s password=%s dbname=%s sslmode=disable",
				username, password, url)
		} else {
			// Remote format: jdbc:postgresql://host:port/database
			dsn = fmt.Sprintf("%s?user=%s&password=%s&sslmode=disable",
				strings.TrimPrefix(url, "//"), username, password)
		}
		return "postgres", dsn
		
	} else if strings.HasPrefix(jdbcURL, "jdbc:mysql:") {
		// MySQL: jdbc:mysql://host:port/database
		url := strings.TrimPrefix(jdbcURL, "jdbc:mysql:")
		dsn = fmt.Sprintf("%s:%s@tcp%s", username, password, url)
		return "mysql", dsn
		
	} else if strings.HasPrefix(jdbcURL, "jdbc:h2:") {
		// H2: jdbc:h2:./db/jnode or jdbc:h2:mem:test
		url := strings.TrimPrefix(jdbcURL, "jdbc:h2:")
		if strings.HasPrefix(url, "mem:") {
			dsn = ":memory:"
		} else {
			// Convert H2 file path to SQLite format
			dsn = strings.TrimPrefix(url, "./") + ".db"
		}
		return "sqlite3", dsn
		
	} else {
		// Default to SQLite
		return "sqlite3", "jnode.db"
	}
}