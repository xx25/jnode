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
 * Comprehensive test for Via line parsing from various FTN software packages.
 * Tests the extractAddressFromViaLine method against formats used by:
 * - ParToss
 * - CrashMail
 * - FidoGate 
 * - ifmail
 * - jNode
 * 
 * @author Claude Code
 */
public class FtnSoftwareViaLineTest {
    
    private FtnTosser tosser;
    private Method extractAddressMethod;
    
    @BeforeEach
    void setUp() throws Exception {
        tosser = new FtnTosser();
        
        // Get access to private method for testing
        extractAddressMethod = FtnTosser.class.getDeclaredMethod("extractAddressFromViaLine", String.class);
        extractAddressMethod.setAccessible(true);
    }
    
    /**
     * Test ParToss Via line formats:
     * Format: \1Via ParToss <version> <zone>:<net>/<node>.<point>, <timestamp>
     * Example: \1Via ParToss 1.10.073/ZOO/W32 2:6078/80.0, 25 Jul 25  06:47:20
     */
    @Test
    void testParTossViaLineFormats() throws Exception {
        String[] parTossLines = {
            "\001Via ParToss 1.10.073/ZOO/W32 2:6078/80.0, 25 Jul 25  06:47:20",
            ".Via ParToss 1.43.0/Xen-ix 2:5020/1042.0, 25 Dec 24  12:00:00",
            "\001Via ParToss 1.50.0/Win32 1:123/456.789, 01 Jan 25  00:00:01"
        };
        
        String[] expectedAddresses = {
            "2:6078/80",     // Point 0 normalized away by FtnAddress
            "2:5020/1042",   // Point 0 normalized away by FtnAddress
            "1:123/456.789"  // Point preserved
        };
        
        for (int i = 0; i < parTossLines.length; i++) {
            FtnAddress result = (FtnAddress) extractAddressMethod.invoke(tosser, parTossLines[i]);
            Assertions.assertNotNull(result, "Should parse ParToss format: " + parTossLines[i]);
            Assertions.assertEquals(expectedAddresses[i], result.toString(), 
                "ParToss address mismatch for: " + parTossLines[i]);
        }
    }
    
    /**
     * Test CrashMail Via line formats:
     * Format: ^AVia <address> @YYYYMMDD.HHMMSS CrashMail II/<platform> <version>
     * Example: ^AVia 2:5020/1042 @20250725.143045 CrashMail II/Linux 1.7
     */
    @Test
    void testCrashMailViaLineFormats() throws Exception {
        String[] crashMailLines = {
            "\001Via 2:5020/1042 @20250725.143045 CrashMail II/Linux 1.7",
            "\001Via 1:123/456.789 @20241201.120000 CrashMail II/Win32 1.6",
            "\001Via 3:712/610 @20250101.000001 CrashMail II/OS2 1.8"
        };
        
        String[] expectedAddresses = {
            "2:5020/1042",
            "1:123/456.789", 
            "3:712/610"
        };
        
        for (int i = 0; i < crashMailLines.length; i++) {
            FtnAddress result = (FtnAddress) extractAddressMethod.invoke(tosser, crashMailLines[i]);
            Assertions.assertNotNull(result, "Should parse CrashMail format: " + crashMailLines[i]);
            Assertions.assertEquals(expectedAddresses[i], result.toString(),
                "CrashMail address mismatch for: " + crashMailLines[i]);
        }
    }
    
    /**
     * Test FidoGate Via line formats:
     * Standard Format: ^AVia FIDOGATE/program node, timestamp
     * FTS-5001 Format: ^AVia node @timestamp FIDOGATE/program
     */
    @Test
    void testFidoGateViaLineFormats() throws Exception {
        // Standard format
        String[] fidoGateStandardLines = {
            "\001Via FIDOGATE/ftntoss 2:5020/1042, Mon Jan 15 2024 14:30",
            "\001Via FIDOGATE/rfc2ftn 1:123/456, Fri Jul 25 2025 12:00"
        };
        
        // FTS-5001 format  
        String[] fidoGateFtsLines = {
            "\001Via 2:5020/1042 @20240115.1430 FIDOGATE/ftntoss",
            "\001Via 1:123/456 @20250725.1200 FIDOGATE/rfc2ftn"
        };
        
        String[] expectedAddresses = {
            "2:5020/1042", "1:123/456", "2:5020/1042", "1:123/456"
        };
        
        String[][] allLines = {fidoGateStandardLines, fidoGateFtsLines};
        int addrIndex = 0;
        
        for (String[] lineGroup : allLines) {
            for (String line : lineGroup) {
                FtnAddress result = (FtnAddress) extractAddressMethod.invoke(tosser, line);
                Assertions.assertNotNull(result, "Should parse FidoGate format: " + line);
                Assertions.assertEquals(expectedAddresses[addrIndex], result.toString(),
                    "FidoGate address mismatch for: " + line);
                addrIndex++;
            }
        }
    }
    
    /**
     * Test ifmail Via line formats:
     * Format: ^AVia ifmail <node_address>, <timestamp> (<version>)
     * Example: ^AVia ifmail 2:5020/1042, Mon Jan 15 2024 at 14:30 (3.03)
     */
    @Test
    void testIfmailViaLineFormats() throws Exception {
        String[] ifmailLines = {
            "\001Via ifmail 2:5020/1042, Mon Jan 15 2024 at 14:30 (3.03)",
            "\001Via ifmail 1:123/456.789, Fri Jul 25 2025 at 12:00 (3.04)",
            "\001Via ifmail 3:712/610, Wed Dec 01 2024 at 23:59 (3.05)"
        };
        
        String[] expectedAddresses = {
            "2:5020/1042",
            "1:123/456.789",
            "3:712/610"
        };
        
        for (int i = 0; i < ifmailLines.length; i++) {
            FtnAddress result = (FtnAddress) extractAddressMethod.invoke(tosser, ifmailLines[i]);
            Assertions.assertNotNull(result, "Should parse ifmail format: " + ifmailLines[i]);
            Assertions.assertEquals(expectedAddresses[i], result.toString(),
                "ifmail address mismatch for: " + ifmailLines[i]);
        }
    }
    
    /**
     * Test jNode Via line formats:
     * Format: ^AVia <address> jNode ver <version> <timestamp>
     * Example: ^AVia 2:5020/1042 jNode ver 2.0.1 Fri Nov 06 2014 at 20:17:07
     */
    @Test
    void testJNodeViaLineFormats() throws Exception {
        String[] jNodeLines = {
            "\001Via 2:5020/1042 jNode ver 2.0.1 Fri Nov 06 2014 at 20:17:07",
            "\001Via 1:123/456.789 jNode ver 2.0.6 Mon Jul 25 2025 at 14:30:00",
            "\001Via 3:712/610 jNode ver 2.1.0 Wed Dec 25 2024 at 12:00:00"
        };
        
        String[] expectedAddresses = {
            "2:5020/1042",
            "1:123/456.789", 
            "3:712/610"
        };
        
        for (int i = 0; i < jNodeLines.length; i++) {
            FtnAddress result = (FtnAddress) extractAddressMethod.invoke(tosser, jNodeLines[i]);
            Assertions.assertNotNull(result, "Should parse jNode format: " + jNodeLines[i]);
            Assertions.assertEquals(expectedAddresses[i], result.toString(),
                "jNode address mismatch for: " + jNodeLines[i]);
        }
    }
    
    /**
     * Test edge cases and unusual formats that might occur
     */
    @Test
    void testEdgeCaseViaLineFormats() throws Exception {
        // Mixed case Via keyword - jNode's parser is flexible and finds addresses via regex
        FtnAddress result1 = (FtnAddress) extractAddressMethod.invoke(tosser, "\001via 2:5020/1042 test");
        Assertions.assertNotNull(result1, "jNode parser finds addresses regardless of Via keyword case");
        Assertions.assertEquals("2:5020/1042", result1.toString(), "Should extract address even with lowercase 'via'");
        
        // Multiple addresses in one line - should return first valid one
        FtnAddress result2 = (FtnAddress) extractAddressMethod.invoke(tosser, 
            "\001Via 2:5020/1042 forwarded through 1:123/456 by gateway");
        Assertions.assertNotNull(result2, "Should parse line with multiple addresses");
        Assertions.assertTrue(result2.toString().equals("2:5020/1042") || result2.toString().equals("1:123/456"),
            "Should extract one of the addresses: " + result2.toString());
        
        // Address with domain
        FtnAddress result3 = (FtnAddress) extractAddressMethod.invoke(tosser, 
            "\001Via 2:5020/1042@fidonet SomeMailer v1.0");
        Assertions.assertNotNull(result3, "Should parse address with domain");
        Assertions.assertEquals("2:5020/1042", result3.toString(), "Should strip domain from address");
        
        // Very long Via line with address buried in middle
        FtnAddress result4 = (FtnAddress) extractAddressMethod.invoke(tosser,
            "\001Via SuperLongMailerNameWithVersionInfo/Platform 2:5020/1042 on very long timestamp with lots of info");
        Assertions.assertNotNull(result4, "Should find address in long line");
        Assertions.assertEquals("2:5020/1042", result4.toString(), "Should extract address from long line");
    }
    
    /**
     * Test problematic formats that should fail gracefully
     */
    @Test
    void testProblematicViaLineFormats() throws Exception {
        String[] problematicLines = {
            "",                                    // Empty line
            "\001Via",                            // Just Via keyword
            "\001Via SomeMailer v1.0",           // No address
            "\001Via 999:999999/999999",         // Invalid address ranges
            "Via 2:5020/1042 no control char",   // Missing control character
            "\001Via invalid:address/format"      // Malformed address
        };
        
        for (String line : problematicLines) {
            FtnAddress result = (FtnAddress) extractAddressMethod.invoke(tosser, line);
            Assertions.assertNull(result, "Should return null for problematic line: " + line);
        }
    }
    
    /**
     * Test that the regex pattern handles boundary conditions properly
     */
    @Test
    void testAddressPatternBoundaryConditions() throws Exception {
        // Address at start of line (after Via)
        FtnAddress result1 = (FtnAddress) extractAddressMethod.invoke(tosser, "\001Via 2:5020/1042");
        Assertions.assertNotNull(result1, "Should parse address at line start");
        
        // Address at end of line
        FtnAddress result2 = (FtnAddress) extractAddressMethod.invoke(tosser, "\001Via SomeMailer 2:5020/1042");
        Assertions.assertNotNull(result2, "Should parse address at line end");
        
        // Address followed by comma (ParToss style)
        FtnAddress result3 = (FtnAddress) extractAddressMethod.invoke(tosser, "\001Via Program 2:5020/1042, timestamp");
        Assertions.assertNotNull(result3, "Should parse address followed by comma");
        
        // Address followed by space and more text
        FtnAddress result4 = (FtnAddress) extractAddressMethod.invoke(tosser, "\001Via 2:5020/1042 more text");
        Assertions.assertNotNull(result4, "Should parse address followed by space");
    }
}