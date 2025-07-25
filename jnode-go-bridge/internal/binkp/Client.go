package binkp

import (
	"fmt"
	"log"
	"net"
	"strings"
	"time"
	
	"jnode-go-bridge/internal/bridge"
	"jnode-go-bridge/internal/config"
)

// BinkPClient implements a BinkP protocol client
type BinkPClient struct {
	config        *config.JnodeConfig
	queueManager  *bridge.JnodeQueueManager
	configManager *bridge.JnodeConfigManager
	
	// Connection settings
	remoteAddr   string
	remoteHost   string
	remotePort   int
	
	// Session state
	session      *BinkPSession
	connected    bool
	authenticated bool
	
	// File transfer state
	outboundFiles []bridge.FileEntry
	currentFile   *bridge.FileEntry
	fileOffset    int64
}

// NewBinkPClient creates a new BinkP client instance
func NewBinkPClient(config *config.JnodeConfig, queueManager *bridge.JnodeQueueManager, configManager *bridge.JnodeConfigManager) *BinkPClient {
	return &BinkPClient{
		config:        config,
		queueManager:  queueManager,
		configManager: configManager,
	}
}

// Connect connects to a remote BinkP system
func (c *BinkPClient) Connect(remoteAddr string) error {
	c.remoteAddr = remoteAddr
	
	// Parse remote address
	host, port, err := c.parseAddress(remoteAddr)
	if err != nil {
		return fmt.Errorf("invalid remote address %s: %w", remoteAddr, err)
	}
	
	c.remoteHost = host
	c.remotePort = port
	
	// Create connection
	conn, err := net.DialTimeout("tcp", fmt.Sprintf("%s:%d", host, port), 
		time.Duration(c.config.ConnTimeout)*time.Second)
	if err != nil {
		return fmt.Errorf("failed to connect to %s:%d: %w", host, port, err)
	}
	
	log.Printf("BinkP client: Connected to %s:%d", host, port)
	
	// Create session
	c.session = NewBinkPSession(conn)
	c.connected = true
	
	// Set system information
	c.session.SetSystemInfo(
		c.configManager.GetSystemName(),
		c.configManager.GetSysopName(),
		c.configManager.GetLocation(),
	)
	
	// Set local address
	c.session.localAddr = c.configManager.GetPrimaryAddress()
	
	// Set session callbacks
	c.session.SetCallbacks(
		c.onFileReceived,
		c.onFileToSend,
		nil, // Client doesn't handle authentication requests
	)
	
	return nil
}

// Disconnect disconnects from the remote system
func (c *BinkPClient) Disconnect() error {
	if c.session != nil {
		err := c.session.Close()
		c.session = nil
		c.connected = false
		c.authenticated = false
		return err
	}
	return nil
}

// RunSession runs a complete BinkP client session
func (c *BinkPClient) RunSession() error {
	if !c.connected {
		return fmt.Errorf("not connected")
	}
	
	// Load outbound files
	if err := c.loadOutboundFiles(); err != nil {
		return fmt.Errorf("failed to load outbound files: %w", err)
	}
	
	log.Printf("BinkP client: Starting session with %d outbound files", len(c.outboundFiles))
	
	// Send handshake
	if err := c.session.SendHandshake(); err != nil {
		return fmt.Errorf("handshake failed: %w", err)
	}
	
	// Send authentication if required
	if err := c.authenticate(); err != nil {
		return fmt.Errorf("authentication failed: %w", err)
	}
	
	// Main session loop
	for {
		// Set read timeout
		c.session.conn.SetReadDeadline(time.Now().Add(time.Duration(c.config.Timeout) * time.Second))
		
		// Read frame
		frame, err := c.session.ReadFrame()
		if err != nil {
			return fmt.Errorf("failed to read frame: %w", err)
		}
		
		// Process frame
		if err := c.session.ProcessFrame(frame); err != nil {
			log.Printf("BinkP client: Frame processing error: %v", err)
			return err
		}
		
		// Handle specific commands
		if frame.Command == FRAME_COMMAND && len(frame.Data) > 0 {
			cmd := frame.Data[0]
			
			switch cmd {
			case M_OK:
				c.authenticated = true
				log.Printf("BinkP client: Authentication successful")
				
				// Start sending files
				if err := c.startFileTransfers(); err != nil {
					return fmt.Errorf("failed to start file transfers: %w", err)
				}
				
			case M_EOB:
				log.Printf("BinkP client: End of batch received")
				// Send our EOB and finish
				c.session.WriteCommand(M_EOB, []byte(""))
				return nil
				
			case M_ERR:
				errMsg := string(frame.Data[1:])
				return fmt.Errorf("remote error: %s", errMsg)
				
			case M_BSY:
				return fmt.Errorf("remote system busy")
			}
		}
	}
}

// parseAddress parses a remote address into host and port
func (c *BinkPClient) parseAddress(addr string) (host string, port int, err error) {
	// Handle IPv6 addresses in brackets
	if strings.HasPrefix(addr, "[") {
		// IPv6 format: [::1]:24554 or [::1]
		if strings.Contains(addr, "]:") {
			parts := strings.Split(addr, "]:")
			if len(parts) != 2 {
				return "", 0, fmt.Errorf("invalid IPv6 address format")
			}
			host = strings.TrimPrefix(parts[0], "[")
			port = DEFAULT_PORT
			if parts[1] != "" {
				if p, err := parsePort(parts[1]); err == nil {
					port = p
				}
			}
		} else {
			// [::1] format
			host = strings.Trim(addr, "[]")
			port = DEFAULT_PORT
		}
		return host, port, nil
	}
	
	// Handle FTN address format: 1:2/3.4@domain
	if strings.Contains(addr, "@") {
		parts := strings.Split(addr, "@")
		if len(parts) == 2 {
			host = parts[1]
			port = DEFAULT_PORT
			return host, port, nil
		}
	}
	
	// Handle host:port format
	if strings.Contains(addr, ":") {
		parts := strings.Split(addr, ":")
		if len(parts) == 2 {
			host = parts[0]
			if p, err := parsePort(parts[1]); err == nil {
				port = p
			} else {
				return "", 0, fmt.Errorf("invalid port: %s", parts[1])
			}
			return host, port, nil
		}
	}
	
	// Just hostname
	return addr, DEFAULT_PORT, nil
}

// parsePort parses a port string
func parsePort(portStr string) (int, error) {
	port := 0
	for _, r := range portStr {
		if r < '0' || r > '9' {
			return 0, fmt.Errorf("invalid port")
		}
		port = port*10 + int(r-'0')
	}
	if port <= 0 || port > 65535 {
		return 0, fmt.Errorf("port out of range")
	}
	return port, nil
}

// loadOutboundFiles loads files to be sent
func (c *BinkPClient) loadOutboundFiles() error {
	files, err := c.queueManager.GetOutboundFiles(c.remoteAddr)
	if err != nil {
		return err
	}
	
	c.outboundFiles = files
	return nil
}

// authenticate performs BinkP authentication
func (c *BinkPClient) authenticate() error {
	// Get password for remote system
	password, err := c.configManager.GetPassword(c.remoteAddr)
	if err != nil {
		log.Printf("BinkP client: No password configured for %s", c.remoteAddr)
		password = ""
	}
	
	if password != "" {
		// Send password
		if err := c.session.WriteCommand(M_PWD, []byte(password)); err != nil {
			return fmt.Errorf("failed to send password: %w", err)
		}
	}
	
	return nil
}

// startFileTransfers initiates file transfers
func (c *BinkPClient) startFileTransfers() error {
	if len(c.outboundFiles) == 0 {
		// No files to send, send EOB
		return c.session.WriteCommand(M_EOB, []byte(""))
	}
	
	// Send first file
	return c.sendNextFile()
}

// sendNextFile sends the next file in the queue
func (c *BinkPClient) sendNextFile() error {
	if len(c.outboundFiles) == 0 {
		// No more files, send EOB
		return c.session.WriteCommand(M_EOB, []byte(""))
	}
	
	// Get next file
	c.currentFile = &c.outboundFiles[0]
	c.outboundFiles = c.outboundFiles[1:]
	c.fileOffset = 0
	
	// Send file header
	fileInfo := fmt.Sprintf("%s %d %d", c.currentFile.Name, c.currentFile.Size, time.Now().Unix())
	if err := c.session.WriteCommand(M_FILE, []byte(fileInfo)); err != nil {
		return fmt.Errorf("failed to send file header: %w", err)
	}
	
	log.Printf("BinkP client: Sending file: %s (%d bytes)", c.currentFile.Name, c.currentFile.Size)
	
	// Send file data
	return c.sendFileData()
}

// sendFileData sends the current file's data
func (c *BinkPClient) sendFileData() error {
	if c.currentFile == nil {
		return fmt.Errorf("no current file")
	}
	
	// Send file data in chunks
	chunkSize := c.config.FrameSize
	if chunkSize <= 0 || chunkSize > 32767 {
		chunkSize = 32767
	}
	
	data := c.currentFile.Data
	for c.fileOffset < int64(len(data)) {
		// Calculate chunk size
		remaining := int64(len(data)) - c.fileOffset
		currentChunkSize := int64(chunkSize)
		if currentChunkSize > remaining {
			currentChunkSize = remaining
		}
		
		// Send chunk
		chunk := data[c.fileOffset : c.fileOffset+currentChunkSize]
		if err := c.session.WriteData(chunk); err != nil {
			return fmt.Errorf("failed to send file data: %w", err)
		}
		
		c.fileOffset += currentChunkSize
		c.session.bytesSent += currentChunkSize
	}
	
	log.Printf("BinkP client: File sent: %s", c.currentFile.Name)
	
	// Mark file as sent
	if err := c.queueManager.MarkSent(*c.currentFile); err != nil {
		log.Printf("BinkP client: Warning: failed to mark file as sent: %v", err)
	}
	
	c.session.filesSent++
	c.currentFile = nil
	
	// Send next file or EOB
	return c.sendNextFile()
}

// onFileReceived handles received files
func (c *BinkPClient) onFileReceived(filename string, data []byte) error {
	log.Printf("BinkP client: Received file: %s (%d bytes)", filename, len(data))
	
	// Create file entry for queue manager
	fileEntry := bridge.FileEntry{
		Name: filename,
		Type: "inbound",
		Size: int64(len(data)),
		Data: data,
	}
	
	// Save to inbound directory
	return c.queueManager.SaveInbound(fileEntry)
}

// onFileToSend provides files to send (not used in client mode)
func (c *BinkPClient) onFileToSend() (filename string, data []byte, hasMore bool) {
	return "", nil, false
}

// GetStatistics returns client statistics
func (c *BinkPClient) GetStatistics() (bytesRx, bytesTx int64, filesRx, filesTx int) {
	if c.session != nil {
		return c.session.GetStatistics()
	}
	return 0, 0, 0, 0
}