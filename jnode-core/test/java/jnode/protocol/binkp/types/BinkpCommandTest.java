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

package jnode.protocol.binkp.types;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for BinkpCommand
 * 
 * Tests BinkP command enumeration according to FTS-1026 specification:
 * - M_NUL (0) - Information/Options
 * - M_ADR (1) - Address Declaration
 * - M_PWD (2) - Password
 * - M_FILE (3) - File Header
 * - M_OK (4) - Authentication Success
 * - M_EOB (5) - End of Batch
 * - M_GOT (6) - File Acknowledgment
 * - M_ERR (7) - Fatal Error
 * - M_BSY (8) - Busy/Non-Fatal Error
 * - M_GET (9) - File Request/Resume
 * - M_SKIP (10) - Non-Destructive Skip
 * - M_PROCESS_FILE (99) - jNode-specific command
 * 
 * @author jNode Team
 */
public class BinkpCommandTest {

    @Test
    void testStandardCommandValues() {
        // Test all standard BinkP commands according to FTS-1026
        assertEquals(0, BinkpCommand.M_NUL.getCmd());
        assertEquals(1, BinkpCommand.M_ADR.getCmd());
        assertEquals(2, BinkpCommand.M_PWD.getCmd());
        assertEquals(3, BinkpCommand.M_FILE.getCmd());
        assertEquals(4, BinkpCommand.M_OK.getCmd());
        assertEquals(5, BinkpCommand.M_EOB.getCmd());
        assertEquals(6, BinkpCommand.M_GOT.getCmd());
        assertEquals(7, BinkpCommand.M_ERR.getCmd());
        assertEquals(8, BinkpCommand.M_BSY.getCmd());
        assertEquals(9, BinkpCommand.M_GET.getCmd());
        assertEquals(10, BinkpCommand.M_SKIP.getCmd());
    }

    @Test
    void testJnodeSpecificCommands() {
        // Test jNode-specific commands
        assertEquals(99, BinkpCommand.M_PROCESS_FILE.getCmd());
    }

    @Test
    void testCommandNames() {
        // Test that command names match expected values
        assertEquals("M_NUL", BinkpCommand.M_NUL.name());
        assertEquals("M_ADR", BinkpCommand.M_ADR.name());
        assertEquals("M_PWD", BinkpCommand.M_PWD.name());
        assertEquals("M_FILE", BinkpCommand.M_FILE.name());
        assertEquals("M_OK", BinkpCommand.M_OK.name());
        assertEquals("M_EOB", BinkpCommand.M_EOB.name());
        assertEquals("M_GOT", BinkpCommand.M_GOT.name());
        assertEquals("M_ERR", BinkpCommand.M_ERR.name());
        assertEquals("M_BSY", BinkpCommand.M_BSY.name());
        assertEquals("M_GET", BinkpCommand.M_GET.name());
        assertEquals("M_SKIP", BinkpCommand.M_SKIP.name());
        assertEquals("M_PROCESS_FILE", BinkpCommand.M_PROCESS_FILE.name());
    }

    @Test
    void testToString() {
        // Test toString method
        assertEquals("M_NUL", BinkpCommand.M_NUL.toString());
        assertEquals("M_ADR", BinkpCommand.M_ADR.toString());
        assertEquals("M_PWD", BinkpCommand.M_PWD.toString());
        assertEquals("M_FILE", BinkpCommand.M_FILE.toString());
        assertEquals("M_OK", BinkpCommand.M_OK.toString());
        assertEquals("M_EOB", BinkpCommand.M_EOB.toString());
        assertEquals("M_GOT", BinkpCommand.M_GOT.toString());
        assertEquals("M_ERR", BinkpCommand.M_ERR.toString());
        assertEquals("M_BSY", BinkpCommand.M_BSY.toString());
        assertEquals("M_GET", BinkpCommand.M_GET.toString());
        assertEquals("M_SKIP", BinkpCommand.M_SKIP.toString());
        assertEquals("M_PROCESS_FILE", BinkpCommand.M_PROCESS_FILE.toString());
    }

    @Test
    void testAllCommandsHaveUniqueValues() {
        BinkpCommand[] commands = BinkpCommand.values();
        
        // Check that all commands have unique values
        for (int i = 0; i < commands.length; i++) {
            for (int j = i + 1; j < commands.length; j++) {
                assertNotEquals(commands[i].getCmd(), commands[j].getCmd(),
                               "Commands " + commands[i] + " and " + commands[j] + " have same value");
            }
        }
    }

    @Test
    void testCommandValueRanges() {
        BinkpCommand[] commands = BinkpCommand.values();
        
        // Check that command values are within valid ranges
        for (BinkpCommand cmd : commands) {
            int value = cmd.getCmd();
            assertTrue(value >= 0, "Command value " + value + " is negative");
            assertTrue(value <= 255, "Command value " + value + " exceeds byte range");
        }
    }

    @Test
    void testStandardCommandsInSequence() {
        // Test that standard commands (0-10) are in sequence
        BinkpCommand[] standardCommands = {
            BinkpCommand.M_NUL,   // 0
            BinkpCommand.M_ADR,   // 1
            BinkpCommand.M_PWD,   // 2
            BinkpCommand.M_FILE,  // 3
            BinkpCommand.M_OK,    // 4
            BinkpCommand.M_EOB,   // 5
            BinkpCommand.M_GOT,   // 6
            BinkpCommand.M_ERR,   // 7
            BinkpCommand.M_BSY,   // 8
            BinkpCommand.M_GET,   // 9
            BinkpCommand.M_SKIP   // 10
        };
        
        for (int i = 0; i < standardCommands.length; i++) {
            assertEquals(i, standardCommands[i].getCmd(),
                        "Command at index " + i + " should have value " + i);
        }
    }

    @Test
    void testCommandCount() {
        BinkpCommand[] commands = BinkpCommand.values();
        assertEquals(12, commands.length); // 11 standard + 1 jNode-specific
    }

    @Test
    void testValueOf() {
        // Test valueOf method
        assertEquals(BinkpCommand.M_NUL, BinkpCommand.valueOf("M_NUL"));
        assertEquals(BinkpCommand.M_ADR, BinkpCommand.valueOf("M_ADR"));
        assertEquals(BinkpCommand.M_PWD, BinkpCommand.valueOf("M_PWD"));
        assertEquals(BinkpCommand.M_FILE, BinkpCommand.valueOf("M_FILE"));
        assertEquals(BinkpCommand.M_OK, BinkpCommand.valueOf("M_OK"));
        assertEquals(BinkpCommand.M_EOB, BinkpCommand.valueOf("M_EOB"));
        assertEquals(BinkpCommand.M_GOT, BinkpCommand.valueOf("M_GOT"));
        assertEquals(BinkpCommand.M_ERR, BinkpCommand.valueOf("M_ERR"));
        assertEquals(BinkpCommand.M_BSY, BinkpCommand.valueOf("M_BSY"));
        assertEquals(BinkpCommand.M_GET, BinkpCommand.valueOf("M_GET"));
        assertEquals(BinkpCommand.M_SKIP, BinkpCommand.valueOf("M_SKIP"));
        assertEquals(BinkpCommand.M_PROCESS_FILE, BinkpCommand.valueOf("M_PROCESS_FILE"));
    }

    @Test
    void testValueOfInvalidName() {
        try {
            BinkpCommand.valueOf("INVALID");
            fail("Should throw IllegalArgumentException for invalid name");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    void testValueOfNullName() {
        try {
            BinkpCommand.valueOf(null);
            fail("Should throw NullPointerException for null name");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    void testEnumComparisons() {
        // Test enum comparisons
        assertEquals(BinkpCommand.M_NUL, BinkpCommand.M_NUL);
        assertNotEquals(BinkpCommand.M_NUL, BinkpCommand.M_ADR);
        
        // Test ordinal values
        assertTrue(BinkpCommand.M_NUL.ordinal() < BinkpCommand.M_ADR.ordinal());
        assertTrue(BinkpCommand.M_ADR.ordinal() < BinkpCommand.M_PWD.ordinal());
    }

    @Test
    void testCommandsByUsage() {
        // Test commands by their usage categories according to FTS-1026
        
        // Session setup commands
        BinkpCommand[] sessionSetup = {
            BinkpCommand.M_ADR, BinkpCommand.M_PWD, BinkpCommand.M_OK
        };
        
        // File transfer commands
        BinkpCommand[] fileTransfer = {
            BinkpCommand.M_FILE, BinkpCommand.M_GOT, BinkpCommand.M_GET,
            BinkpCommand.M_SKIP, BinkpCommand.M_EOB
        };
        
        // Error handling commands
        BinkpCommand[] errorHandling = {
            BinkpCommand.M_ERR, BinkpCommand.M_BSY
        };
        
        // Information command (can be used anytime)
        BinkpCommand[] information = {
            BinkpCommand.M_NUL
        };
        
        // Verify all commands are accounted for
        int totalCommands = sessionSetup.length + fileTransfer.length + 
                           errorHandling.length + information.length + 1; // +1 for M_PROCESS_FILE
        assertEquals(BinkpCommand.values().length, totalCommands);
    }

    @Test
    void testCommandSerialization() {
        // Test that commands can be serialized/deserialized consistently
        for (BinkpCommand cmd : BinkpCommand.values()) {
            String name = cmd.name();
            int value = cmd.getCmd();
            
            // Verify that name and value are consistent
            BinkpCommand reconstructed = BinkpCommand.valueOf(name);
            assertEquals(cmd, reconstructed);
            assertEquals(value, reconstructed.getCmd());
        }
    }

    @Test
    void testCommandPurposes() {
        // Test that commands have the expected purposes according to FTS-1026
        
        // M_NUL - Information/Options
        assertTrue(BinkpCommand.M_NUL.toString().contains("NUL"),
                  "M_NUL should be for information");
        
        // M_ADR - Address Declaration
        assertTrue(BinkpCommand.M_ADR.toString().contains("ADR"),
                  "M_ADR should be for addresses");
        
        // M_PWD - Password
        assertTrue(BinkpCommand.M_PWD.toString().contains("PWD"),
                  "M_PWD should be for passwords");
        
        // M_FILE - File Header
        assertTrue(BinkpCommand.M_FILE.toString().contains("FILE"),
                  "M_FILE should be for files");
        
        // M_OK - Authentication Success
        assertTrue(BinkpCommand.M_OK.toString().contains("OK"),
                  "M_OK should be for acknowledgment");
        
        // M_EOB - End of Batch
        assertTrue(BinkpCommand.M_EOB.toString().contains("EOB"),
                  "M_EOB should be for end of batch");
        
        // M_GOT - File Acknowledgment
        assertTrue(BinkpCommand.M_GOT.toString().contains("GOT"),
                  "M_GOT should be for got files");
        
        // M_ERR - Fatal Error
        assertTrue(BinkpCommand.M_ERR.toString().contains("ERR"),
                  "M_ERR should be for errors");
        
        // M_BSY - Busy/Non-Fatal Error
        assertTrue(BinkpCommand.M_BSY.toString().contains("BSY"),
                  "M_BSY should be for busy");
        
        // M_GET - File Request/Resume
        assertTrue(BinkpCommand.M_GET.toString().contains("GET"),
                  "M_GET should be for get requests");
        
        // M_SKIP - Non-Destructive Skip
        assertTrue(BinkpCommand.M_SKIP.toString().contains("SKIP"),
                  "M_SKIP should be for skip");
    }
}