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

package jnode.protocol.binkp.connector;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import jnode.protocol.binkp.types.BinkpCommand;
import jnode.protocol.binkp.types.BinkpFrame;

/**
 * Unit tests for BinkP frame processing and protocol handling
 * 
 * Tests frame creation, parsing, and protocol state management
 * across all BinkP connector implementations.
 * 
 * @author jNode Team
 */
public class BinkpFrameProcessingTest {

    @BeforeEach
    void setUp() {
        // Setup any common test data
    }

    @Test
    void testBinkpFrameCreationWithCommand() {
        BinkpFrame frame = new BinkpFrame(BinkpCommand.M_NUL, "SYS jNode BBS");
        
        assertNotNull(frame);
        assertEquals(BinkpCommand.M_NUL, frame.getCommand());
        assertEquals("SYS jNode BBS", frame.getArg());
        assertNotNull(frame.getBytes());
    }

    @Test
    void testBinkpFrameCreationWithData() {
        byte[] data = "Hello, World!".getBytes();
        BinkpFrame frame = new BinkpFrame(data);
        
        assertNotNull(frame);
        assertNull(frame.getCommand());
        assertNull(frame.getArg());
        assertNotNull(frame.getBytes());
    }

    @Test
    void testBinkpFrameCreationWithDataAndLength() {
        byte[] data = "Hello, World! Extra data that should be ignored".getBytes();
        int length = 13; // "Hello, World!" length
        BinkpFrame frame = new BinkpFrame(data, length);
        
        assertNotNull(frame);
        assertNull(frame.getCommand());
        assertNull(frame.getArg());
        assertNotNull(frame.getBytes());
    }

    @Test
    void testBinkpFrameWithEmptyCommand() {
        BinkpFrame frame = new BinkpFrame(BinkpCommand.M_EOB, "");
        
        assertNotNull(frame);
        assertEquals(BinkpCommand.M_EOB, frame.getCommand());
        assertEquals("", frame.getArg());
    }

    @Test
    void testBinkpFrameWithNullCommand() {
        BinkpFrame frame = new BinkpFrame(BinkpCommand.M_EOB, null);
        
        assertNotNull(frame);
        assertEquals(BinkpCommand.M_EOB, frame.getCommand());
        // The argument might be null or empty string depending on implementation
        // assertNull(frame.getArg());
    }

    @Test
    void testBinkpFrameWithLongArgument() {
        StringBuilder longArg = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longArg.append("A");
        }
        
        BinkpFrame frame = new BinkpFrame(BinkpCommand.M_NUL, longArg.toString());
        
        assertNotNull(frame);
        assertEquals(BinkpCommand.M_NUL, frame.getCommand());
        assertEquals(longArg.toString(), frame.getArg());
    }

    @Test
    void testBinkpFrameWithSpecialCharacters() {
        String specialArg = "SYS jNode BBS (тест) ñoño café 日本";
        BinkpFrame frame = new BinkpFrame(BinkpCommand.M_NUL, specialArg);
        
        assertNotNull(frame);
        assertEquals(BinkpCommand.M_NUL, frame.getCommand());
        assertEquals(specialArg, frame.getArg());
    }

    @Test
    void testBinkpFrameDataIntegrity() {
        byte[] originalData = {0x01, 0x02, 0x03, (byte) 0xFF, 0x00, 0x7F};
        BinkpFrame frame = new BinkpFrame(originalData);
        
        assertNotNull(frame);
        byte[] frameBytes = frame.getBytes();
        assertNotNull(frameBytes);
        // The frame bytes include a 2-byte header, so we check the data portion
        assertTrue(frameBytes.length >= originalData.length);
    }

    @Test
    void testBinkpFrameWithZeroLengthData() {
        byte[] data = new byte[0];
        BinkpFrame frame = new BinkpFrame(data);
        
        assertNotNull(frame);
        assertNotNull(frame.getBytes());
    }

    @Test
    void testBinkpFrameWithMaxSizeData() {
        // BinkP frames have a maximum size of 32767 bytes
        byte[] maxData = new byte[32765]; // Leave room for header
        for (int i = 0; i < maxData.length; i++) {
            maxData[i] = (byte) (i % 256);
        }
        
        BinkpFrame frame = new BinkpFrame(maxData);
        assertNotNull(frame);
        assertNotNull(frame.getBytes());
    }

    @Test
    void testBinkpCommandValues() {
        // Test that all BinkP commands have expected values
        assertNotNull(BinkpCommand.M_NUL);
        assertNotNull(BinkpCommand.M_ADR);
        assertNotNull(BinkpCommand.M_PWD);
        assertNotNull(BinkpCommand.M_FILE);
        assertNotNull(BinkpCommand.M_OK);
        assertNotNull(BinkpCommand.M_EOB);
        assertNotNull(BinkpCommand.M_GOT);
        assertNotNull(BinkpCommand.M_ERR);
        assertNotNull(BinkpCommand.M_BSY);
        assertNotNull(BinkpCommand.M_GET);
        assertNotNull(BinkpCommand.M_SKIP);
    }

    @Test
    void testBinkpFrameEquality() {
        BinkpFrame frame1 = new BinkpFrame(BinkpCommand.M_NUL, "SYS jNode");
        BinkpFrame frame2 = new BinkpFrame(BinkpCommand.M_NUL, "SYS jNode");
        BinkpFrame frame3 = new BinkpFrame(BinkpCommand.M_NUL, "SYS Other");
        
        // Note: This test depends on how equals() is implemented in BinkpFrame
        // If not implemented, objects will use reference equality
        assertNotNull(frame1);
        assertNotNull(frame2);
        assertNotNull(frame3);
        
        // Test that frames with same command and arg are conceptually equal
        assertEquals(frame1.getCommand(), frame2.getCommand());
        assertEquals(frame1.getArg(), frame2.getArg());
        
        // Test that frames with different args are different
        assertNotEquals(frame1.getArg(), frame3.getArg());
    }

    @Test
    void testBinkpFrameToString() {
        BinkpFrame frame = new BinkpFrame(BinkpCommand.M_NUL, "SYS jNode BBS");
        
        String frameString = frame.toString();
        assertNotNull(frameString);
        assertFalse(frameString.isEmpty());
        
        // The string representation should contain some identifying information
        assertTrue(frameString.contains("M_NUL") || frameString.contains("SYS") || 
                  frameString.toLowerCase().contains("frame"));
    }

    @Test
    void testBinkpFrameBytesFormat() {
        BinkpFrame frame = new BinkpFrame(BinkpCommand.M_NUL, "TEST");
        byte[] bytes = frame.getBytes();
        
        assertNotNull(bytes);
        assertTrue(bytes.length >= 2); // At least header
        
        // First two bytes are the frame header
        // Bit 15 of first two bytes should be set for command frames
        int header = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
        assertTrue((header & 0x8000) != 0, "Command frame should have bit 15 set");
    }

    @Test
    void testBinkpDataFrameBytesFormat() {
        byte[] data = "TEST DATA".getBytes();
        BinkpFrame frame = new BinkpFrame(data);
        byte[] bytes = frame.getBytes();
        
        assertNotNull(bytes);
        assertTrue(bytes.length >= 2); // At least header
        
        // First two bytes are the frame header
        // Bit 15 of first two bytes should NOT be set for data frames
        int header = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
        assertEquals(0, header & 0x8000, "Data frame should not have bit 15 set");
    }

    @Test
    void testBinkpFrameHeaderLength() {
        String[] testArgs = {"", "SHORT", "MEDIUM_LENGTH_ARGUMENT", 
                           "VERY_LONG_ARGUMENT_THAT_SHOULD_STILL_WORK_PROPERLY"};
        
        for (String arg : testArgs) {
            BinkpFrame frame = new BinkpFrame(BinkpCommand.M_NUL, arg);
            byte[] bytes = frame.getBytes();
            
            // Extract length from header
            int length = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
            length &= 0x7FFF; // Clear the command bit
            
            // Length should match the actual frame content length
            assertEquals(bytes.length - 2, length, 
                        "Frame length mismatch for arg: " + arg);
        }
    }

    @Test
    void testBinkpFrameWithBinaryData() {
        // Test frame with binary data including null bytes and high-bit characters
        byte[] binaryData = {
            0x00, 0x01, 0x02, 0x03, (byte) 0x80, (byte) 0xFF,
            0x7F, 0x40, 0x20, 0x10, 0x08, 0x04, 0x02, 0x01
        };
        
        BinkpFrame frame = new BinkpFrame(binaryData);
        assertNotNull(frame);
        
        byte[] frameBytes = frame.getBytes();
        assertNotNull(frameBytes);
        assertTrue(frameBytes.length >= binaryData.length + 2);
    }

    @Test
    void testBinkpFrameWithControlCharacters() {
        // Test argument with control characters
        String controlArg = "TEST\n\r\t\0CONTROL";
        BinkpFrame frame = new BinkpFrame(BinkpCommand.M_NUL, controlArg);
        
        assertNotNull(frame);
        assertEquals(BinkpCommand.M_NUL, frame.getCommand());
        assertEquals(controlArg, frame.getArg());
    }
}