package bridge

import (
	"database/sql"
	"fmt"
	"log"
	"os"
	"path"

	"jnode-go-bridge/internal/config"
	"jnode-go-bridge/internal/database"
)

type FileEntry struct {
	Name      string
	Type      string
	Size      int64
	Data      []byte
	LinkID    int64
	MessageID int64
}

type JnodeQueueManager struct {
	db     *sql.DB
	config *config.JnodeConfig
}

func NewJnodeQueueManager(db *sql.DB, config *config.JnodeConfig) *JnodeQueueManager {
	return &JnodeQueueManager{
		db:     db,
		config: config,
	}
}

func (j *JnodeQueueManager) GetOutboundFiles(linkAddr string) ([]FileEntry, error) {
	linkID, err := j.getLinkID(linkAddr)
	if err != nil {
		return nil, err
	}
	
	var files []FileEntry
	
	// Get echomail from existing jNode tables
	echoFiles, err := j.getOutboundEchomail(linkID)
	if err != nil {
		return nil, err
	}
	files = append(files, echoFiles...)
	
	// Get netmail from existing jNode tables  
	netmailFiles, err := j.getOutboundNetmail(linkID)
	if err != nil {
		return nil, err
	}
	files = append(files, netmailFiles...)
	
	// Get filemail from existing jNode tables
	filemailFiles, err := j.getOutboundFilemail(linkID)
	if err != nil {
		return nil, err
	}
	files = append(files, filemailFiles...)
	
	return files, nil
}

func (j *JnodeQueueManager) MarkSent(entry FileEntry) error {
	switch entry.Type {
	case "echomail":
		return j.markEchomailSent(entry.LinkID, entry.MessageID)
	case "netmail":
		return j.markNetmailSent(entry.MessageID)
	case "filemail":
		return j.markFilemailSent(entry.LinkID, entry.MessageID)
	default:
		return fmt.Errorf("unknown message type: %s", entry.Type)
	}
}

func (j *JnodeQueueManager) SaveInbound(file FileEntry) error {
	// Save to jNode's inbound directory
	inboundPath := j.config.InboundDir
	filepath := path.Join(inboundPath, file.Name)
	
	// Create directory if needed
	if err := os.MkdirAll(inboundPath, 0755); err != nil {
		return err
	}
	
	// Write file - jNode's tosser will process it
	return os.WriteFile(filepath, file.Data, 0644)
}

// Private methods for database operations
func (j *JnodeQueueManager) getLinkID(ftnAddr string) (int64, error) {
	query := "SELECT id FROM links WHERE ftn_address = ? AND enabled = true"
	row := j.db.QueryRow(query, ftnAddr)
	
	var linkID int64
	err := row.Scan(&linkID)
	return linkID, err
}

func (j *JnodeQueueManager) getOutboundEchomail(linkID int64) ([]FileEntry, error) {
	query := `
		SELECT e.id, e.from_name, e.to_name, e.from_ftn, e.to_ftn,
		       e.subject, e.text, e.date, e.area_name
		FROM echomails e
		JOIN echomail_queue ea ON e.id = ea.echomail_id
		WHERE ea.link_id = ?
		ORDER BY e.date ASC
	`
	
	rows, err := j.db.Query(query, linkID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	
	var files []FileEntry
	for rows.Next() {
		var msg database.EchomailMessage
		err := rows.Scan(&msg.ID, &msg.FromName, &msg.ToName,
			&msg.FromFtn, &msg.ToFtn, &msg.Subject,
			&msg.Text, &msg.Date, &msg.AreaName)
		if err != nil {
			return nil, err
		}
		
		// Pack message into FTN packet format
		packetData, err := j.packEchomailMessage(&msg)
		if err != nil {
			return nil, err
		}
		
		entry := FileEntry{
			Name:      fmt.Sprintf("echo_%d.pkt", msg.ID),
			Type:      "echomail",
			Size:      int64(len(packetData)),
			Data:      packetData,
			LinkID:    linkID,
			MessageID: msg.ID,
		}
		
		files = append(files, entry)
	}
	
	return files, nil
}

func (j *JnodeQueueManager) getOutboundNetmail(linkID int64) ([]FileEntry, error) {
	query := `
		SELECT id, from_name, to_name, from_ftn, to_ftn, subject, text,
		       date, attributes
		FROM netmails
		WHERE route_via = ? AND send = false
		ORDER BY date ASC
	`
	
	rows, err := j.db.Query(query, linkID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	
	var files []FileEntry
	for rows.Next() {
		var msg database.NetmailMessage
		err := rows.Scan(&msg.ID, &msg.FromName, &msg.ToName,
			&msg.FromFtn, &msg.ToFtn, &msg.Subject,
			&msg.Text, &msg.Date, &msg.Attributes)
		if err != nil {
			return nil, err
		}
		
		// Pack message into FTN packet format
		packetData, err := j.packNetmailMessage(&msg)
		if err != nil {
			return nil, err
		}
		
		entry := FileEntry{
			Name:      fmt.Sprintf("netmail_%d.pkt", msg.ID),
			Type:      "netmail", 
			Size:      int64(len(packetData)),
			Data:      packetData,
			LinkID:    linkID,
			MessageID: msg.ID,
		}
		
		files = append(files, entry)
	}
	
	return files, nil
}

func (j *JnodeQueueManager) getOutboundFilemail(linkID int64) ([]FileEntry, error) {
	query := `
		SELECT f.id, f.filename, f.filepath, f.size
		FROM filemails f
		JOIN filemail_queue fa ON f.id = fa.filemail_id
		WHERE fa.link_id = ?
		ORDER BY f.created ASC
	`
	
	rows, err := j.db.Query(query, linkID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	
	var files []FileEntry
	for rows.Next() {
		var fileID int64
		var filename, filepath string
		var size int64
		
		err := rows.Scan(&fileID, &filename, &filepath, &size)
		if err != nil {
			return nil, err
		}
		
		// Read file data
		fileData, err := os.ReadFile(filepath)
		if err != nil {
			log.Printf("Failed to read file %s: %v", filepath, err)
			continue
		}
		
		entry := FileEntry{
			Name:      filename,
			Type:      "filemail",
			Size:      int64(len(fileData)),
			Data:      fileData,
			LinkID:    linkID,
			MessageID: fileID,
		}
		
		files = append(files, entry)
	}
	
	return files, nil
}

func (j *JnodeQueueManager) markEchomailSent(linkID, echomailID int64) error {
	query := "DELETE FROM echomail_queue WHERE link_id = ? AND echomail_id = ?"
	_, err := j.db.Exec(query, linkID, echomailID)
	return err
}

func (j *JnodeQueueManager) markNetmailSent(netmailID int64) error {
	query := "UPDATE netmails SET send = true WHERE id = ?"
	_, err := j.db.Exec(query, netmailID)
	return err
}

func (j *JnodeQueueManager) markFilemailSent(linkID, filemailID int64) error {
	query := "DELETE FROM filemail_queue WHERE link_id = ? AND filemail_id = ?"
	_, err := j.db.Exec(query, linkID, filemailID)
	return err
}

