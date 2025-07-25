package config

import (
	"bufio"
	"fmt"
	"os"
	"strconv"
	"strings"
)

type JnodeConfig struct {
	// Database configuration
	DatabaseURL      string
	DatabaseUser     string
	DatabasePassword string
	
	// FTN configuration
	PrimaryAddress string
	StationName    string
	SysopName      string
	Location       string
	
	// Directory configuration
	InboundDir  string
	OutboundDir string
	TempDir     string
	
	// BinkP configuration
	ServerEnabled bool
	ClientEnabled bool
	BindAddress   string
	Bind6Address  string
	BindPort      int
	IPv6Enable    bool
	Timeout       int
	ConnTimeout   int
	MaxMemory     int
	FrameSize     int
	Threads       int
	
	// Logging configuration
	LogLevel int
	LogFile  string
}

func LoadJnodeConfig(configPath string) (*JnodeConfig, error) {
	file, err := os.Open(configPath)
	if err != nil {
		return nil, fmt.Errorf("failed to open config file: %w", err)
	}
	defer file.Close()
	
	config := &JnodeConfig{
		// Set defaults matching jNode defaults
		ServerEnabled: true,
		ClientEnabled: true,
		BindAddress:   "0.0.0.0",
		Bind6Address:  "::",
		BindPort:      24554,
		IPv6Enable:    false,
		Timeout:       30,
		ConnTimeout:   10,
		MaxMemory:     10485760,
		FrameSize:     32767,	
		Threads:       10,
		LogLevel:      4,
	}
	
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		
		// Skip comments and empty lines
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		
		// Parse key=value pairs
		parts := strings.SplitN(line, "=", 2)
		if len(parts) != 2 {
			continue
		}
		
		key := strings.TrimSpace(parts[0])
		value := strings.TrimSpace(parts[1])
		
		// Map jNode configuration keys to struct fields
		switch key {
		case "jdbc.url":
			config.DatabaseURL = value
		case "jdbc.user":
			config.DatabaseUser = value
		case "jdbc.pass":
			config.DatabasePassword = value
		case "ftn.primary":
			config.PrimaryAddress = value
		case "station.name":
			config.StationName = value
		case "sysop.name":
			config.SysopName = value
		case "ftn.location":
			config.Location = value
		case "ftn.inbound":
			config.InboundDir = value
		case "ftn.outbound":
			config.OutboundDir = value
		case "ftn.temp":
			config.TempDir = value
		case "binkp.server":
			config.ServerEnabled = parseBool(value)
		case "binkp.client":
			config.ClientEnabled = parseBool(value)
		case "binkp.bind":
			config.BindAddress = value
		case "binkp.bind6":
			config.Bind6Address = value
		case "binkp.port":
			config.BindPort = parseInt(value, 24554)
		case "binkp.ipv6.enable":
			config.IPv6Enable = parseBool(value)
		case "binkp.timeout":
			config.Timeout = parseInt(value, 30)
		case "binkp.connect.timeout":
			config.ConnTimeout = parseInt(value, 10)
		case "binkp.maxmem":
			config.MaxMemory = parseInt(value, 10485760)
		case "binkp.size":
			config.FrameSize = parseInt(value, 32767)
		case "binkp.threads":
			config.Threads = parseInt(value, 10)
		case "log.level":
			config.LogLevel = parseInt(value, 4)
		case "log.file":
			config.LogFile = value
		}
	}
	
	return config, scanner.Err()
}

func parseBool(value string) bool {
	return strings.ToLower(value) == "true"
}

func parseInt(value string, defaultValue int) int {
	if i, err := strconv.Atoi(value); err == nil {
		return i
	}
	return defaultValue
}