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

import jnode.dto.Link;
import jnode.ftn.tosser.FtnTosser;
import jnode.main.MainHandler;
import jnode.protocol.io.Message;
import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/**
 * Basic unit tests for FtnTosser that don't require database initialization.
 * Following CLAUDE.md guidelines to focus on testable functionality.
 * 
 * Note: Disabled due to database initialization issues. Use FtnTosserMinimalTest instead.
 * 
 * @author Claude Code
 */
@Disabled("Database initialization causes System.exit() in CI environment - use FtnTosserMinimalTest instead")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FtnTosserBasicTest {
    
    private Path tempDir;
    private FtnTosser tosser;
    
    @BeforeAll
    void setupTestEnvironment() throws Exception {
        // Create temporary directory for test files
        tempDir = Files.createTempDirectory("ftn-tosser-basic-test");
        
        // Setup basic configuration
        setupBasicConfiguration();
        
        // Initialize test objects
        tosser = new FtnTosser();
    }
    
    @AfterAll
    void cleanupTestEnvironment() throws Exception {
        // Clean up temporary directory
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }
    
    private void setupBasicConfiguration() throws Exception {
        // Set up minimal test properties
        Properties testProps = new Properties();
        testProps.setProperty("ftn.primary", "2:9999/9999");
        testProps.setProperty("station.name", "Test jNode");
        testProps.setProperty("sysop.name", "Test Sysop");
        testProps.setProperty("ftn.inbound", tempDir.toString() + "/inbound");
        testProps.setProperty("ftn.outbound", tempDir.toString() + "/outbound");
        testProps.setProperty("ftn.temp", tempDir.toString() + "/temp");
        testProps.setProperty("binkp.inbound", tempDir.toString() + "/inbound");
        testProps.setProperty("binkp.outbound", tempDir.toString() + "/outbound");
        testProps.setProperty("fileecho.path", tempDir.toString() + "/fileecho");
        
        // Initialize MainHandler with test configuration
        new MainHandler(testProps);
        
        // Create required directories
        Files.createDirectories(tempDir.resolve("inbound"));
        Files.createDirectories(tempDir.resolve("outbound"));
        Files.createDirectories(tempDir.resolve("temp"));
        Files.createDirectories(tempDir.resolve("fileecho"));
    }
    
    @Test
    void testTossIncomingWithNullMessage() {
        // Test null message handling
        int result = FtnTosser.tossIncoming(null);
        Assertions.assertEquals(0, result, "Null message should return 0");
    }
    
    @Test
    void testTossIncomingWithInvalidFile() throws Exception {
        // Create corrupted/invalid file with proper FTN naming pattern
        File invalidFile = tempDir.resolve("cafebabe.pkt").toFile();
        try (FileOutputStream fos = new FileOutputStream(invalidFile)) {
            fos.write("Invalid packet data that will cause FtnPkt.unpack() to fail".getBytes());
        }
        
        Message message = new Message(invalidFile);
        
        // Process the invalid message
        int result = FtnTosser.tossIncoming(message);
        
        // tossIncoming() only validates filename patterns and copies files
        // Actual packet validation happens later in tossInboundDirectory()
        // So invalid packet content should still return 0 (success) from tossIncoming()
        Assertions.assertEquals(0, result, "tossIncoming should return 0 for file operations success");
    }
    
    @Test
    void testRunningStateInitiallyFalse() {
        // Initially not running
        Assertions.assertFalse(tosser.isRunning(), "Tosser should not be running initially");
    }
    
    @Test
    void testRunningStateAfterEnd() {
        // Call end() method
        tosser.end();
        
        // Should not be running after end()
        Assertions.assertFalse(tosser.isRunning(), "Tosser should not be running after end()");
    }
    
    @Test
    void testGetMessagesForLinkWithNullLink() {
        // Test static method for getting messages for a null link
        // This should handle null gracefully and not throw NPE
        Assertions.assertThrows(NullPointerException.class, () -> {
            FtnTosser.getMessagesForLink(null);
        }, "Should throw NPE for null link parameter");
    }
    
    @Test
    void testGetMessagesForLinkWithValidLink() {
        // Create a test link (doesn't need to be persisted for this test)
        Link testLink = new Link();
        testLink.setLinkAddress("2:5020/1042");
        testLink.setLinkName("Test Link");
        testLink.setPaketPassword("test");
        
        // Test static method for getting messages for a link
        List<Message> messages = FtnTosser.getMessagesForLink(testLink);
        Assertions.assertNotNull(messages, "Messages list should not be null");
        // Messages list may be empty if no messages are pending
    }
    
    @Test
    void testTossInboundDirectoryEmptyDirectory() {
        // Test processing empty inbound directory
        Assertions.assertDoesNotThrow(() -> {
            tosser.tossInboundDirectory();
        }, "Processing empty inbound should not throw exception");
        
        // Should not be running after processing empty directory
        Assertions.assertFalse(tosser.isRunning(), "Tosser should not be running after processing empty directory");
    }
    
    @Test
    void testGetFileechoPath() {
        // Test static method for getting fileecho path
        String path = FtnTosser.getFileechoPath();
        Assertions.assertNotNull(path, "Fileecho path should not be null");
        // Should return configured fileecho path
        String expectedFileecho = tempDir.toString() + "/fileecho";
        Assertions.assertEquals(expectedFileecho, path, "Should return configured fileecho path");
    }
    
    @Test
    void testConcurrentProcessing() throws Exception {
        // Test that tosser correctly handles running state in concurrent scenario
        Assertions.assertFalse(tosser.isRunning(), "Tosser should not be running initially");
        
        // Simulate concurrent access with short-running thread
        Thread processingThread = new Thread(() -> {
            try {
                // Process empty directory (quick operation)
                tosser.tossInboundDirectory();
            } catch (Exception e) {
                // Ignore exceptions for this concurrency test
            }
        });
        
        processingThread.start();
        processingThread.join(1000); // Wait max 1 second
        
        // After processing completes
        Assertions.assertFalse(tosser.isRunning(), "Tosser should not be running after completion");
    }
    
    @Test
    void testEndMethodTwice() {
        // Test calling end() method multiple times
        Assertions.assertDoesNotThrow(() -> {
            tosser.end();
            tosser.end(); // Should not throw exception
        }, "Calling end() multiple times should not throw exception");
        
        Assertions.assertFalse(tosser.isRunning(), "Tosser should not be running after multiple end() calls");
    }
    
    @Test
    void testTossInboundDirectoryWithNonExistentDirectory() {
        // Temporarily change inbound to non-existent directory
        String originalInbound = MainHandler.getCurrentInstance().getProperty("ftn.inbound", "");
        MainHandler.getCurrentInstance().setProperty("ftn.inbound", "/non/existent/directory");
        
        try {
            // Should handle non-existent directory gracefully
            Assertions.assertDoesNotThrow(() -> {
                tosser.tossInboundDirectory();
            }, "Processing non-existent inbound directory should not throw exception");
        } finally {
            // Restore original inbound path
            MainHandler.getCurrentInstance().setProperty("ftn.inbound", originalInbound);
        }
    }
    
    @Test
    void testBasicObjectCreation() {
        // Test that tosser can be created and basic methods work
        FtnTosser newTosser = new FtnTosser();
        Assertions.assertNotNull(newTosser, "FtnTosser should be created successfully");
        Assertions.assertFalse(newTosser.isRunning(), "New tosser should not be running");
        
        // Test end method on new instance
        Assertions.assertDoesNotThrow(() -> {
            newTosser.end();
        }, "end() should work on new tosser instance");
    }
}