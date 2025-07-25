package logging

import (
	"io"
	"os"
	"path/filepath"
	
	"github.com/sirupsen/logrus"
	"jnode-go-bridge/internal/config"
)

// Logger wraps logrus with jNode-specific configuration
type Logger struct {
	*logrus.Logger
	config *config.JnodeConfig
}

// NewLogger creates a new logger instance configured for jNode
func NewLogger(config *config.JnodeConfig) (*Logger, error) {
	logger := logrus.New()
	
	// Set log level based on jNode configuration
	switch config.LogLevel {
	case 1:
		logger.SetLevel(logrus.ErrorLevel)
	case 2:
		logger.SetLevel(logrus.WarnLevel)
	case 3:
		logger.SetLevel(logrus.InfoLevel)
	case 4:
		logger.SetLevel(logrus.DebugLevel)
	case 5:
		logger.SetLevel(logrus.TraceLevel)
	default:
		logger.SetLevel(logrus.InfoLevel)
	}
	
	// Configure output
	var output io.Writer = os.Stdout
	
	if config.LogFile != "" {
		// Ensure log directory exists
		logDir := filepath.Dir(config.LogFile)
		if err := os.MkdirAll(logDir, 0755); err != nil {
			return nil, err
		}
		
		// Open log file
		file, err := os.OpenFile(config.LogFile, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)
		if err != nil {
			return nil, err
		}
		
		// Use both file and stdout
		output = io.MultiWriter(os.Stdout, file)
	}
	
	logger.SetOutput(output)
	
	// Use structured logging format
	logger.SetFormatter(&logrus.TextFormatter{
		FullTimestamp:   true,
		TimestampFormat: "2006-01-02 15:04:05",
	})
	
	return &Logger{
		Logger: logger,
		config: config,
	}, nil
}

// WithBinkPSession creates a logger with BinkP session context
func (l *Logger) WithBinkPSession(sessionID int64, remoteAddr string) *logrus.Entry {
	return l.WithFields(logrus.Fields{
		"component":  "binkp",
		"session_id": sessionID,
		"remote":     remoteAddr,
	})
}

// WithBinkPServer creates a logger with BinkP server context  
func (l *Logger) WithBinkPServer() *logrus.Entry {
	return l.WithFields(logrus.Fields{
		"component": "binkp-server",
	})
}

// WithBinkPClient creates a logger with BinkP client context
func (l *Logger) WithBinkPClient(remoteAddr string) *logrus.Entry {
	return l.WithFields(logrus.Fields{
		"component": "binkp-client",
		"remote":    remoteAddr,
	})
}

// WithDatabase creates a logger with database context
func (l *Logger) WithDatabase() *logrus.Entry {
	return l.WithFields(logrus.Fields{
		"component": "database",
	})
}

// WithQueue creates a logger with queue context
func (l *Logger) WithQueue() *logrus.Entry {
	return l.WithFields(logrus.Fields{
		"component": "queue",
	})
}

// WithConfig creates a logger with configuration context
func (l *Logger) WithConfig() *logrus.Entry {
	return l.WithFields(logrus.Fields{
		"component": "config",
	})
}

// LogBinkPFrame logs a BinkP frame with appropriate detail level
func (l *Logger) LogBinkPFrame(direction string, frameType string, cmd uint8, dataLen int, data []byte) {
	entry := l.WithFields(logrus.Fields{
		"direction":  direction,
		"frame_type": frameType,
		"command":    cmd,
		"data_len":   dataLen,
	})
	
	// Log frame data at trace level for debugging
	if l.Logger.Level >= logrus.TraceLevel && len(data) > 0 {
		if len(data) <= 64 {
			// Short data - log in full
			entry = entry.WithField("data", string(data))
		} else {
			// Long data - log truncated
			entry = entry.WithField("data", string(data[:64])+"...")
		}
	}
	
	entry.Debug("BinkP frame")
}

// LogSessionStatistics logs session completion statistics
func (l *Logger) LogSessionStatistics(sessionID int64, remoteAddr string, duration string, 
	bytesRx, bytesTx int64, filesRx, filesTx int) {
	
	l.WithFields(logrus.Fields{
		"session_id":    sessionID,
		"remote":        remoteAddr,
		"duration":      duration,
		"bytes_rx":      bytesRx,
		"bytes_tx":      bytesTx,
		"files_rx":      filesRx,
		"files_tx":      filesTx,
	}).Info("BinkP session completed")
}

// LogError logs an error with context
func (l *Logger) LogError(err error, context string, fields logrus.Fields) {
	entry := l.WithError(err)
	if fields != nil {
		entry = entry.WithFields(fields)
	}
	entry.Error(context)
}

// LogFileTransfer logs file transfer information
func (l *Logger) LogFileTransfer(direction string, filename string, size int64, offset int64) {
	l.WithFields(logrus.Fields{
		"direction": direction,
		"filename":  filename,
		"size":      size,
		"offset":    offset,
	}).Info("File transfer")
}

// LogAuthentication logs authentication attempts
func (l *Logger) LogAuthentication(remoteAddr string, method string, success bool) {
	entry := l.WithFields(logrus.Fields{
		"remote":  remoteAddr,
		"method":  method,
		"success": success,
	})
	
	if success {
		entry.Info("Authentication successful")
	} else {
		entry.Warn("Authentication failed")
	}
}

// LogServerStatistics logs server-wide statistics
func (l *Logger) LogServerStatistics(sessionsTotal, sessionsActive, bytesRx, bytesTx, filesRx, filesTx int64) {
	l.WithFields(logrus.Fields{
		"sessions_total":  sessionsTotal,
		"sessions_active": sessionsActive,
		"bytes_rx":        bytesRx,
		"bytes_tx":        bytesTx,
		"files_rx":        filesRx,
		"files_tx":        filesTx,
	}).Info("Server statistics")
}