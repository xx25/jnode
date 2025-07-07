package jnode.ftn;

import jnode.dto.Echoarea;

/**
 * Result class for echoarea lookup operations that provides detailed error information
 * when an echoarea cannot be accessed by a specific link.
 */
public class EchoareaLookupResult {
    
    public enum ErrorType {
        NONE,                    // Success - echoarea found and accessible
        AREA_NOT_EXISTS,         // Echoarea doesn't exist
        AUTO_CREATE_DISABLED,    // Echoarea doesn't exist and auto-creation disabled
        LINK_NOT_SUBSCRIBED     // Echoarea exists but link not subscribed
    }
    
    private final Echoarea echoarea;
    private final ErrorType errorType;
    private final String errorMessage;
    
    private EchoareaLookupResult(Echoarea echoarea, ErrorType errorType, String errorMessage) {
        this.echoarea = echoarea;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }
    
    /**
     * Create a successful result
     */
    public static EchoareaLookupResult success(Echoarea echoarea) {
        return new EchoareaLookupResult(echoarea, ErrorType.NONE, null);
    }
    
    /**
     * Create an error result for non-existent echoarea with auto-creation disabled
     */
    public static EchoareaLookupResult autoCreateDisabled(String areaName) {
        return new EchoareaLookupResult(null, ErrorType.AUTO_CREATE_DISABLED, 
            "Echoarea '" + areaName + "' does not exist and automatic echoarea creation is disabled for this link");
    }
    
    /**
     * Create an error result for existing echoarea where link is not subscribed
     */
    public static EchoareaLookupResult linkNotSubscribed(String areaName, String linkAddress) {
        return new EchoareaLookupResult(null, ErrorType.LINK_NOT_SUBSCRIBED,
            "Echoarea '" + areaName + "' exists but link " + linkAddress + " is not subscribed to it");
    }
    
    /**
     * @return the echoarea if lookup was successful, null otherwise
     */
    public Echoarea getEchoarea() {
        return echoarea;
    }
    
    /**
     * @return true if the lookup was successful
     */
    public boolean isSuccess() {
        return errorType == ErrorType.NONE;
    }
    
    /**
     * @return the error type if lookup failed
     */
    public ErrorType getErrorType() {
        return errorType;
    }
    
    /**
     * @return detailed error message if lookup failed
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * @return user-friendly error description for logging
     */
    public String getLogMessage() {
        switch (errorType) {
            case AUTO_CREATE_DISABLED:
                return "echoarea does not exist, auto-creation disabled";
            case LINK_NOT_SUBSCRIBED:
                return "echoarea exists but link not subscribed";
            default:
                return "unknown error";
        }
    }
}