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
 * Unit tests for BinkpFrame
 * 
 * Tests BinkP frame construction and processing according to FTS-1026 specification:
 * - Frame format: 2-byte header + data (max 32767 bytes)
 * - Header format: T|SIZE (1 bit + 15 bits)
 * - T=1 for command frames, T=0 for data frames
 * - Command frames: first data byte = command ID
 * - Data frames: raw file data
 * 
 * @author jNode Team
 */
public class BinkpFrameTest {

    @Test
    void testCommandFrameWithoutArgument() {
        BinkpFrame frame = new BinkpFrame(BinkpCommand.M_OK);
        
        assertNotNull(frame);
        assertEquals(BinkpCommand.M_OK, frame.getCommand());
        assertNull(frame.getArg());
        
        byte[] bytes = frame.getBytes();
        assertNotNull(bytes);
        assertEquals(3, bytes.length); // 2-byte header + 1 command byte
        
        // Check header: T=1 (command), SIZE=1
        int header = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
        assertEquals(0x8001, header); // T=1, SIZE=1
        
        // Check command byte
        assertEquals(BinkpCommand.M_OK.getCmd(), bytes[2] & 0xFF);
    }

    @Test
    void testCommandFrameWithArgument() {
        String arg = "test argument";
        BinkpFrame frame = new BinkpFrame(BinkpCommand.M_NUL, arg);
        
        assertNotNull(frame);
        assertEquals(BinkpCommand.M_NUL, frame.getCommand());
        assertEquals(arg, frame.getArg());
        
        byte[] bytes = frame.getBytes();
        assertNotNull(bytes);
        assertEquals(16, bytes.length); // 2-byte header + 1 command byte + 13 arg bytes
        
        // Check header: T=1 (command), SIZE=14 (1 command + 13 arg)
        int header = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
        assertEquals(0x800E, header); // T=1, SIZE=14
        
        // Check command byte
        assertEquals(BinkpCommand.M_NUL.getCmd(), bytes[2] & 0xFF);
        
        // Check argument
        String recoveredArg = new String(bytes, 3, arg.length());
        assertEquals(arg, recoveredArg);
    }

    @Test
    void testDataFrameWithByteArray() {
        byte[] data = {0x01, 0x02, 0x03, 0x04, 0x05};
        BinkpFrame frame = new BinkpFrame(data);
        
        assertNotNull(frame);
        assertNull(frame.getCommand());
        assertNull(frame.getArg());
        
        byte[] bytes = frame.getBytes();
        assertNotNull(bytes);
        assertEquals(7, bytes.length); // 2-byte header + 5 data bytes
        
        // Check header: T=0 (data), SIZE=5
        int header = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
        assertEquals(0x0005, header); // T=0, SIZE=5
        
        // Check data
        for (int i = 0; i < data.length; i++) {
            assertEquals(data[i], bytes[i + 2]);
        }
    }

    @Test
    void testDataFrameWithPartialArray() {
        byte[] data = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
        int len = 5;
        BinkpFrame frame = new BinkpFrame(data, len);
        
        assertNotNull(frame);
        assertNull(frame.getCommand());
        assertNull(frame.getArg());
        
        byte[] bytes = frame.getBytes();
        assertNotNull(bytes);
        assertEquals(7, bytes.length); // 2-byte header + 5 data bytes
        
        // Check header: T=0 (data), SIZE=5
        int header = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
        assertEquals(0x0005, header); // T=0, SIZE=5
        
        // Check only first 5 bytes of data
        for (int i = 0; i < len; i++) {
            assertEquals(data[i], bytes[i + 2]);
        }
    }

    @Test
    void testEmptyDataFrame() {
        byte[] data = {};
        BinkpFrame frame = new BinkpFrame(data);
        
        assertNotNull(frame);
        assertNull(frame.getCommand());
        assertNull(frame.getArg());
        
        byte[] bytes = frame.getBytes();
        assertNotNull(bytes);
        assertEquals(2, bytes.length); // 2-byte header only
        
        // Check header: T=0 (data), SIZE=0
        int header = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
        assertEquals(0x0000, header); // T=0, SIZE=0
    }

    @Test
    void testMaxSizeDataFrame() {
        byte[] data = new byte[32767]; // Maximum allowed size
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        
        BinkpFrame frame = new BinkpFrame(data);
        
        assertNotNull(frame);
        assertNull(frame.getCommand());
        
        byte[] bytes = frame.getBytes();
        assertNotNull(bytes);
        assertEquals(32769, bytes.length); // 2-byte header + 32767 data bytes
        
        // Check header: T=0 (data), SIZE=32767
        int header = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
        assertEquals(0x7FFF, header); // T=0, SIZE=32767
    }

    @Test
    void testCommandFrameWithEmptyArgument() {
        BinkpFrame frame = new BinkpFrame(BinkpCommand.M_EOB, "");
        
        assertNotNull(frame);
        assertEquals(BinkpCommand.M_EOB, frame.getCommand());
        assertEquals("", frame.getArg());
        
        byte[] bytes = frame.getBytes();
        assertNotNull(bytes);
        assertEquals(3, bytes.length); // 2-byte header + 1 command byte
        
        // Check header: T=1 (command), SIZE=1
        int header = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
        assertEquals(0x8001, header); // T=1, SIZE=1
        
        // Check command byte
        assertEquals(BinkpCommand.M_EOB.getCmd(), bytes[2] & 0xFF);
    }

    @Test
    void testLongArgumentFrame() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("A");
        }
        String longArg = sb.toString();
        
        BinkpFrame frame = new BinkpFrame(BinkpCommand.M_NUL, longArg);
        
        assertNotNull(frame);
        assertEquals(BinkpCommand.M_NUL, frame.getCommand());
        assertEquals(longArg, frame.getArg());
        
        byte[] bytes = frame.getBytes();
        assertNotNull(bytes);
        assertEquals(1003, bytes.length); // 2-byte header + 1 command byte + 1000 arg bytes
        
        // Check header: T=1 (command), SIZE=1001
        int header = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
        assertEquals(0x83E9, header); // T=1, SIZE=1001
        
        // Check command byte
        assertEquals(BinkpCommand.M_NUL.getCmd(), bytes[2] & 0xFF);
        
        // Check argument
        String recoveredArg = new String(bytes, 3, longArg.length());
        assertEquals(longArg, recoveredArg);
    }

    @Test
    void testToStringCommand() {
        BinkpFrame frame = new BinkpFrame(BinkpCommand.M_ADR, "2:5047/999");
        String str = frame.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("M_ADR"));
        assertTrue(str.contains("2:5047/999"));
        assertTrue(str.startsWith("[ "));
        assertTrue(str.endsWith(" ]"));
    }

    @Test
    void testToStringData() {
        byte[] data = {0x01, 0x02, 0x03, 0x04, 0x05};
        BinkpFrame frame = new BinkpFrame(data);
        String str = frame.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("DATA frame"));
        assertTrue(str.contains("size=5"));
        assertTrue(str.contains("bytes"));
        assertTrue(str.startsWith("[ "));
        assertTrue(str.endsWith(" ]"));
    }

    @Test
    void testToStringNullCommand() {
        BinkpFrame frame = new BinkpFrame(BinkpCommand.M_OK, null);
        String str = frame.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("M_OK"));
        assertTrue(str.contains("null"));
    }

    @Test
    void testGetBytesNullFrame() {
        // Test edge case handling
        BinkpFrame frame = new BinkpFrame(new byte[0]);
        byte[] bytes = frame.getBytes();
        
        assertNotNull(bytes);
        assertTrue(bytes.length >= 2); // Should at least have header
    }

    @Test
    void testNullDataHandling() {
        // Test with null array - should not crash
        try {
            BinkpFrame frame = new BinkpFrame((byte[]) null);
            fail("Should throw exception for null data");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    void testSpecialCharactersInArgument() {
        String arg = "test\nwith\ttabs\rand\0nulls";
        BinkpFrame frame = new BinkpFrame(BinkpCommand.M_NUL, arg);
        
        assertNotNull(frame);
        assertEquals(BinkpCommand.M_NUL, frame.getCommand());
        assertEquals(arg, frame.getArg());
        
        byte[] bytes = frame.getBytes();
        assertNotNull(bytes);
        
        // Check that special characters are preserved
        String recoveredArg = new String(bytes, 3, arg.length());
        assertEquals(arg, recoveredArg);
    }

    @Test
    void testUnicodeInArgument() {
        String arg = "тест with unicode ñ and émojis 🚀";
        BinkpFrame frame = new BinkpFrame(BinkpCommand.M_NUL, arg);
        
        assertNotNull(frame);
        assertEquals(BinkpCommand.M_NUL, frame.getCommand());
        assertEquals(arg, frame.getArg());
        
        byte[] bytes = frame.getBytes();
        assertNotNull(bytes);
        
        // Check that unicode is preserved (depends on platform encoding)
        String recoveredArg = new String(bytes, 3, arg.getBytes().length);
        assertEquals(arg, recoveredArg);
    }

    @Test
    void testAllCommandTypes() {
        BinkpCommand[] commands = {
            BinkpCommand.M_NUL, BinkpCommand.M_ADR, BinkpCommand.M_PWD,
            BinkpCommand.M_FILE, BinkpCommand.M_OK, BinkpCommand.M_EOB,
            BinkpCommand.M_GOT, BinkpCommand.M_ERR, BinkpCommand.M_BSY,
            BinkpCommand.M_GET, BinkpCommand.M_SKIP, BinkpCommand.M_PROCESS_FILE
        };
        
        for (BinkpCommand cmd : commands) {
            BinkpFrame frame = new BinkpFrame(cmd);
            assertEquals(cmd, frame.getCommand());
            
            byte[] bytes = frame.getBytes();
            assertNotNull(bytes);
            assertEquals(3, bytes.length); // 2-byte header + 1 command byte
            
            // Check T bit is set
            int header = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
            assertEquals(0x8001, header & 0x8001); // T=1, SIZE=1
            
            // Check command byte
            assertEquals(cmd.getCmd(), bytes[2] & 0xFF);
        }
    }

    @Test
    void testFrameSizeOverflowProtection() {
        // Test that oversized data frames are rejected
        try {
            byte[] oversizedData = new byte[32768]; // One byte over limit
            BinkpFrame frame = new BinkpFrame(oversizedData);
            fail("Should throw IllegalArgumentException for oversized frame");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("exceeds maximum allowed size"));
            assertTrue(e.getMessage().contains("32767"));
        }
    }

    @Test
    void testFrameSizeOverflowProtectionWithCommand() {
        // Test that oversized command frames are rejected
        try {
            StringBuilder hugeName = new StringBuilder();
            for (int i = 0; i < 32768; i++) {
                hugeName.append("A");
            }
            BinkpFrame frame = new BinkpFrame(BinkpCommand.M_NUL, hugeName.toString());
            fail("Should throw IllegalArgumentException for oversized command frame");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("exceeds maximum allowed size"));
            assertTrue(e.getMessage().contains("32767"));
        }
    }
}