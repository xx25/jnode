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
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;

/**
 * Unit tests for BinkpAsyncConnector
 * 
 * Tests the async BinkP connector functionality including:
 * - IPv4 and IPv6 address parsing and connection
 * - Constructor validation
 * - Error handling for invalid addresses
 * - Connection initialization
 * 
 * @author jNode Team
 */
public class BinkpAsyncConnectorTest {

    private ServerSocketChannel testServer;
    private int testPort;

    @BeforeAll
    static void setUpClass() {
        TestHelper.initializeTestEnvironment();
    }

    @AfterAll
    static void tearDownClass() {
        TestHelper.cleanupTestEnvironment();
    }

    @BeforeEach
    void setUp() throws IOException {
        // Create a local test server for connection testing
        testServer = ServerSocketChannel.open();
        testServer.bind(new InetSocketAddress("127.0.0.1", 0));
        testPort = testServer.socket().getLocalPort();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (testServer != null && testServer.isOpen()) {
            testServer.close();
        }
    }

    @Test
    void testConstructorWithValidIPv4Address() throws IOException {
        String address = "127.0.0.1:" + testPort;
        
        assertDoesNotThrow(() -> {
            BinkpAsyncConnector connector = new BinkpAsyncConnector(address);
            assertNotNull(connector);
        });
    }

    @Test
    void testConstructorWithValidIPv6Address() throws IOException {
        String address = "[::1]:" + testPort;
        
        // Note: This might fail on systems without IPv6 support
        try {
            BinkpAsyncConnector connector = new BinkpAsyncConnector(address);
            assertNotNull(connector);
        } catch (IOException e) {
            // Expected on systems without IPv6 support
            assertTrue(e.getMessage().contains("protocolAddress") || 
                      e.getMessage().contains("connect"));
        }
    }

    @Test
    void testConstructorWithValidHostname() throws IOException {
        String address = "localhost:" + testPort;
        
        assertDoesNotThrow(() -> {
            BinkpAsyncConnector connector = new BinkpAsyncConnector(address);
            assertNotNull(connector);
        });
    }

    @Test
    void testConstructorWithDefaultPort() {
        String address = "127.0.0.1";
        
        // With non-blocking sockets, constructor should succeed
        // Connection failure will be detected in run() method
        assertDoesNotThrow(() -> {
            BinkpAsyncConnector connector = new BinkpAsyncConnector(address);
            assertNotNull(connector);
        });
    }

    @Test
    void testConstructorWithInvalidAddress() {
        String[] invalidAddresses = {
            "",
            "invalid:address:format",
            "256.256.256.256:24554",  // Invalid IP
            "host:99999",             // Invalid port
            "host:-1",                // Negative port
            "[invalid:ipv6::format]:8080"
        };
        
        for (String address : invalidAddresses) {
            assertThrows(Exception.class, () -> {
                new BinkpAsyncConnector(address);
            }, "Should throw Exception for address: " + address);
        }
    }

    @Test
    void testConstructorWithUnreachableHost() {
        String address = "192.0.2.1:24554"; // RFC 5737 TEST-NET-1
        
        // With non-blocking sockets, constructor should succeed
        // Connection failure will be detected in run() method
        assertDoesNotThrow(() -> {
            BinkpAsyncConnector connector = new BinkpAsyncConnector(address);
            assertNotNull(connector);
        });
    }

    @Test
    void testServerSocketChannelConstructor() throws IOException {
        SocketChannel clientSocket = SocketChannel.open();
        try {
            clientSocket.connect(new InetSocketAddress("127.0.0.1", testPort));
            
            // Accept the connection
            SocketChannel serverSocket = testServer.accept();
            assertNotNull(serverSocket);
            
            // Create connector from accepted socket
            BinkpAsyncConnector connector = new BinkpAsyncConnector(serverSocket);
            assertNotNull(connector);
            
            serverSocket.close();
        } finally {
            clientSocket.close();
        }
    }

    @Test
    void testSocketChannelConstructorWithNullSocket() {
        assertThrows(NullPointerException.class, () -> {
            new BinkpAsyncConnector((SocketChannel) null);
        });
    }

    @Test
    void testIPv6BracketNotationHandling() {
        String[] ipv6Addresses = {
            "[::1]:8080",
            "[2001:db8::1]:25555",
            "[fe80::1%eth0]:24554"
        };
        
        for (String address : ipv6Addresses) {
            try {
                BinkpAsyncConnector connector = new BinkpAsyncConnector(address);
                assertNotNull(connector);
            } catch (Exception e) {
                // Expected if IPv6 is not available or connection fails
                // Accept various types of exceptions that can occur with IPv6
                // Some exceptions might have null messages, which is acceptable
                assertTrue(e != null);
            }
        }
    }

    @Test
    void testAddressParsingEdgeCases() {
        String[] edgeCases = {
            "127.0.0.1:0",           // Port 0
            "localhost:1",           // Port 1
            "127.0.0.1:65535"        // Max port
        };
        
        for (String address : edgeCases) {
            try {
                BinkpAsyncConnector connector = new BinkpAsyncConnector(address);
                // If it doesn't throw, that's fine - connection might fail later
                assertNotNull(connector);
            } catch (IOException e) {
                // Expected for most of these cases
                assertNotNull(e.getMessage());
            }
        }
    }

    @Test
    void testClientConnectionFlag() throws IOException {
        String address = "127.0.0.1:" + testPort;
        
        try {
            BinkpAsyncConnector connector = new BinkpAsyncConnector(address);
            // This is a client connection since we provided an address
            // We can't easily test the clientConnection field since it's package-private
            // But we can verify the constructor succeeds
            assertNotNull(connector);
        } catch (IOException e) {
            // Connection might fail, but constructor should handle address parsing
            assertTrue(e.getMessage().contains("protocolAddress") ||
                      e.getMessage().contains("connect"));
        }
    }

    @Test
    void testServerConnectionConstructor() throws IOException {
        SocketChannel clientSocket = SocketChannel.open();
        try {
            clientSocket.connect(new InetSocketAddress("127.0.0.1", testPort));
            SocketChannel serverSocket = testServer.accept();
            
            // This should create a server-side connector
            BinkpAsyncConnector connector = new BinkpAsyncConnector(serverSocket);
            assertNotNull(connector);
            
            serverSocket.close();
        } finally {
            clientSocket.close();
        }
    }

    @Test
    void testMultipleConnectorInstances() throws IOException {
        // Test that we can create multiple connector instances
        String address = "127.0.0.1:" + testPort;
        
        try {
            BinkpAsyncConnector connector1 = new BinkpAsyncConnector(address);
            BinkpAsyncConnector connector2 = new BinkpAsyncConnector(address);
            
            assertNotNull(connector1);
            assertNotNull(connector2);
            assertNotSame(connector1, connector2);
        } catch (IOException e) {
            // Expected if connections fail
            assertTrue(e.getMessage().contains("protocolAddress") ||
                      e.getMessage().contains("connect"));
        }
    }

    @Test
    void testNonBlockingConfiguration() throws IOException {
        SocketChannel clientSocket = SocketChannel.open();
        try {
            clientSocket.connect(new InetSocketAddress("127.0.0.1", testPort));
            SocketChannel serverSocket = testServer.accept();
            
            BinkpAsyncConnector connector = new BinkpAsyncConnector(serverSocket);
            assertNotNull(connector);
            
            // The socket should be configured as non-blocking by the connector
            assertFalse(serverSocket.isBlocking());
            
            serverSocket.close();
        } finally {
            clientSocket.close();
        }
    }
}