package main

import (
	"context"
	"database/sql"
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"
	
	"jnode-go-bridge/internal/binkp"
	"jnode-go-bridge/internal/bridge"
	"jnode-go-bridge/internal/config"
	"jnode-go-bridge/internal/database"
	
	_ "github.com/lib/pq"
	_ "github.com/go-sql-driver/mysql"
	_ "github.com/mattn/go-sqlite3"
)

func main() {
	var configPath = flag.String("config", "jnode.conf", "Path to jNode configuration file")
	var serverMode = flag.Bool("server", false, "Run as BinkP server")
	var clientMode = flag.Bool("client", false, "Connect to remote system")
	var remoteAddr = flag.String("remote", "", "Remote system address (for client mode)")
	var debug = flag.Bool("debug", false, "Enable debug logging")
	var testDB = flag.Bool("test-db", false, "Test database connection and exit")
	var testQueue = flag.Bool("test-queue", false, "Test queue operations and exit")
	var testConfig = flag.Bool("test-config", false, "Test configuration parsing and exit")
	flag.Parse()
	
	// Load jNode configuration
	jnodeConfig, err := config.LoadJnodeConfig(*configPath)
	if err != nil {
		log.Fatalf("Failed to load configuration: %v", err)
	}
	
	if *debug {
		log.SetFlags(log.LstdFlags | log.Lshortfile)
		log.Printf("Loaded configuration from %s", *configPath)
		log.Printf("Primary address: %s", jnodeConfig.PrimaryAddress)
		log.Printf("Database URL: %s", jnodeConfig.DatabaseURL)
	}
	
	// Test configuration and exit if requested
	if *testConfig {
		fmt.Printf("Configuration test successful:\n")
		fmt.Printf("  Primary Address: %s\n", jnodeConfig.PrimaryAddress)
		fmt.Printf("  Station Name: %s\n", jnodeConfig.StationName)
		fmt.Printf("  Sysop Name: %s\n", jnodeConfig.SysopName)
		fmt.Printf("  Database URL: %s\n", jnodeConfig.DatabaseURL)
		fmt.Printf("  Inbound Dir: %s\n", jnodeConfig.InboundDir)
		fmt.Printf("  Outbound Dir: %s\n", jnodeConfig.OutboundDir)
		fmt.Printf("  BinkP Server: %t\n", jnodeConfig.ServerEnabled)
		fmt.Printf("  BinkP Port: %d\n", jnodeConfig.BindPort)
		return
	}
	
	// Connect to jNode database
	db, err := database.ConnectToJnodeDatabase(jnodeConfig)
	if err != nil {
		log.Fatalf("Failed to connect to database: %v", err)
	}
	defer db.Close()
	
	if *debug {
		log.Printf("Connected to database successfully")
	}
	
	// Test database connection and exit if requested
	if *testDB {
		fmt.Printf("Database connection test successful\n")
		
		// Test basic queries
		if err := testDatabaseQueries(db); err != nil {
			log.Fatalf("Database query test failed: %v", err)
		}
		
		fmt.Printf("Database queries test successful\n")
		return
	}
	
	// Create bridge components
	queueManager := bridge.NewJnodeQueueManager(db, jnodeConfig)
	configManager := bridge.NewJnodeConfigManager(jnodeConfig, db)
	
	// Use configManager to avoid unused variable error
	_ = configManager
	
	// Test queue operations and exit if requested
	if *testQueue {
		fmt.Printf("Queue operations test:\n")
		
		// Test getting outbound files (this will likely return empty results)
		files, err := queueManager.GetOutboundFiles("1:2/3.4")
		if err != nil {
			log.Printf("Warning: Failed to get outbound files: %v", err)
		} else {
			fmt.Printf("  Found %d outbound files for test address 1:2/3.4\n", len(files))
		}
		
		fmt.Printf("Queue operations test completed\n")
		return
	}
	
	if *serverMode {
		log.Printf("Starting BinkP server on %s:%d", jnodeConfig.BindAddress, jnodeConfig.BindPort)
		
		// Create BinkP server
		server := binkp.NewBinkPServer(jnodeConfig, queueManager, configManager)
		
		// Create context for graceful shutdown
		ctx, cancel := context.WithCancel(context.Background())
		defer cancel()
		
		// Handle shutdown signals
		sigChan := make(chan os.Signal, 1)
		signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
		
		go func() {
			<-sigChan
			log.Println("Shutdown signal received, stopping server...")
			cancel()
		}()
		
		// Start server
		if err := server.ListenAndServe(ctx); err != nil {
			log.Fatalf("Server error: %v", err)
		}
		
		log.Println("Server stopped")
		
	} else if *clientMode {
		if *remoteAddr == "" {
			log.Fatal("Remote address required for client mode")
		}
		
		log.Printf("Connecting to %s", *remoteAddr)
		
		// Create BinkP client
		client := binkp.NewBinkPClient(jnodeConfig, queueManager, configManager)
		
		// Connect to remote system
		if err := client.Connect(*remoteAddr); err != nil {
			log.Fatalf("Failed to connect: %v", err)
		}
		defer client.Disconnect()
		
		// Run session
		sessionStart := time.Now()
		if err := client.RunSession(); err != nil {
			log.Fatalf("Session failed: %v", err)
		}
		sessionDuration := time.Since(sessionStart)
		
		// Get statistics
		bytesRx, bytesTx, filesRx, filesTx := client.GetStatistics()
		
		log.Printf("Session completed in %v", sessionDuration)
		log.Printf("Statistics: RX %d bytes/%d files, TX %d bytes/%d files", 
			bytesRx, filesRx, bytesTx, filesTx)
		
	} else {
		fmt.Println("jNode BinkP Bridge")
		fmt.Println("Usage:")
		fmt.Println("  Server mode: jnode-binkp -server -config /path/to/jnode.conf")
		fmt.Println("  Client mode: jnode-binkp -client -remote 1:2/3.4 -config /path/to/jnode.conf")
		fmt.Println("  Test mode:")
		fmt.Println("    jnode-binkp -test-config -config /path/to/jnode.conf")
		fmt.Println("    jnode-binkp -test-db -config /path/to/jnode.conf")
		fmt.Println("    jnode-binkp -test-queue -config /path/to/jnode.conf")
		os.Exit(1)
	}
}

func testDatabaseQueries(db *sql.DB) error {
	// Test links table
	var linkCount int
	err := db.QueryRow("SELECT COUNT(*) FROM links").Scan(&linkCount)
	if err != nil {
		return fmt.Errorf("failed to query links table: %w", err)
	}
	fmt.Printf("  Links table: %d records\n", linkCount)
	
	// Test echomail table
	var echomailCount int
	err = db.QueryRow("SELECT COUNT(*) FROM echomail").Scan(&echomailCount)
	if err != nil {
		return fmt.Errorf("failed to query echomail table: %w", err)
	}
	fmt.Printf("  Echomail table: %d records\n", echomailCount)
	
	// Test netmail table
	var netmailCount int
	err = db.QueryRow("SELECT COUNT(*) FROM netmail").Scan(&netmailCount)
	if err != nil {
		return fmt.Errorf("failed to query netmail table: %w", err)
	}
	fmt.Printf("  Netmail table: %d records\n", netmailCount)
	
	return nil
}