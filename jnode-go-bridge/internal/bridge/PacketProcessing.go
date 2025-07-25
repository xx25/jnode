package bridge

import (
	"bytes"
	"encoding/binary"
	"fmt"
	"strconv"
	"strings"

	"jnode-go-bridge/internal/database"
)

// NetAddr represents an FTN address
type NetAddr struct {
	Zone  uint16
	Net   uint16
	Node  uint16
	Point uint16
}

// PacketHeader represents FTN packet header structure
type PacketHeader struct {
	OrigNode   uint16
	DestNode   uint16
	Year       uint16
	Month      uint16
	Day        uint16
	Hour       uint16
	Minute     uint16
	Second     uint16
	Baud       uint16
	PacketType uint16
	OrigNet    uint16
	DestNet    uint16
	ProdCodeLo uint8
	ProdCodeHi uint8
	Password   [8]byte
	OrigZone   uint16
	DestZone   uint16
	AuxNet     uint16
	CWValidate uint16
	ProdCode   uint8
	ProdRev    uint8
	Password2  [8]byte
	OrigPoint  uint16
	DestPoint  uint16
	ProdData   [4]byte
}

// PackedMessage represents a message in packet format
type PackedMessage struct {
	MessageType  uint16
	OrigNode     uint16
	DestNode     uint16
	OrigNet      uint16
	DestNet      uint16
	Attributes   uint16
	Cost         uint16
	DateTime     [20]byte
	ToUserName   [36]byte
	FromUserName [36]byte
	Subject      [72]byte
	Text         []byte
}

func (j *JnodeQueueManager) packEchomailMessage(msg *database.EchomailMessage) ([]byte, error) {
	var buf bytes.Buffer
	
	// Parse addresses
	fromAddr, err := parseAddress(msg.FromFtn)
	if err != nil {
		return nil, fmt.Errorf("invalid from address %s: %w", msg.FromFtn, err)
	}
	
	// Create packet header for echomail (broadcast to all)
	header := PacketHeader{
		OrigNode:   fromAddr.Node,
		DestNode:   0, // Echomail broadcast
		Year:       uint16(msg.Date.Year()),
		Month:      uint16(msg.Date.Month()),
		Day:        uint16(msg.Date.Day()),
		Hour:       uint16(msg.Date.Hour()),
		Minute:     uint16(msg.Date.Minute()),
		Second:     uint16(msg.Date.Second()),
		PacketType: 2,
		OrigNet:    fromAddr.Net,
		DestNet:    0,
		OrigZone:   fromAddr.Zone,
		DestZone:   0,
		OrigPoint:  fromAddr.Point,
		DestPoint:  0,
	}
	
	// Write packet header
	if err := binary.Write(&buf, binary.LittleEndian, header); err != nil {
		return nil, err
	}
	
	// Create packed message
	packedMsg := PackedMessage{
		MessageType: 2, // Normal message
		OrigNode:    fromAddr.Node,
		DestNode:    0, // Echomail
		OrigNet:     fromAddr.Net,
		DestNet:     0,
		Attributes:  0, // Public message
		Cost:        0,
	}
	
	// Format date/time (FTN format: "DD MMM YY  HH:MM:SS")
	dateStr := msg.Date.Format("02 Jan 06  15:04:05")
	copy(packedMsg.DateTime[:], dateStr)
	
	// Copy names and subject (null-terminated)
	copy(packedMsg.ToUserName[:], msg.ToName)
	copy(packedMsg.FromUserName[:], msg.FromName)
	copy(packedMsg.Subject[:], msg.Subject)
	
	// Write packed message header
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.MessageType); err != nil {
		return nil, err
	}
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.OrigNode); err != nil {
		return nil, err
	}
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.DestNode); err != nil {
		return nil, err
	}
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.OrigNet); err != nil {
		return nil, err
	}
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.DestNet); err != nil {
		return nil, err
	}
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.Attributes); err != nil {
		return nil, err
	}
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.Cost); err != nil {
		return nil, err
	}
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.DateTime); err != nil {
		return nil, err
	}
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.ToUserName); err != nil {
		return nil, err
	}
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.FromUserName); err != nil {
		return nil, err
	}
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.Subject); err != nil {
		return nil, err
	}
	
	// Write message text with AREA kludge
	messageText := fmt.Sprintf("AREA:%s\r\n%s", msg.AreaName, msg.Text)
	if _, err := buf.WriteString(messageText); err != nil {
		return nil, err
	}
	
	// Write message terminator
	if err := buf.WriteByte(0); err != nil {
		return nil, err
	}
	
	// Write packet terminator
	if err := binary.Write(&buf, binary.LittleEndian, uint16(0)); err != nil {
		return nil, err
	}
	
	return buf.Bytes(), nil
}

func (j *JnodeQueueManager) packNetmailMessage(msg *database.NetmailMessage) ([]byte, error) {
	var buf bytes.Buffer
	
	// Parse addresses
	fromAddr, err := parseAddress(msg.FromFtn)
	if err != nil {
		return nil, fmt.Errorf("invalid from address %s: %w", msg.FromFtn, err)
	}
	
	toAddr, err := parseAddress(msg.ToFtn)
	if err != nil {
		return nil, fmt.Errorf("invalid to address %s: %w", msg.ToFtn, err)
	}
	
	// Create packet header for netmail
	header := PacketHeader{
		OrigNode:   fromAddr.Node,
		DestNode:   toAddr.Node,
		Year:       uint16(msg.Date.Year()),
		Month:      uint16(msg.Date.Month()),
		Day:        uint16(msg.Date.Day()),
		Hour:       uint16(msg.Date.Hour()),
		Minute:     uint16(msg.Date.Minute()),
		Second:     uint16(msg.Date.Second()),
		PacketType: 2,
		OrigNet:    fromAddr.Net,
		DestNet:    toAddr.Net,
		OrigZone:   fromAddr.Zone,
		DestZone:   toAddr.Zone,
		OrigPoint:  fromAddr.Point,
		DestPoint:  toAddr.Point,
	}
	
	// Write packet header
	if err := binary.Write(&buf, binary.LittleEndian, header); err != nil {
		return nil, err
	}
	
	// Create packed message
	packedMsg := PackedMessage{
		MessageType: 2, // Normal message
		OrigNode:    fromAddr.Node,
		DestNode:    toAddr.Node,
		OrigNet:     fromAddr.Net,
		DestNet:     toAddr.Net,
		Attributes:  uint16(msg.Attributes),
		Cost:        0,
	}
	
	// Format date/time (FTN format: "DD MMM YY  HH:MM:SS")
	dateStr := msg.Date.Format("02 Jan 06  15:04:05")
	copy(packedMsg.DateTime[:], dateStr)
	
	// Copy names and subject (null-terminated)
	copy(packedMsg.ToUserName[:], msg.ToName)
	copy(packedMsg.FromUserName[:], msg.FromName)
	copy(packedMsg.Subject[:], msg.Subject)
	
	// Write packed message header
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.MessageType); err != nil {
		return nil, err
	}
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.OrigNode); err != nil {
		return nil, err
	}
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.DestNode); err != nil {
		return nil, err
	}
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.OrigNet); err != nil {
		return nil, err
	}
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.DestNet); err != nil {
		return nil, err
	}
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.Attributes); err != nil {
		return nil, err
	}
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.Cost); err != nil {
		return nil, err
	}
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.DateTime); err != nil {
		return nil, err
	}
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.ToUserName); err != nil {
		return nil, err
	}
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.FromUserName); err != nil {
		return nil, err
	}
	if err := binary.Write(&buf, binary.LittleEndian, packedMsg.Subject); err != nil {
		return nil, err
	}
	
	// Write message text
	if _, err := buf.WriteString(msg.Text); err != nil {
		return nil, err
	}
	
	// Write message terminator
	if err := buf.WriteByte(0); err != nil {
		return nil, err
	}
	
	// Write packet terminator
	if err := binary.Write(&buf, binary.LittleEndian, uint16(0)); err != nil {
		return nil, err
	}
	
	return buf.Bytes(), nil
}

func parseAddress(addrStr string) (*NetAddr, error) {
	// Parse FTN address string into NetAddr format
	// Format: zone:net/node.point or zone:net/node
	
	parts := strings.Split(addrStr, ":")
	if len(parts) != 2 {
		return nil, fmt.Errorf("invalid address format: %s", addrStr)
	}
	
	zone, err := strconv.ParseUint(parts[0], 10, 16)
	if err != nil {
		return nil, err
	}
	
	netNode := strings.Split(parts[1], "/")
	if len(netNode) != 2 {
		return nil, fmt.Errorf("invalid net/node format: %s", addrStr)
	}
	
	net, err := strconv.ParseUint(netNode[0], 10, 16)
	if err != nil {
		return nil, err
	}
	
	nodePoint := strings.Split(netNode[1], ".")
	node, err := strconv.ParseUint(nodePoint[0], 10, 16)
	if err != nil {
		return nil, err
	}
	
	var point uint64 = 0
	if len(nodePoint) > 1 {
		point, err = strconv.ParseUint(nodePoint[1], 10, 16)
		if err != nil {
			return nil, err
		}
	}
	
	return &NetAddr{
		Zone:  uint16(zone),
		Net:   uint16(net),
		Node:  uint16(node),
		Point: uint16(point),
	}, nil
}