package binkp

import (
	"encoding/hex"
	"testing"
)

func TestCRAMAuth_SetChallengeData(t *testing.T) {
	auth := NewCRAMAuth()
	
	// Test valid hex challenge
	challenge := "48656c6c6f20576f726c64" // "Hello World" in hex
	err := auth.SetChallengeData([]byte(challenge))
	if err != nil {
		t.Errorf("SetChallengeData failed: %v", err)
	}
	
	expected := []byte("Hello World")
	if string(auth.challengeData) != string(expected) {
		t.Errorf("Expected challenge data '%s', got '%s'", expected, auth.challengeData)
	}
	
	// Test invalid hex challenge
	invalidChallenge := "invalid hex data"
	err = auth.SetChallengeData([]byte(invalidChallenge))
	if err == nil {
		t.Errorf("Expected error for invalid hex data, got nil")
	}
}

func TestCRAMAuth_CalculateDigest(t *testing.T) {
	auth := NewCRAMAuth()
	
	// Set test data
	secret := []byte("test-secret")
	challenge := []byte("test-challenge")
	
	auth.SetSecret(secret)
	
	// Convert challenge to hex for SetChallengeData
	hexChallenge := make([]byte, hex.EncodedLen(len(challenge)))
	hex.Encode(hexChallenge, challenge)
	
	err := auth.SetChallengeData(hexChallenge)
	if err != nil {
		t.Fatalf("SetChallengeData failed: %v", err)
	}
	
	// Calculate digest
	digest, err := auth.CalculateDigest()
	if err != nil {
		t.Errorf("CalculateDigest failed: %v", err)
	}
	
	// Digest should be non-empty hex string
	if len(digest) == 0 {
		t.Errorf("Expected non-empty digest, got empty")
	}
	
	// Digest should be valid hex
	_, err = hex.DecodeString(string(digest))
	if err != nil {
		t.Errorf("Digest is not valid hex: %v", err)
	}
	
	// Same inputs should produce same digest
	digest2, err := auth.CalculateDigest()
	if err != nil {
		t.Errorf("Second CalculateDigest failed: %v", err)
	}
	
	if string(digest) != string(digest2) {
		t.Errorf("Digest not consistent: %s != %s", digest, digest2)
	}
}

func TestCRAMAuth_CreateAuthResponse(t *testing.T) {
	auth := NewCRAMAuth()
	
	// Set test data
	secret := []byte("test-password")
	challenge := []byte("random-challenge-data")
	
	auth.SetSecret(secret)
	
	// Convert challenge to hex
	hexChallenge := make([]byte, hex.EncodedLen(len(challenge)))
	hex.Encode(hexChallenge, challenge)
	
	err := auth.SetChallengeData(hexChallenge)
	if err != nil {
		t.Fatalf("SetChallengeData failed: %v", err)
	}
	
	// Create auth response
	response, err := auth.CreateAuthResponse()
	if err != nil {
		t.Errorf("CreateAuthResponse failed: %v", err)
	}
	
	// Response should start with "CRAM-MD5-"
	expectedPrefix := "CRAM-MD5-"
	if len(response) < len(expectedPrefix) || response[:len(expectedPrefix)] != expectedPrefix {
		t.Errorf("Expected response to start with '%s', got '%s'", expectedPrefix, response)
	}
	
	// Response should be non-empty
	if len(response) <= len(expectedPrefix) {
		t.Errorf("Expected response longer than prefix, got '%s'", response)
	}
}

func TestVerifyChallenge(t *testing.T) {
	secret := "test-password"
	challenge := "48656c6c6f20576f726c64" // "Hello World" in hex
	
	// Create expected response
	auth := NewCRAMAuth()
	auth.SetSecret([]byte(secret))
	auth.SetChallengeData([]byte(challenge))
	expectedResponse, err := auth.CreateAuthResponse()
	if err != nil {
		t.Fatalf("Failed to create expected response: %v", err)
	}
	
	// Test valid verification
	if !VerifyChallenge(secret, challenge, expectedResponse) {
		t.Errorf("VerifyChallenge failed for valid inputs")
	}
	
	// Test invalid secret
	if VerifyChallenge("wrong-password", challenge, expectedResponse) {
		t.Errorf("VerifyChallenge succeeded with wrong password")
	}
	
	// Test invalid challenge
	if VerifyChallenge(secret, "wrong-challenge", expectedResponse) {
		t.Errorf("VerifyChallenge succeeded with wrong challenge")
	}
	
	// Test invalid response
	if VerifyChallenge(secret, challenge, "wrong-response") {
		t.Errorf("VerifyChallenge succeeded with wrong response")
	}
}

func TestCRAMAuth_LongSecret(t *testing.T) {
	auth := NewCRAMAuth()
	
	// Test with secret longer than 64 bytes (should be hashed)
	longSecret := make([]byte, 100)
	for i := range longSecret {
		longSecret[i] = byte(i % 256)
	}
	
	challenge := []byte("test-challenge")
	hexChallenge := make([]byte, hex.EncodedLen(len(challenge)))
	hex.Encode(hexChallenge, challenge)
	
	auth.SetSecret(longSecret)
	err := auth.SetChallengeData(hexChallenge)
	if err != nil {
		t.Fatalf("SetChallengeData failed: %v", err)
	}
	
	// Should not fail with long secret
	digest, err := auth.CalculateDigest()
	if err != nil {
		t.Errorf("CalculateDigest failed with long secret: %v", err)
	}
	
	if len(digest) == 0 {
		t.Errorf("Expected non-empty digest with long secret")
	}
}

func TestCRAMAuth_EmptyInputs(t *testing.T) {
	auth := NewCRAMAuth()
	
	// Test with empty secret
	auth.SetSecret([]byte(""))
	
	challenge := []byte("test")
	hexChallenge := make([]byte, hex.EncodedLen(len(challenge)))
	hex.Encode(hexChallenge, challenge)
	
	err := auth.SetChallengeData(hexChallenge)
	if err != nil {
		t.Fatalf("SetChallengeData failed: %v", err)
	}
	
	digest, err := auth.CalculateDigest()
	if err != nil {
		t.Errorf("CalculateDigest failed with empty secret: %v", err)
	}
	
	if len(digest) == 0 {
		t.Errorf("Expected non-empty digest even with empty secret")
	}
	
	// Test with empty challenge
	auth.SetSecret([]byte("secret"))
	err = auth.SetChallengeData([]byte(""))
	if err != nil {
		t.Fatalf("SetChallengeData failed with empty challenge: %v", err)
	}
	
	digest, err = auth.CalculateDigest()
	if err != nil {
		t.Errorf("CalculateDigest failed with empty challenge: %v", err)
	}
	
	if len(digest) == 0 {
		t.Errorf("Expected non-empty digest even with empty challenge")
	}
}