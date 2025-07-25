package config

import (
	"os"
	"testing"
)

func TestLoadJnodeConfig(t *testing.T) {
	// Create a temporary config file
	configContent := `# Test jNode configuration
jdbc.url=jdbc:h2:./test/db
jdbc.user=testuser
jdbc.pass=testpass
ftn.primary=1:2/3.4
station.name=Test Station
sysop.name=Test Sysop
ftn.location=Test Location
ftn.inbound=./test/inbound
ftn.outbound=./test/outbound
ftn.temp=./test/temp
binkp.server=true
binkp.client=false
binkp.bind=127.0.0.1
binkp.bind6=::1
binkp.port=24555
binkp.ipv6.enable=true
binkp.timeout=60
binkp.connect.timeout=20
binkp.maxmem=20971520
binkp.size=65535
binkp.threads=5
log.level=3
log.file=./test/jnode.log
`
	
	// Write to temporary file
	tmpfile, err := os.CreateTemp("", "jnode_test_*.conf")
	if err != nil {
		t.Fatal(err)
	}
	defer os.Remove(tmpfile.Name())
	
	if _, err := tmpfile.Write([]byte(configContent)); err != nil {
		t.Fatal(err)
	}
	if err := tmpfile.Close(); err != nil {
		t.Fatal(err)
	}
	
	// Test loading configuration
	config, err := LoadJnodeConfig(tmpfile.Name())
	if err != nil {
		t.Errorf("LoadJnodeConfig failed: %v", err)
	}
	
	// Test parsed values
	if config.DatabaseURL != "jdbc:h2:./test/db" {
		t.Errorf("Expected DatabaseURL 'jdbc:h2:./test/db', got '%s'", config.DatabaseURL)
	}
	
	if config.DatabaseUser != "testuser" {
		t.Errorf("Expected DatabaseUser 'testuser', got '%s'", config.DatabaseUser)
	}
	
	if config.DatabasePassword != "testpass" {
		t.Errorf("Expected DatabasePassword 'testpass', got '%s'", config.DatabasePassword)
	}
	
	if config.PrimaryAddress != "1:2/3.4" {
		t.Errorf("Expected PrimaryAddress '1:2/3.4', got '%s'", config.PrimaryAddress)
	}
	
	if config.StationName != "Test Station" {
		t.Errorf("Expected StationName 'Test Station', got '%s'", config.StationName)
	}
	
	if config.SysopName != "Test Sysop" {
		t.Errorf("Expected SysopName 'Test Sysop', got '%s'", config.SysopName)
	}
	
	if config.Location != "Test Location" {
		t.Errorf("Expected Location 'Test Location', got '%s'", config.Location)
	}
	
	if config.InboundDir != "./test/inbound" {
		t.Errorf("Expected InboundDir './test/inbound', got '%s'", config.InboundDir)
	}
	
	if config.OutboundDir != "./test/outbound" {
		t.Errorf("Expected OutboundDir './test/outbound', got '%s'", config.OutboundDir)
	}
	
	if config.TempDir != "./test/temp" {
		t.Errorf("Expected TempDir './test/temp', got '%s'", config.TempDir)
	}
	
	if !config.ServerEnabled {
		t.Errorf("Expected ServerEnabled true, got %t", config.ServerEnabled)
	}
	
	if config.ClientEnabled {
		t.Errorf("Expected ClientEnabled false, got %t", config.ClientEnabled)
	}
	
	if config.BindAddress != "127.0.0.1" {
		t.Errorf("Expected BindAddress '127.0.0.1', got '%s'", config.BindAddress)
	}
	
	if config.Bind6Address != "::1" {
		t.Errorf("Expected Bind6Address '::1', got '%s'", config.Bind6Address)
	}
	
	if config.BindPort != 24555 {
		t.Errorf("Expected BindPort 24555, got %d", config.BindPort)
	}
	
	if !config.IPv6Enable {
		t.Errorf("Expected IPv6Enable true, got %t", config.IPv6Enable)
	}
	
	if config.Timeout != 60 {
		t.Errorf("Expected Timeout 60, got %d", config.Timeout)
	}
	
	if config.ConnTimeout != 20 {
		t.Errorf("Expected ConnTimeout 20, got %d", config.ConnTimeout)
	}
	
	if config.MaxMemory != 20971520 {
		t.Errorf("Expected MaxMemory 20971520, got %d", config.MaxMemory)
	}
	
	if config.FrameSize != 65535 {
		t.Errorf("Expected FrameSize 65535, got %d", config.FrameSize)
	}
	
	if config.Threads != 5 {
		t.Errorf("Expected Threads 5, got %d", config.Threads)
	}
	
	if config.LogLevel != 3 {
		t.Errorf("Expected LogLevel 3, got %d", config.LogLevel)
	}
	
	if config.LogFile != "./test/jnode.log" {
		t.Errorf("Expected LogFile './test/jnode.log', got '%s'", config.LogFile)
	}
}

func TestLoadJnodeConfigDefaults(t *testing.T) {
	// Create a minimal config file
	configContent := `# Minimal config
jdbc.url=jdbc:h2:./minimal/db
ftn.primary=1:2/3.0
`
	
	// Write to temporary file
	tmpfile, err := os.CreateTemp("", "jnode_minimal_*.conf")
	if err != nil {
		t.Fatal(err)
	}
	defer os.Remove(tmpfile.Name())
	
	if _, err := tmpfile.Write([]byte(configContent)); err != nil {
		t.Fatal(err)
	}
	if err := tmpfile.Close(); err != nil {
		t.Fatal(err)
	}
	
	// Test loading configuration
	config, err := LoadJnodeConfig(tmpfile.Name())
	if err != nil {
		t.Errorf("LoadJnodeConfig failed: %v", err)
	}
	
	// Test default values
	if !config.ServerEnabled {
		t.Errorf("Expected default ServerEnabled true, got %t", config.ServerEnabled)
	}
	
	if !config.ClientEnabled {
		t.Errorf("Expected default ClientEnabled true, got %t", config.ClientEnabled)
	}
	
	if config.BindAddress != "0.0.0.0" {
		t.Errorf("Expected default BindAddress '0.0.0.0', got '%s'", config.BindAddress)
	}
	
	if config.Bind6Address != "::" {
		t.Errorf("Expected default Bind6Address '::', got '%s'", config.Bind6Address)
	}
	
	if config.BindPort != 24554 {
		t.Errorf("Expected default BindPort 24554, got %d", config.BindPort)
	}
	
	if config.IPv6Enable {
		t.Errorf("Expected default IPv6Enable false, got %t", config.IPv6Enable)
	}
	
	if config.LogLevel != 4 {
		t.Errorf("Expected default LogLevel 4, got %d", config.LogLevel)
	}
}

func TestLoadJnodeConfigNonexistentFile(t *testing.T) {
	_, err := LoadJnodeConfig("/nonexistent/path/config.conf")
	if err == nil {
		t.Errorf("Expected error for nonexistent file, got nil")
	}
}