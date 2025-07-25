/*
 * Licensed to the jNode FTN Platform Development Team (jNode Team)
 * under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for 
 * additional information regarding copyright ownership.  
 * The jNode Team licenses this file to you under the 
 * Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package jnode.ftn.tosser;

import jnode.ftn.types.FtnAddress;
import org.junit.jupiter.api.*;
import java.lang.reflect.Method;

/**
 * Unit tests for Via line parsing functionality in FtnTosser.
 * Tests the extractAddressFromViaLine method to ensure proper parsing
 * of different Via line formats from various FTN software.
 * 
 * @author Claude Code
 */
public class ViaLineParsingTest {
    
    private FtnTosser tosser;
    private Method extractAddressMethod;
    
    @BeforeEach
    void setUp() throws Exception {
        tosser = new FtnTosser();
        
        // Get access to private method for testing
        extractAddressMethod = FtnTosser.class.getDeclaredMethod("extractAddressFromViaLine", String.class);
        extractAddressMethod.setAccessible(true);
    }
    
    @Test
    void testStandardViaLineFormat() throws Exception {
        // Test standard jNode Via line format
        String viaLine = "\001Via 2:5020/1042 jNode ver 2.0.1 Fri Nov 06 2014 at 20:17:07";
        
        FtnAddress result = (FtnAddress) extractAddressMethod.invoke(tosser, viaLine);
        
        Assertions.assertNotNull(result, "Should parse standard Via line");
        Assertions.assertEquals("2:5020/1042", result.toString(), "Should extract correct address");
    }
    
    @Test
    void testParTossViaLineFormat() throws Exception {
        // Test ParToss Via line format (the problematic one from the log)
        String viaLine = ".Via ParToss 1.10.073/ZOO/W32 2:6078/80.0, 25 Jul 25  06:47:20";
        
        FtnAddress result = (FtnAddress) extractAddressMethod.invoke(tosser, viaLine);
        
        Assertions.assertNotNull(result, "Should parse ParToss Via line");
        // FtnAddress normalizes 2:6078/80.0 to 2:6078/80 (point 0 is not shown)
        Assertions.assertEquals("2:6078/80", result.toString(), "Should extract correct address from ParToss format");
    }
    
    @Test
    void testViaLineWithPointAddress() throws Exception {
        // Test Via line with point address
        String viaLine = "\001Via 2:5020/1042.1 SomeMailer v1.0 Mon Jan 01 2024 12:00:00";
        
        FtnAddress result = (FtnAddress) extractAddressMethod.invoke(tosser, viaLine);
        
        Assertions.assertNotNull(result, "Should parse Via line with point");
        Assertions.assertEquals("2:5020/1042.1", result.toString(), "Should extract point address correctly");
    }
    
    @Test
    void testViaLineWithDomainAddress() throws Exception {
        // Test Via line with domain address
        String viaLine = "\001Via 2:5020/1042@fidonet SomeMailer v1.0 Mon Jan 01 2024 12:00:00";
        
        FtnAddress result = (FtnAddress) extractAddressMethod.invoke(tosser, viaLine);
        
        Assertions.assertNotNull(result, "Should parse Via line with domain");
        // FtnAddress strips domain info, only keeps zone:net/node
        Assertions.assertEquals("2:5020/1042", result.toString(), "Should extract address correctly (domain stripped by FtnAddress)");
    }
    
    @Test
    void testViaLineAddressInMiddle() throws Exception {
        // Test Via line with address somewhere in the middle
        String viaLine = ".Via SomeProgram v2.1/OS2 2:1234/5678 on 25-Jul-25 12:34:56";
        
        FtnAddress result = (FtnAddress) extractAddressMethod.invoke(tosser, viaLine);
        
        Assertions.assertNotNull(result, "Should parse Via line with address in middle");
        Assertions.assertEquals("2:1234/5678", result.toString(), "Should find address in middle of line");
    }
    
    @Test
    void testViaLineWithMultipleAddresses() throws Exception {
        // Test Via line with multiple addresses (should return first valid one)
        String viaLine = "\001Via 2:5020/1042 forwarded via 2:5020/1 SomeMailer v1.0";
        
        FtnAddress result = (FtnAddress) extractAddressMethod.invoke(tosser, viaLine);
        
        Assertions.assertNotNull(result, "Should parse Via line with multiple addresses");
        // Should return the first parseable address
        Assertions.assertTrue(result.toString().equals("2:5020/1042") || result.toString().equals("2:5020/1"), 
            "Should extract one of the addresses: " + result.toString());
    }
    
    @Test
    void testInvalidViaLine() throws Exception {
        // Test Via line with no valid addresses
        String viaLine = "\001Via SomeProgram v1.0 no address here";
        
        FtnAddress result = (FtnAddress) extractAddressMethod.invoke(tosser, viaLine);
        
        Assertions.assertNull(result, "Should return null for Via line with no valid addresses");
    }
    
    @Test
    void testEmptyViaLine() throws Exception {
        // Test empty Via line
        String viaLine = "";
        
        FtnAddress result = (FtnAddress) extractAddressMethod.invoke(tosser, viaLine);
        
        Assertions.assertNull(result, "Should return null for empty Via line");
    }
    
    @Test
    void testViaLineWithJustViaKeyword() throws Exception {
        // Test Via line with just the Via keyword
        String viaLine = "\001Via";
        
        FtnAddress result = (FtnAddress) extractAddressMethod.invoke(tosser, viaLine);
        
        Assertions.assertNull(result, "Should return null for Via line with just keyword");
    }
    
    @Test
    void testDotViaVariant() throws Exception {
        // Test .Via variant (used by some software instead of \001Via)
        String viaLine = ".Via 2:9999/1234 TestMailer v1.0 25-Jul-25 12:00:00";
        
        FtnAddress result = (FtnAddress) extractAddressMethod.invoke(tosser, viaLine);
        
        Assertions.assertNotNull(result, "Should parse .Via variant");
        Assertions.assertEquals("2:9999/1234", result.toString(), "Should extract address from .Via format");
    }
    
    @Test
    void testViaLineWithCommaAfterAddress() throws Exception {
        // Test Via line where address is followed by comma (like ParToss format)
        String viaLine = ".Via Program 2:1234/5678, 25 Jul 25 12:00:00";
        
        FtnAddress result = (FtnAddress) extractAddressMethod.invoke(tosser, viaLine);
        
        Assertions.assertNotNull(result, "Should parse Via line with comma after address");
        Assertions.assertEquals("2:1234/5678", result.toString(), "Should extract address before comma");
    }
}