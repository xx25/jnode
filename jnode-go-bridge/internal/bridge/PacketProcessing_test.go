package bridge

import (
	"testing"
	"time"
	
	"jnode-go-bridge/internal/database"
)

func TestParseAddress(t *testing.T) {
	tests := []struct {
		input    string
		expected NetAddr
		hasError bool
	}{
		{
			input: "1:2/3.4",
			expected: NetAddr{
				Zone:  1,
				Net:   2,
				Node:  3,
				Point: 4,
			},
			hasError: false,
		},
		{
			input: "2:5020/1042.0",
			expected: NetAddr{
				Zone:  2,
				Net:   5020,
				Node:  1042,
				Point: 0,
			},
			hasError: false,
		},
		{
			input: "3:633/280",
			expected: NetAddr{
				Zone:  3,
				Net:   633,
				Node:  280,
				Point: 0,
			},
			hasError: false,
		},
		{
			input:    "invalid",
			expected: NetAddr{},
			hasError: true,
		},
		{
			input:    "1:2",
			expected: NetAddr{},
			hasError: true,
		},
		{
			input:    "1/2/3",
			expected: NetAddr{},
			hasError: true,
		},
	}
	
	for _, test := range tests {
		result, err := parseAddress(test.input)
		
		if test.hasError {
			if err == nil {
				t.Errorf("Expected error for input '%s', got nil", test.input)
			}
			continue
		}
		
		if err != nil {
			t.Errorf("Unexpected error for input '%s': %v", test.input, err)
			continue
		}
		
		if result.Zone != test.expected.Zone {
			t.Errorf("For input '%s', expected Zone %d, got %d", test.input, test.expected.Zone, result.Zone)
		}
		
		if result.Net != test.expected.Net {
			t.Errorf("For input '%s', expected Net %d, got %d", test.input, test.expected.Net, result.Net)
		}
		
		if result.Node != test.expected.Node {
			t.Errorf("For input '%s', expected Node %d, got %d", test.input, test.expected.Node, result.Node)
		}
		
		if result.Point != test.expected.Point {
			t.Errorf("For input '%s', expected Point %d, got %d", test.input, test.expected.Point, result.Point)
		}
	}
}

func TestPackEchomailMessage(t *testing.T) {
	// Create a mock queue manager
	qm := &JnodeQueueManager{}
	
	// Create test echomail message
	testTime := time.Date(2024, 1, 15, 12, 30, 45, 0, time.UTC)
	msg := &database.EchomailMessage{
		ID:          123,
		FromName:    "Test Sender",
		ToName:      "All",
		FromFtn:     "1:2/3.4",
		ToFtn:       "0:0/0.0",
		Subject:     "Test Subject",
		Text:        "This is a test message.",
		Date:        testTime,
		AreaName:    "TEST.AREA",
	}
	
	// Pack the message
	packetData, err := qm.packEchomailMessage(msg)
	if err != nil {
		t.Errorf("packEchomailMessage failed: %v", err)
	}
	
	// Basic validation - check that we got some data
	if len(packetData) == 0 {
		t.Errorf("Expected non-empty packet data, got empty slice")
	}
	
	// Check that packet contains expected elements
	packetStr := string(packetData)
	if !containsString(packetStr, "AREA:TEST.AREA") {
		t.Errorf("Expected packet to contain 'AREA:TEST.AREA'")
	}
	
	if !containsString(packetStr, "This is a test message.") {
		t.Errorf("Expected packet to contain message text")
	}
}

func TestPackNetmailMessage(t *testing.T) {
	// Create a mock queue manager
	qm := &JnodeQueueManager{}
	
	// Create test netmail message
	testTime := time.Date(2024, 1, 15, 12, 30, 45, 0, time.UTC)
	msg := &database.NetmailMessage{
		ID:         456,
		FromName:   "Test Sender",
		ToName:     "Test Recipient",
		FromFtn:    "1:2/3.4",
		ToFtn:      "1:2/5.6",
		Subject:    "Test Netmail",
		Text:       "This is a test netmail message.",
		Date:       testTime,
		Attributes: 1, // Private message
	}
	
	// Pack the message
	packetData, err := qm.packNetmailMessage(msg)
	if err != nil {
		t.Errorf("packNetmailMessage failed: %v", err)
	}
	
	// Basic validation - check that we got some data
	if len(packetData) == 0 {
		t.Errorf("Expected non-empty packet data, got empty slice")
	}
	
	// Check that packet contains expected elements
	packetStr := string(packetData)
	if !containsString(packetStr, "This is a test netmail message.") {
		t.Errorf("Expected packet to contain message text")
	}
}

func TestPackInvalidAddress(t *testing.T) {
	// Create a mock queue manager
	qm := &JnodeQueueManager{}
	
	// Create test message with invalid address
	testTime := time.Date(2024, 1, 15, 12, 30, 45, 0, time.UTC)
	msg := &database.EchomailMessage{
		ID:          789,
		FromName:    "Test Sender",
		ToName:      "All",
		FromFtn:     "invalid-address",
		ToFtn:       "0:0/0.0",
		Subject:     "Test Subject",
		Text:        "This is a test message.",
		Date:        testTime,
		AreaName:    "TEST.AREA",
	}
	
	// Pack the message - should fail
	_, err := qm.packEchomailMessage(msg)
	if err == nil {
		t.Errorf("Expected error for invalid address, got nil")
	}
}

// Helper function to check if a string contains a substring
func containsString(s, substr string) bool {
	return len(s) >= len(substr) && 
		   (s == substr || 
		    containsString(s[1:], substr) || 
		    (len(s) > 0 && s[:len(substr)] == substr))
}