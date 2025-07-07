package jnode.ftn;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for EchoareaLookupResult class
 */
public class EchoareaLookupResultTest {

    @Test
    public void testSuccessResult() {
        // Create a mock echoarea (we'll use null for simplicity)
        EchoareaLookupResult result = EchoareaLookupResult.success(null);
        
        assertTrue(result.isSuccess());
        assertEquals(EchoareaLookupResult.ErrorType.NONE, result.getErrorType());
        assertNull(result.getErrorMessage());
    }

    @Test
    public void testAutoCreateDisabledResult() {
        String areaName = "TEST.AREA";
        EchoareaLookupResult result = EchoareaLookupResult.autoCreateDisabled(areaName);
        
        assertFalse(result.isSuccess());
        assertEquals(EchoareaLookupResult.ErrorType.AUTO_CREATE_DISABLED, result.getErrorType());
        assertNull(result.getEchoarea());
        
        String expectedMessage = "Echoarea 'TEST.AREA' does not exist and automatic echoarea creation is disabled for this link";
        assertEquals(expectedMessage, result.getErrorMessage());
        assertEquals("echoarea does not exist, auto-creation disabled", result.getLogMessage());
    }

    @Test
    public void testLinkNotSubscribedResult() {
        String areaName = "5023/11.ROBOT";
        String linkAddress = "2:5023/24";
        EchoareaLookupResult result = EchoareaLookupResult.linkNotSubscribed(areaName, linkAddress);
        
        assertFalse(result.isSuccess());
        assertEquals(EchoareaLookupResult.ErrorType.LINK_NOT_SUBSCRIBED, result.getErrorType());
        assertNull(result.getEchoarea());
        
        String expectedMessage = "Echoarea '5023/11.ROBOT' exists but link 2:5023/24 is not subscribed to it";
        assertEquals(expectedMessage, result.getErrorMessage());
        assertEquals("echoarea exists but link not subscribed", result.getLogMessage());
    }
}