package jnode.protocol.binkp;

import jnode.logger.Logger;

/**
 * Helper class to enable detailed Binkp protocol debugging
 */
public class BinkpDebugHelper {
    
    public static void enableVerboseLogging() {
        // Set global log level to maximum verbosity (LOG_L5)
        Logger.Loglevel = Logger.LOG_L5;
        
        System.out.println("=== Binkp verbose logging enabled ===");
        System.out.println("Global log level set to LOG_L5 (maximum verbosity)");
        System.out.println("Look for log entries starting with '===' for detailed protocol trace");
    }
    
    public static void printDiagnostics() {
        System.out.println("\n=== Binkp Diagnostics ===");
        System.out.println("1. Check if M_FILE is sent but no data frames follow");
        System.out.println("2. Verify currentInputStream is properly initialized");
        System.out.println("3. Look for 'readFrame returned null' messages");
        System.out.println("4. Check 'Available bytes in stream' values");
        System.out.println("5. Verify frame sizes match expected file sizes");
        System.out.println("6. Monitor '=== Sending data frame' vs '=== Frame sent successfully'");
        System.out.println("7. Watch for input stream availability vs actual bytes read");
        System.out.println("========================\n");
    }
    
    public static void setLogLevel(int level) {
        Logger.Loglevel = level;
        System.out.println("=== Log level set to: " + level + " ===");
    }
    
    public static void enableMaxVerbosity() {
        setLogLevel(Logger.LOG_L5);
    }
    
    public static void enableModerateVerbosity() {
        setLogLevel(Logger.LOG_L4);
    }
}