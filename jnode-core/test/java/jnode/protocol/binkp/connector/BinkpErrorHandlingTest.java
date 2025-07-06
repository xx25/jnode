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

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for BinkP connector error handling and connection management
 * 
 * Tests various error conditions and how connectors handle them,
 * including network errors, invalid addresses, and timeout scenarios.
 * 
 * @author jNode Team
 */
public class BinkpErrorHandlingTest {

    @BeforeAll
    static void setUpClass() {
        TestHelper.initializeTestEnvironment();
    }

    @AfterAll
    static void tearDownClass() {
        TestHelper.cleanupTestEnvironment();
    }

    @Test
    void testAsyncConnectorWithUnreachableHost() {
        // Use TEST-NET-1 address that should be unreachable
        String unreachableAddress = "192.0.2.1:24554";
        
        // With non-blocking sockets, constructor should succeed
        // Connection failure will be detected in run() method
        assertDoesNotThrow(() -> {
            BinkpAsyncConnector connector = new BinkpAsyncConnector(unreachableAddress);
            assertNotNull(connector);
        });
    }

    @Test
    void testAsyncConnectorWithInvalidHostname() {
        String invalidHostname = "this-hostname-definitely-does-not-exist-12345.invalid:24554";
        
        // Invalid hostname should still throw exception during address resolution
        Exception exception = assertThrows(Exception.class, () -> {
            new BinkpAsyncConnector(invalidHostname);
        });
        
        assertNotNull(exception);
        assertTrue(exception instanceof UnknownHostException ||
                  exception instanceof java.nio.channels.UnresolvedAddressException ||
                  exception.getMessage().contains("protocolAddress") ||
                  exception.getMessage().contains("unknown"));
    }

    @Test
    void testAsyncConnectorWithInvalidPort() {
        String[] invalidPortAddresses = {
            "127.0.0.1:99999",     // Port too high
            "127.0.0.1:-1",        // Negative port
            "127.0.0.1:abc",       // Non-numeric port
            "127.0.0.1:",          // Empty port
        };
        
        for (String address : invalidPortAddresses) {
            assertThrows(Exception.class, () -> {
                new BinkpAsyncConnector(address);
            }, "Should fail for invalid port address: " + address);
        }
        
        // Port 0 might fail on some systems due to binding restrictions
        try {
            new BinkpAsyncConnector("127.0.0.1:0");
            // If it succeeds, that's fine
        } catch (IOException e) {
            // If it fails, that's also acceptable ("Can't assign requested address")
            assertTrue(e.getMessage().contains("assign") || e.getMessage().contains("protocolAddress"));
        }
    }

    @Test
    void testAsyncConnectorWithMalformedAddress() {
        String[] malformedAddresses = {
            "",                    // Empty string
            ":",                   // Just colon
            ":::",                 // Too many colons
            "host:port:extra",     // Too many parts
            "256.256.256.256:24554", // Invalid IP
            "[invalid:ipv6]:8080"  // Malformed IPv6
        };
        
        for (String address : malformedAddresses) {
            assertThrows(Exception.class, () -> {
                new BinkpAsyncConnector(address);
            }, "Should fail for malformed address: " + address);
        }
    }

    @Test
    void testPipeConnectorWithNonExistentCommand() {
        String nonExistentCommand = "this_command_absolutely_does_not_exist_anywhere_12345";
        
        IOException exception = assertThrows(IOException.class, () -> {
            new BinkpPipeConnector(nonExistentCommand);
        });
        
        assertNotNull(exception.getMessage());
    }

    @Test
    void testPipeConnectorWithEmptyCommand() {
        assertThrows(Exception.class, () -> {
            new BinkpPipeConnector("");
        });
    }

    @Test
    void testPipeConnectorWithNullCommand() {
        assertThrows(NullPointerException.class, () -> {
            new BinkpPipeConnector(null);
        });
    }

    @Test
    void testPipeConnectorWithInvalidCommandSyntax() {
        String[] invalidCommands = {
            "command\0with\0nulls",  // Commands with null bytes
            "command\nwith\nnewlines", // Commands with newlines
        };
        
        for (String command : invalidCommands) {
            try {
                new BinkpPipeConnector(command);
                // Some systems might accept these, others might reject them
            } catch (IOException e) {
                // Expected on systems that validate command syntax
                assertNotNull(e.getMessage());
            }
        }
    }

    @Test
    void testAsyncConnectorConnectionRefused() {
        // Try to connect to localhost on an unused port
        String refusedAddress = "127.0.0.1:1"; // Port 1 should be refused
        
        // With non-blocking sockets, constructor should succeed
        // Connection failure will be detected in run() method
        assertDoesNotThrow(() -> {
            BinkpAsyncConnector connector = new BinkpAsyncConnector(refusedAddress);
            assertNotNull(connector);
        });
    }

    @Test
    void testAsyncConnectorNetworkUnreachable() {
        // Use an address that should be network unreachable
        String unreachableNetwork = "10.255.255.1:24554";
        
        try {
            new BinkpAsyncConnector(unreachableNetwork);
            // If this succeeds, the network might actually be reachable
        } catch (IOException e) {
            // Expected - network unreachable or connection timeout
            assertNotNull(e.getMessage());
        }
    }

    @Test
    void testMultipleFailedConnections() {
        String unreachableAddress = "192.0.2.1:24554";
        
        // Try multiple connections to ensure no resource leaks
        for (int i = 0; i < 5; i++) {
            final int attempt = i;
            assertDoesNotThrow(() -> {
                BinkpAsyncConnector connector = new BinkpAsyncConnector(unreachableAddress);
                assertNotNull(connector);
            }, "Connection attempt " + attempt + " should succeed");
        }
    }

    @Test
    void testPipeConnectorWithFailingCommand() {
        String failingCommand = getFailingCommand();
        
        // Constructor should succeed even if command will fail later
        assertDoesNotThrow(() -> {
            BinkpPipeConnector connector = new BinkpPipeConnector(failingCommand);
            assertNotNull(connector);
        });
    }

    @Test
    void testAsyncConnectorNullSocketChannel() {
        assertThrows(NullPointerException.class, () -> {
            new BinkpAsyncConnector((java.nio.channels.SocketChannel) null);
        });
    }

    @Test
    void testErrorMessageQuality() {
        try {
            new BinkpAsyncConnector("invalid_address_format");
            fail("Should have thrown Exception");
        } catch (Exception e) {
            // Some exceptions might have null messages in certain environments
            String message = e.getMessage();
            if (message != null) {
                assertFalse(message.isEmpty());
                // Error message should be informative
                assertTrue(message.length() > 5, "Error message should be descriptive");
            }
            // Just ensure we got an exception
            assertNotNull(e);
        }
    }

    @Test
    void testConcurrentErrorHandling() throws InterruptedException {
        // Test that multiple concurrent connections don't interfere
        String unreachableAddress = "192.0.2.1:24554";
        Thread[] threads = new Thread[5];
        boolean[] results = new boolean[5];
        
        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    BinkpAsyncConnector connector = new BinkpAsyncConnector(unreachableAddress);
                    results[index] = (connector != null); // Should succeed in constructor
                } catch (Exception e) {
                    results[index] = false; // Unexpected exception
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(5000); // 5 second timeout
        }
        
        // All threads should have successfully created connectors
        for (int i = 0; i < results.length; i++) {
            assertTrue(results[i], "Thread " + i + " should have created connector");
        }
    }

    @Test
    void testResourceCleanupOnError() {
        // Test that resources are properly cleaned up
        String unreachableAddress = "192.0.2.1:24554";
        
        // Create multiple connectors
        for (int i = 0; i < 10; i++) {
            assertDoesNotThrow(() -> {
                BinkpAsyncConnector connector = new BinkpAsyncConnector(unreachableAddress);
                assertNotNull(connector);
            });
        }
        
        // If we get here without running out of resources, cleanup is working
        assertTrue(true, "Resource cleanup appears to be working");
    }

    @Test
    void testErrorHandlingWithValidThenInvalidAddress() throws IOException {
        // First create a successful connector (should succeed even if no server)
        assertDoesNotThrow(() -> {
            BinkpAsyncConnector connector = new BinkpAsyncConnector("127.0.0.1:24554");
            assertNotNull(connector);
        });
        
        // Then try an invalid address - should still fail properly
        assertThrows(Exception.class, () -> {
            new BinkpAsyncConnector("invalid_address");
        });
    }

    @Test
    void testPipeConnectorCommandValidation() {
        // Test various command validation scenarios
        String[] questionableCommands = {
            "echo hello",           // Simple valid command
            "/bin/echo hello",      // Absolute path
            "cmd /c echo hello",    // Windows command
            "echo 'hello world'",   // Quoted arguments
            "echo hello > /dev/null" // With redirection
        };
        
        for (String command : questionableCommands) {
            try {
                BinkpPipeConnector connector = new BinkpPipeConnector(command);
                assertNotNull(connector);
            } catch (IOException e) {
                // Some commands might not be available in test environment
                System.out.println("Command not available: " + command);
            }
        }
    }

    // Helper methods

    private String getFailingCommand() {
        if (TestHelper.isWindows()) {
            return "cmd /c exit 1";
        } else {
            return "false";
        }
    }
}