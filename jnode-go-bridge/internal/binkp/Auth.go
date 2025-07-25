package binkp

import (
	"crypto/md5"
	"encoding/hex"
	"fmt"
)

// CRAMAuth implements CRAM-MD5 authentication for BinkP protocol
// Based on Golden Point's auth/Authorization.go implementation
type CRAMAuth struct {
	challengeData []byte
	secret        []byte
}

// NewCRAMAuth creates a new CRAM-MD5 authenticator
func NewCRAMAuth() *CRAMAuth {
	return &CRAMAuth{}
}

// SetSecret sets the authentication secret (password)
func (a *CRAMAuth) SetSecret(secret []byte) {
	a.secret = secret
}

// SetChallengeData sets the challenge data from hex string
func (a *CRAMAuth) SetChallengeData(challengeData []byte) error {
	newChallengeData := make([]byte, hex.DecodedLen(len(challengeData)))
	_, err := hex.Decode(newChallengeData, challengeData)
	if err != nil {
		return err
	}
	a.challengeData = newChallengeData
	return nil
}

// CalculateDigest computes CRAM-MD5 digest following RFC 2104
// HASH((secret XOR opad), HASH((secret XOR ipad), challengedata))
func (a *CRAMAuth) CalculateDigest() ([]byte, error) {
	// Prepare secret key (pad or hash if needed)
	key := a.secret
	if len(key) > 64 {
		// If key is longer than 64 bytes, use MD5 hash of key
		h := md5.New()
		h.Write(key)
		key = h.Sum(nil)
	}
	
	// Pad key to 64 bytes
	keyPadded := make([]byte, 64)
	copy(keyPadded, key)
	
	// Create inner and outer padded keys
	iKeyPad := make([]byte, 64)
	oKeyPad := make([]byte, 64)
	
	for i := 0; i < 64; i++ {
		iKeyPad[i] = keyPadded[i] ^ 0x36 // inner pad
		oKeyPad[i] = keyPadded[i] ^ 0x5C // outer pad
	}
	
	// Inner hash: HASH(iKeyPad + challengeData)
	innerHash := md5.New()
	innerHash.Write(iKeyPad)
	innerHash.Write(a.challengeData)
	innerResult := innerHash.Sum(nil)
	
	// Outer hash: HASH(oKeyPad + innerResult)
	outerHash := md5.New()
	outerHash.Write(oKeyPad)
	outerHash.Write(innerResult)
	outerResult := outerHash.Sum(nil)
	
	// Return hex-encoded result
	hexResult := make([]byte, hex.EncodedLen(len(outerResult)))
	hex.Encode(hexResult, outerResult)
	
	return hexResult, nil
}

// CreateAuthResponse creates CRAM-MD5 authentication response
func (a *CRAMAuth) CreateAuthResponse() (string, error) {
	digest, err := a.CalculateDigest()
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("CRAM-MD5-%s", string(digest)), nil
}

// VerifyChallenge verifies a challenge/response pair
func VerifyChallenge(secret, challenge, response string) bool {
	auth := NewCRAMAuth()
	auth.SetSecret([]byte(secret))
	
	if err := auth.SetChallengeData([]byte(challenge)); err != nil {
		return false
	}
	
	expected, err := auth.CreateAuthResponse()
	if err != nil {
		return false
	}
	
	return expected == response
}