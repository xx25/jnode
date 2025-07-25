package database

import "time"

// JNode database schema definitions for Go structs

type Link struct {
	ID           int64  `db:"id"`
	FtnAddress   string `db:"ftn_address"`
	Password     string `db:"password"`
	PktPassword  string `db:"pkt_password"`
	Enabled      bool   `db:"enabled"`
	Name         string `db:"name"`
	Location     string `db:"location"`
	SysopName    string `db:"sysop_name"`
	Host         string `db:"host"`
	Port         int    `db:"port"`
	ProtocolType string `db:"protocol_type"`
}

type EchomailMessage struct {
	ID          int64     `db:"id"`
	FromName    string    `db:"from_name"`
	ToName      string    `db:"to_name"`
	FromFtn     string    `db:"from_ftn"`
	ToFtn       string    `db:"to_ftn"`
	Subject     string    `db:"subject"`
	Text        string    `db:"text"`
	Date        time.Time `db:"date"`
	AreaName    string    `db:"area_name"`
	MsgID       string    `db:"msgid"`
	Reply       string    `db:"reply"`
	Attributes  int       `db:"attributes"`
}

type NetmailMessage struct {
	ID         int64     `db:"id"`
	FromName   string    `db:"from_name"`
	ToName     string    `db:"to_name"`
	FromFtn    string    `db:"from_ftn"`
	ToFtn      string    `db:"to_ftn"`
	Subject    string    `db:"subject"`
	Text       string    `db:"text"`
	Date       time.Time `db:"date"`
	Attributes int       `db:"attributes"`
	RouteVia   int64     `db:"route_via"`
	Send       bool      `db:"send"`
}

type FilemailMessage struct {
	ID       int64     `db:"id"`
	Filename string    `db:"filename"`
	Filepath string    `db:"filepath"`
	Size     int64     `db:"size"`
	Created  time.Time `db:"created"`
}

type EchomailAwaiting struct {
	ID         int64 `db:"id"`
	LinkID     int64 `db:"link_id"`
	EchomailID int64 `db:"echomail_id"`
}

type NetmailAwaiting struct {
	ID        int64 `db:"id"`
	LinkID    int64 `db:"link_id"`
	NetmailID int64 `db:"netmail_id"`
}

type FilemailAwaiting struct {
	ID         int64 `db:"id"`
	LinkID     int64 `db:"link_id"`
	FilemailID int64 `db:"filemail_id"`
}

type Echoarea struct {
	ID          int64  `db:"id"`
	Name        string `db:"name"`
	Description string `db:"description"`
	ReadLevel   int    `db:"read_level"`
	WriteLevel  int    `db:"write_level"`
	GroupID     int64  `db:"group_id"`
}

type Subscription struct {
	ID         int64 `db:"id"`
	LinkID     int64 `db:"link_id"`
	EchoareaID int64 `db:"echoarea_id"`
	Subscribed bool  `db:"subscribed"`
}