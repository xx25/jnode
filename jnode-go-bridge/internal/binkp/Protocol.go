package binkp

import (
	"bufio"
	"bytes"
	"encoding/binary"
	"fmt"
	"io"
	"log"
	"net"
	"strconv"
	"strings"
	"time"
)

// BinkP protocol constants
const (
	// BinkP command IDs
	M_NUL  = 0 // NUL - various info
	M_ADR  = 1 // ADR - address info
	M_PWD  = 2 // PWD - password
	M_FILE = 3 // FILE - file info
	M_OK   = 4 // OK - password OK
	M_EOB  = 5 // EOB - end of batch
	M_GOT  = 6 // GOT - file received OK
	M_ERR  = 7 // ERR - error
	M_BSY  = 8 // BSY - busy, try later
	M_GET  = 9 // GET - get file from offset
	M_SKIP = 10 // SKIP - skip file

	// Frame types
	FRAME_COMMAND = 0x80
	FRAME_DATA    = 0x00

	// Default BinkP port
	DEFAULT_PORT = 24554

	// Protocol timeouts
	CONNECT_TIMEOUT = 30 * time.Second
	READ_TIMEOUT    = 60 * time.Second
	WRITE_TIMEOUT   = 60 * time.Second
)

// BinkPFrame represents a BinkP protocol frame
type BinkPFrame struct {
	Length  uint16
	Command uint8
	Data    []byte
}

// BinkPSession represents a BinkP protocol session
type BinkPSession struct {
	conn     net.Conn
	reader   *bufio.Reader
	writer   *bufio.Writer
	
	// Session state
	authenticated bool
	localAddr     string
	remoteAddr    string
	sessionKey    string
	
	// Configuration
	systemName string
	sysopName  string
	location   string
	
	// Statistics
	bytesReceived int64
	bytesSent     int64
	filesReceived int
	filesSent     int
	
	// Callbacks
	onFileReceived func(filename string, data []byte) error
	onFileToSend   func() (filename string, data []byte, hasMore bool)
	onAuthenticate func(addr, password string) bool
}

// NewBinkPSession creates a new BinkP session
func NewBinkPSession(conn net.Conn) *BinkPSession {
	return &BinkPSession{
		conn:   conn,
		reader: bufio.NewReader(conn),
		writer: bufio.NewWriter(conn),
	}
}

// SetSystemInfo sets system identification information
func (s *BinkPSession) SetSystemInfo(name, sysop, location string) {
	s.systemName = name
	s.sysopName = sysop
	s.location = location
}

// SetCallbacks sets session callback functions
func (s *BinkPSession) SetCallbacks(
	onFileReceived func(string, []byte) error,
	onFileToSend func() (string, []byte, bool),
	onAuth func(string, string) bool,
) {
	s.onFileReceived = onFileReceived
	s.onFileToSend = onFileToSend
	s.onAuthenticate = onAuth
}

// ReadFrame reads a BinkP frame from the connection
func (s *BinkPSession) ReadFrame() (*BinkPFrame, error) {
	// Set read timeout
	s.conn.SetReadDeadline(time.Now().Add(READ_TIMEOUT))
	
	// Read frame header (2 bytes length + command/data flag)
	header := make([]byte, 2)
	if _, err := io.ReadFull(s.reader, header); err != nil {
		return nil, fmt.Errorf("failed to read frame header: %w", err)
	}
	
	// Parse length (little endian, with command bit)
	rawLength := binary.LittleEndian.Uint16(header)
	isCommand := (rawLength & 0x8000) != 0
	length := rawLength & 0x7FFF
	
	frame := &BinkPFrame{
		Length: length,
	}
	
	if isCommand {
		frame.Command = FRAME_COMMAND
	} else {
		frame.Command = FRAME_DATA
	}
	
	// Read frame data
	if length > 0 {
		frame.Data = make([]byte, length)
		if _, err := io.ReadFull(s.reader, frame.Data); err != nil {
			return nil, fmt.Errorf("failed to read frame data: %w", err)
		}
	}
	
	log.Printf("BinkP RX: cmd=%d, len=%d, data=%q", frame.Command, frame.Length, frame.Data)
	return frame, nil
}

// WriteFrame writes a BinkP frame to the connection
func (s *BinkPSession) WriteFrame(frame *BinkPFrame) error {
	// Set write timeout
	s.conn.SetWriteDeadline(time.Now().Add(WRITE_TIMEOUT))
	
	// Prepare frame header
	length := uint16(len(frame.Data))
	if frame.Command == FRAME_COMMAND {
		length |= 0x8000 // Set command bit
	}
	
	// Write header
	header := make([]byte, 2)
	binary.LittleEndian.PutUint16(header, length)
	if _, err := s.writer.Write(header); err != nil {
		return fmt.Errorf("failed to write frame header: %w", err)
	}
	
	// Write data
	if len(frame.Data) > 0 {
		if _, err := s.writer.Write(frame.Data); err != nil {
			return fmt.Errorf("failed to write frame data: %w", err)
		}
	}
	
	// Flush writer
	if err := s.writer.Flush(); err != nil {
		return fmt.Errorf("failed to flush frame: %w", err)
	}
	
	log.Printf("BinkP TX: cmd=%d, len=%d, data=%q", frame.Command, len(frame.Data), frame.Data)
	return nil
}

// WriteCommand writes a BinkP command frame
func (s *BinkPSession) WriteCommand(cmd uint8, data []byte) error {
	// Prepare command data with command ID
	cmdData := make([]byte, len(data)+1)
	cmdData[0] = cmd
	copy(cmdData[1:], data)
	
	frame := &BinkPFrame{
		Command: FRAME_COMMAND,
		Data:    cmdData,
	}
	
	return s.WriteFrame(frame)
}

// WriteData writes a BinkP data frame
func (s *BinkPSession) WriteData(data []byte) error {
	frame := &BinkPFrame{
		Command: FRAME_DATA,
		Data:    data,
	}
	
	return s.WriteFrame(frame)
}

// ProcessFrame processes a received BinkP frame
func (s *BinkPSession) ProcessFrame(frame *BinkPFrame) error {
	if frame.Command == FRAME_COMMAND {
		return s.processCommand(frame)
	} else {
		return s.processData(frame)
	}
}

// processCommand processes a BinkP command frame
func (s *BinkPSession) processCommand(frame *BinkPFrame) error {
	if len(frame.Data) == 0 {
		return fmt.Errorf("empty command frame")
	}
	
	cmd := frame.Data[0]
	data := frame.Data[1:]
	
	log.Printf("BinkP CMD: %d, data=%q", cmd, data)
	
	switch cmd {
	case M_NUL:
		return s.handleNUL(data)
	case M_ADR:
		return s.handleADR(data)
	case M_PWD:
		return s.handlePWD(data)
	case M_FILE:
		return s.handleFILE(data)
	case M_OK:
		return s.handleOK(data)
	case M_EOB:
		return s.handleEOB(data)
	case M_GOT:
		return s.handleGOT(data)
	case M_ERR:
		return s.handleERR(data)
	case M_BSY:
		return s.handleBSY(data)
	case M_GET:
		return s.handleGET(data)
	case M_SKIP:
		return s.handleSKIP(data)
	default:
		log.Printf("Unknown BinkP command: %d", cmd)
		return nil
	}
}

// processData processes a BinkP data frame
func (s *BinkPSession) processData(frame *BinkPFrame) error {
	log.Printf("BinkP DATA: %d bytes", len(frame.Data))
	s.bytesReceived += int64(len(frame.Data))
	
	// Handle file data reception
	if s.onFileReceived != nil {
		// For now, we'll need to implement file state tracking
		// This is a simplified version
		return s.onFileReceived("received_file.dat", frame.Data)
	}
	
	return nil
}

// BinkP command handlers
func (s *BinkPSession) handleNUL(data []byte) error {
	log.Printf("M_NUL: %s", string(data))
	
	// Parse NUL command for various options
	parts := bytes.Fields(data)
	for _, part := range parts {
		if bytes.HasPrefix(part, []byte("OPT")) {
			// Handle options
			continue
		}
		if bytes.HasPrefix(part, []byte("CRAM-MD5-")) {
			// Handle CRAM-MD5 challenge
			challenge := bytes.TrimPrefix(part, []byte("CRAM-MD5-"))
			log.Printf("Received CRAM-MD5 challenge: %s", challenge)
		}
	}
	
	return nil
}

func (s *BinkPSession) handleADR(data []byte) error {
	s.remoteAddr = string(data)
	log.Printf("M_ADR: Remote address: %s", s.remoteAddr)
	return nil
}

func (s *BinkPSession) handlePWD(data []byte) error {
	password := string(data)
	log.Printf("M_PWD: Password authentication")
	
	if s.onAuthenticate != nil {
		if s.onAuthenticate(s.remoteAddr, password) {
			s.authenticated = true
			return s.WriteCommand(M_OK, []byte(""))
		} else {
			return s.WriteCommand(M_ERR, []byte("Authentication failed"))
		}
	}
	
	return nil
}

func (s *BinkPSession) handleFILE(data []byte) error {
	fileInfo := string(data)
	log.Printf("M_FILE: File info: %s", fileInfo)
	
	// Parse file info: filename size timestamp
	parts := strings.Fields(fileInfo)
	if len(parts) >= 2 {
		filename := parts[0]
		size, _ := strconv.ParseInt(parts[1], 10, 64)
		log.Printf("Receiving file: %s, size: %d", filename, size)
	}
	
	return nil
}

func (s *BinkPSession) handleOK(data []byte) error {
	log.Printf("M_OK: Authentication successful")
	s.authenticated = true
	return nil
}

func (s *BinkPSession) handleEOB(data []byte) error {
	log.Printf("M_EOB: End of batch")
	return nil
}

func (s *BinkPSession) handleGOT(data []byte) error {
	filename := string(data)
	log.Printf("M_GOT: File received: %s", filename)
	return nil
}

func (s *BinkPSession) handleERR(data []byte) error {
	errMsg := string(data)
	log.Printf("M_ERR: Error: %s", errMsg)
	return fmt.Errorf("remote error: %s", errMsg)
}

func (s *BinkPSession) handleBSY(data []byte) error {
	log.Printf("M_BSY: Remote busy")
	return fmt.Errorf("remote system busy")
}

func (s *BinkPSession) handleGET(data []byte) error {
	log.Printf("M_GET: %s", string(data))
	return nil
}

func (s *BinkPSession) handleSKIP(data []byte) error {
	filename := string(data)
	log.Printf("M_SKIP: Skip file: %s", filename)
	return nil
}

// SendHandshake sends initial BinkP handshake
func (s *BinkPSession) SendHandshake() error {
	// Send system info
	if s.systemName != "" {
		sysInfo := fmt.Sprintf("SYS %s", s.systemName)
		if err := s.WriteCommand(M_NUL, []byte(sysInfo)); err != nil {
			return err
		}
	}
	
	if s.sysopName != "" {
		sysopInfo := fmt.Sprintf("ZYZ %s", s.sysopName)
		if err := s.WriteCommand(M_NUL, []byte(sysopInfo)); err != nil {
			return err
		}
	}
	
	if s.location != "" {
		locInfo := fmt.Sprintf("LOC %s", s.location)
		if err := s.WriteCommand(M_NUL, []byte(locInfo)); err != nil {
			return err
		}
	}
	
	// Send local address
	if s.localAddr != "" {
		if err := s.WriteCommand(M_ADR, []byte(s.localAddr)); err != nil {
			return err
		}
	}
	
	return nil
}

// Close closes the BinkP session
func (s *BinkPSession) Close() error {
	if s.conn != nil {
		return s.conn.Close()
	}
	return nil
}

// GetStatistics returns session statistics
func (s *BinkPSession) GetStatistics() (bytesRx, bytesTx int64, filesRx, filesTx int) {
	return s.bytesReceived, s.bytesSent, s.filesReceived, s.filesSent
}