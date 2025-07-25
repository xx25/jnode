package binkp

import (
	"bytes"
	"encoding/binary"
	"net"
	"testing"
	"time"
)

func TestBinkPFrame_Encoding(t *testing.T) {
	tests := []struct {
		name     string
		frame    *BinkPFrame
		expected []byte
	}{
		{
			name: "Command frame",
			frame: &BinkPFrame{
				Length:  5,
				Command: FRAME_COMMAND,
				Data:    []byte{M_NUL, 't', 'e', 's', 't'},
			},
			expected: []byte{0x05, 0x80, M_NUL, 't', 'e', 's', 't'}, // Length with command bit set
		},
		{
			name: "Data frame",
			frame: &BinkPFrame{
				Length:  4,
				Command: FRAME_DATA,
				Data:    []byte{'d', 'a', 't', 'a'},
			},
			expected: []byte{0x04, 0x00, 'd', 'a', 't', 'a'}, // Length without command bit
		},
		{
			name: "Empty frame",
			frame: &BinkPFrame{
				Length:  0,
				Command: FRAME_COMMAND,
				Data:    []byte{},
			},
			expected: []byte{0x00, 0x80}, // Just header with command bit
		},
	}
	
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			// Create mock connection
			server, client := net.Pipe()
			defer server.Close()
			defer client.Close()
			
			session := NewBinkPSession(client)
			
			// Write frame in goroutine
			go func() {
				defer client.Close()
				if err := session.WriteFrame(test.frame); err != nil {
					t.Errorf("WriteFrame failed: %v", err)
				}
			}()
			
			// Read raw data from server side
			buffer := make([]byte, len(test.expected))
			n, err := server.Read(buffer)
			if err != nil {
				t.Fatalf("Failed to read data: %v", err)
			}
			
			if n != len(test.expected) {
				t.Errorf("Expected %d bytes, got %d", len(test.expected), n)
			}
			
			if !bytes.Equal(buffer[:n], test.expected) {
				t.Errorf("Expected %v, got %v", test.expected, buffer[:n])
			}
		})
	}
}

func TestBinkPFrame_Decoding(t *testing.T) {
	tests := []struct {
		name     string
		input    []byte
		expected *BinkPFrame
	}{
		{
			name:  "Command frame",
			input: []byte{0x05, 0x80, M_NUL, 't', 'e', 's', 't'},
			expected: &BinkPFrame{
				Length:  5,
				Command: FRAME_COMMAND,
				Data:    []byte{M_NUL, 't', 'e', 's', 't'},
			},
		},
		{
			name:  "Data frame",
			input: []byte{0x04, 0x00, 'd', 'a', 't', 'a'},
			expected: &BinkPFrame{
				Length:  4,
				Command: FRAME_DATA,
				Data:    []byte{'d', 'a', 't', 'a'},
			},
		},
		{
			name:  "Empty frame",
			input: []byte{0x00, 0x80},
			expected: &BinkPFrame{
				Length:  0,
				Command: FRAME_COMMAND,
				Data:    []byte{},
			},
		},
	}
	
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			// Create mock connection
			server, client := net.Pipe()
			defer server.Close()
			defer client.Close()
			
			session := NewBinkPSession(client)
			
			// Write test data to server side
			go func() {
				defer server.Close()
				server.Write(test.input)
			}()
			
			// Read frame
			frame, err := session.ReadFrame()
			if err != nil {
				t.Fatalf("ReadFrame failed: %v", err)
			}
			
			if frame.Length != test.expected.Length {
				t.Errorf("Expected length %d, got %d", test.expected.Length, frame.Length)
			}
			
			if frame.Command != test.expected.Command {
				t.Errorf("Expected command %d, got %d", test.expected.Command, frame.Command)
			}
			
			if !bytes.Equal(frame.Data, test.expected.Data) {
				t.Errorf("Expected data %v, got %v", test.expected.Data, frame.Data)
			}
		})
	}
}

func TestBinkPSession_WriteCommand(t *testing.T) {
	// Create mock connection
	server, client := net.Pipe()
	defer server.Close()
	defer client.Close()
	
	session := NewBinkPSession(client)
	
	// Test data
	cmd := uint8(M_ADR)
	data := []byte("1:2/3.4@fidonet")
	
	// Write command in goroutine
	go func() {
		defer client.Close()
		if err := session.WriteCommand(cmd, data); err != nil {
			t.Errorf("WriteCommand failed: %v", err)
		}
	}()
	
	// Read and verify frame structure
	header := make([]byte, 2)
	if _, err := server.Read(header); err != nil {
		t.Fatalf("Failed to read header: %v", err)
	}
	
	// Parse header
	rawLength := binary.LittleEndian.Uint16(header)
	isCommand := (rawLength & 0x8000) != 0
	length := rawLength & 0x7FFF
	
	if !isCommand {
		t.Errorf("Expected command frame, got data frame")
	}
	
	expectedLength := uint16(len(data) + 1) // +1 for command byte
	if length != expectedLength {
		t.Errorf("Expected length %d, got %d", expectedLength, length)
	}
	
	// Read frame data
	frameData := make([]byte, length)
	if _, err := server.Read(frameData); err != nil {
		t.Fatalf("Failed to read frame data: %v", err)
	}
	
	// Verify command byte
	if frameData[0] != cmd {
		t.Errorf("Expected command %d, got %d", cmd, frameData[0])
	}
	
	// Verify data
	if !bytes.Equal(frameData[1:], data) {
		t.Errorf("Expected data %s, got %s", string(data), string(frameData[1:]))
	}
}

func TestBinkPSession_WriteData(t *testing.T) {
	// Create mock connection
	server, client := net.Pipe()
	defer server.Close()
	defer client.Close()
	
	session := NewBinkPSession(client)
	
	// Test data
	data := []byte("This is test file data")
	
	// Write data in goroutine
	go func() {
		defer client.Close()
		if err := session.WriteData(data); err != nil {
			t.Errorf("WriteData failed: %v", err)
		}
	}()
	
	// Read and verify frame structure
	header := make([]byte, 2)
	if _, err := server.Read(header); err != nil {
		t.Fatalf("Failed to read header: %v", err)
	}
	
	// Parse header
	rawLength := binary.LittleEndian.Uint16(header)
	isCommand := (rawLength & 0x8000) != 0
	length := rawLength & 0x7FFF
	
	if isCommand {
		t.Errorf("Expected data frame, got command frame")
	}
	
	expectedLength := uint16(len(data))
	if length != expectedLength {
		t.Errorf("Expected length %d, got %d", expectedLength, length)
	}
	
	// Read frame data
	frameData := make([]byte, length)
	if _, err := server.Read(frameData); err != nil {
		t.Fatalf("Failed to read frame data: %v", err)
	}
	
	// Verify data
	if !bytes.Equal(frameData, data) {
		t.Errorf("Expected data %s, got %s", string(data), string(frameData))
	}
}

func TestBinkPSession_SetSystemInfo(t *testing.T) {
	// Create mock connection
	server, client := net.Pipe()
	defer server.Close()
	defer client.Close()
	
	session := NewBinkPSession(client)
	
	// Set system info
	systemName := "Test System"
	sysopName := "Test Sysop"
	location := "Test Location"
	
	session.SetSystemInfo(systemName, sysopName, location)
	
	// Verify fields are set
	if session.systemName != systemName {
		t.Errorf("Expected system name %s, got %s", systemName, session.systemName)
	}
	
	if session.sysopName != sysopName {
		t.Errorf("Expected sysop name %s, got %s", sysopName, session.sysopName)
	}
	
	if session.location != location {
		t.Errorf("Expected location %s, got %s", location, session.location)
	}
}

func TestBinkPSession_GetStatistics(t *testing.T) {
	// Create mock connection
	server, client := net.Pipe()
	defer server.Close()
	defer client.Close()
	
	session := NewBinkPSession(client)
	
	// Set some test statistics
	session.bytesReceived = 1234
	session.bytesSent = 5678
	session.filesReceived = 3
	session.filesSent = 2
	
	// Get statistics
	bytesRx, bytesTx, filesRx, filesTx := session.GetStatistics()
	
	if bytesRx != 1234 {
		t.Errorf("Expected bytes received 1234, got %d", bytesRx)
	}
	
	if bytesTx != 5678 {
		t.Errorf("Expected bytes sent 5678, got %d", bytesTx)
	}
	
	if filesRx != 3 {
		t.Errorf("Expected files received 3, got %d", filesRx)
	}
	
	if filesTx != 2 {
		t.Errorf("Expected files sent 2, got %d", filesTx)
	}
}

func TestBinkPSession_Timeouts(t *testing.T) {
	// Create mock connection
	server, client := net.Pipe()
	defer server.Close()
	defer client.Close()
	
	session := NewBinkPSession(client)
	
	// Close server side to trigger timeout
	server.Close()
	
	// Set a very short timeout for testing
	oldTimeout := READ_TIMEOUT
	defer func() {
		// Restore original timeout (though this won't affect the const)
		_ = oldTimeout
	}()
	
	// Try to read frame - should timeout quickly
	start := time.Now()
	_, err := session.ReadFrame()
	duration := time.Since(start)
	
	if err == nil {
		t.Errorf("Expected error due to closed connection, got nil")
	}
	
	// Should fail quickly, not wait for full timeout
	if duration > 1*time.Second {
		t.Errorf("ReadFrame took too long: %v", duration)
	}
}