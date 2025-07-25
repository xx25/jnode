package bridge

import (
	"database/sql"
	"jnode-go-bridge/internal/config"
)

type JnodeConfigManager struct {
	config *config.JnodeConfig
	db     *sql.DB
}

func NewJnodeConfigManager(config *config.JnodeConfig, db *sql.DB) *JnodeConfigManager {
	return &JnodeConfigManager{
		config: config,
		db:     db,
	}
}

// Implement Golden's expected config interface
func (j *JnodeConfigManager) GetSystemName() string {
	return j.config.StationName
}

func (j *JnodeConfigManager) GetSysopName() string {
	return j.config.SysopName
}

func (j *JnodeConfigManager) GetLocation() string {
	return j.config.Location
}

func (j *JnodeConfigManager) GetPrimaryAddress() string {
	return j.config.PrimaryAddress
}

func (j *JnodeConfigManager) GetPassword(linkAddr string) (string, error) {
	query := "SELECT password FROM links WHERE ftn_address = ? AND enabled = true"
	row := j.db.QueryRow(query, linkAddr)
	
	var password string
	err := row.Scan(&password)
	if err != nil {
		return "", err
	}
	
	// Handle jNode's password conventions
	if password == "-" || password == "" {
		return "", nil // No password required
	}
	
	return password, nil
}

func (j *JnodeConfigManager) GetPacketPassword(linkAddr string) (string, error) {
	query := "SELECT pkt_password FROM links WHERE ftn_address = ? AND enabled = true"
	row := j.db.QueryRow(query, linkAddr)
	
	var password string
	err := row.Scan(&password)
	if err != nil {
		return "", err
	}
	
	return password, nil
}

func (j *JnodeConfigManager) GetInboundDir() string {
	return j.config.InboundDir
}

func (j *JnodeConfigManager) GetOutboundDir() string {
	return j.config.OutboundDir
}

func (j *JnodeConfigManager) GetTempDir() string {
	return j.config.TempDir
}

func (j *JnodeConfigManager) GetBindAddress() string {
	return j.config.BindAddress
}

func (j *JnodeConfigManager) GetBindPort() int {
	return j.config.BindPort
}

func (j *JnodeConfigManager) IsIPv6Enabled() bool {
	return j.config.IPv6Enable
}

func (j *JnodeConfigManager) GetIPv6BindAddress() string {
	return j.config.Bind6Address
}