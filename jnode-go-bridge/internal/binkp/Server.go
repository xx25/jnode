package binkp

import (
	"context"
	"fmt"
	"log"
	"net"
	"sync"
	"time"
	
	"jnode-go-bridge/internal/bridge"
	"jnode-go-bridge/internal/config"
)

// BinkPServer implements a BinkP protocol server
type BinkPServer struct {
	config        *config.JnodeConfig
	queueManager  *bridge.JnodeQueueManager  
	configManager *bridge.JnodeConfigManager
	
	listener  net.Listener
	running   bool
	mutex     sync.RWMutex
	waitGroup sync.WaitGroup
	
	// Statistics
	sessionsTotal   int64
	sessionsActive  int64
	bytesReceived   int64
	bytesSent       int64
	filesReceived   int64
	filesSent       int64
}

// NewBinkPServer creates a new BinkP server instance
func NewBinkPServer(config *config.JnodeConfig, queueManager *bridge.JnodeQueueManager, configManager *bridge.JnodeConfigManager) *BinkPServer {
	return &BinkPServer{
		config:        config,
		queueManager:  queueManager,
		configManager: configManager,
	}
}

// Start starts the BinkP server
func (s *BinkPServer) Start() error {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	
	if s.running {
		return fmt.Errorf("server already running")
	}
	
	// Determine bind address
	bindAddr := s.getBindAddress()
	
	// Create listener
	listener, err := net.Listen("tcp", bindAddr)
	if err != nil {
		return fmt.Errorf("failed to create listener on %s: %w", bindAddr, err)
	}
	
	s.listener = listener
	s.running = true
	
	log.Printf("BinkP server listening on %s", bindAddr)
	
	// Start accepting connections
	s.waitGroup.Add(1)
	go s.acceptConnections()
	
	return nil
}

// Stop stops the BinkP server
func (s *BinkPServer) Stop() error {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	
	if !s.running {
		return nil
	}
	
	s.running = false
	
	// Close listener
	if s.listener != nil {
		s.listener.Close()
	}
	
	// Wait for all goroutines to finish
	s.waitGroup.Wait()
	
	log.Printf("BinkP server stopped")
	return nil
}

// IsRunning returns true if the server is running
func (s *BinkPServer) IsRunning() bool {
	s.mutex.RLock()
	defer s.mutex.RUnlock()
	return s.running
}

// getBindAddress constructs the bind address from configuration
func (s *BinkPServer) getBindAddress() string {
	if s.config.IPv6Enable && s.config.Bind6Address != "" {
		return fmt.Sprintf("[%s]:%d", s.config.Bind6Address, s.config.BindPort)
	}
	return fmt.Sprintf("%s:%d", s.config.BindAddress, s.config.BindPort)
}

// acceptConnections accepts incoming connections
func (s *BinkPServer) acceptConnections() {
	defer s.waitGroup.Done()
	
	for {
		// Check if we should stop
		s.mutex.RLock()
		running := s.running
		s.mutex.RUnlock()
		
		if !running {
			break
		}
		
		// Set accept deadline to allow periodic checks
		if tcpListener, ok := s.listener.(*net.TCPListener); ok {
			tcpListener.SetDeadline(time.Now().Add(1 * time.Second))
		}
		
		// Accept connection
		conn, err := s.listener.Accept()
		if err != nil {
			if netErr, ok := err.(net.Error); ok && netErr.Timeout() {
				continue // Timeout, check if we should stop
			}
			
			s.mutex.RLock()
			running := s.running
			s.mutex.RUnlock()
			
			if running {
				log.Printf("Error accepting connection: %v", err)
			}
			continue
		}
		
		// Handle connection in goroutine
		s.waitGroup.Add(1)
		go s.handleConnection(conn)
	}
}

// handleConnection handles a single BinkP connection
func (s *BinkPServer) handleConnection(conn net.Conn) {
	defer s.waitGroup.Done()
	defer conn.Close()
	
	// Update statistics
	s.mutex.Lock()
	s.sessionsTotal++
	s.sessionsActive++
	sessionID := s.sessionsTotal
	s.mutex.Unlock()
	
	defer func() {
		s.mutex.Lock()
		s.sessionsActive--
		s.mutex.Unlock()
	}()
	
	remoteAddr := conn.RemoteAddr().String()
	log.Printf("BinkP session %d: New connection from %s", sessionID, remoteAddr)
	
	// Create BinkP session
	session := NewBinkPSession(conn)
	
	// Set system information
	session.SetSystemInfo(
		s.configManager.GetSystemName(),
		s.configManager.GetSysopName(),
		s.configManager.GetLocation(),
	)
	
	// Set local address
	session.localAddr = s.configManager.GetPrimaryAddress()
	
	// Set session callbacks
	session.SetCallbacks(
		s.onFileReceived,
		s.onFileToSend,
		s.onAuthenticate,
	)
	
	// Set connection timeout
	conn.SetDeadline(time.Now().Add(time.Duration(s.config.Timeout) * time.Second))
	
	// Run BinkP session
	if err := s.runSession(session, sessionID); err != nil {
		log.Printf("BinkP session %d error: %v", sessionID, err)
	}
	
	// Update statistics
	rxBytes, txBytes, rxFiles, txFiles := session.GetStatistics()
	s.mutex.Lock()
	s.bytesReceived += rxBytes
	s.bytesSent += txBytes
	s.filesReceived += int64(rxFiles)
	s.filesSent += int64(txFiles)
	s.mutex.Unlock()
	
	log.Printf("BinkP session %d: Completed. RX: %d bytes/%d files, TX: %d bytes/%d files",
		sessionID, rxBytes, rxFiles, txBytes, txFiles)
}

// runSession runs a complete BinkP session
func (s *BinkPServer) runSession(session *BinkPSession, sessionID int64) error {
	// Send handshake
	if err := session.SendHandshake(); err != nil {
		return fmt.Errorf("handshake failed: %w", err)
	}
	
	// Session loop
	for {
		// Set read timeout
		session.conn.SetReadDeadline(time.Now().Add(time.Duration(s.config.Timeout) * time.Second))
		
		// Read frame
		frame, err := session.ReadFrame()
		if err != nil {
			return fmt.Errorf("failed to read frame: %w", err)
		}
		
		// Process frame
		if err := session.ProcessFrame(frame); err != nil {
			log.Printf("BinkP session %d: Frame processing error: %v", sessionID, err)
			
			// Send error response
			session.WriteCommand(M_ERR, []byte(err.Error()))
			return err
		}
		
		// Check for session end conditions
		if frame.Command == FRAME_COMMAND && len(frame.Data) > 0 {
			cmd := frame.Data[0]
			if cmd == M_EOB {
				// End of batch - session complete
				log.Printf("BinkP session %d: End of batch received", sessionID)
				break
			}
		}
	}
	
	return nil
}

// onFileReceived handles received files
func (s *BinkPServer) onFileReceived(filename string, data []byte) error {
	log.Printf("BinkP: Received file: %s (%d bytes)", filename, len(data))
	
	// Create file entry for queue manager
	fileEntry := bridge.FileEntry{
		Name: filename,
		Type: "inbound",
		Size: int64(len(data)),
		Data: data,
	}
	
	// Save to inbound directory
	return s.queueManager.SaveInbound(fileEntry)
}

// onFileToSend provides files to send
func (s *BinkPServer) onFileToSend() (filename string, data []byte, hasMore bool) {
	// This would typically get files from the outbound queue
	// For now, return no files (EOB)
	return "", nil, false
}

// onAuthenticate handles authentication
func (s *BinkPServer) onAuthenticate(addr, password string) bool {
	log.Printf("BinkP: Authentication request from %s", addr)
	
	// Get password from configuration
	expectedPassword, err := s.configManager.GetPassword(addr)
	if err != nil {
		log.Printf("BinkP: Failed to get password for %s: %v", addr, err)
		return false
	}
	
	// Handle no password case
	if expectedPassword == "" {
		return true
	}
	
	// Handle CRAM-MD5 authentication
	if len(password) > 10 && password[:8] == "CRAM-MD5" {
		// Extract challenge and response from password
		// This is a simplified implementation
		return password == expectedPassword
	}
	
	// Plain password check
	return password == expectedPassword
}

// GetStatistics returns server statistics
func (s *BinkPServer) GetStatistics() (sessionsTotal, sessionsActive, bytesRx, bytesTx, filesRx, filesTx int64) {
	s.mutex.RLock()
	defer s.mutex.RUnlock()
	
	return s.sessionsTotal, s.sessionsActive, s.bytesReceived, s.bytesSent, s.filesReceived, s.filesSent
}

// ListenAndServe starts the server and blocks until stopped
func (s *BinkPServer) ListenAndServe(ctx context.Context) error {
	if err := s.Start(); err != nil {
		return err
	}
	
	// Wait for context cancellation
	<-ctx.Done()
	
	return s.Stop()
}