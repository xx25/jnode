/*
 * Licensed to the jNode FTN Platform Develpoment Team (jNode Team)
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

package jnode.protocol.binkp.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for AddressParser IPv6 and IPv4 address parsing
 */
public class AddressParserTest {

    @Test
    public void testIPv4AddressWithPort() throws IOException {
        InetSocketAddress addr = AddressParser.parseAddress("192.168.1.1:24554");
        assertEquals("192.168.1.1", addr.getHostString());
        assertEquals(24554, addr.getPort());
    }
    
    @Test
    public void testIPv4AddressWithoutPort() throws IOException {
        InetSocketAddress addr = AddressParser.parseAddress("192.168.1.1");
        assertEquals("192.168.1.1", addr.getHostString());
        assertEquals(24554, addr.getPort());
    }
    
    @Test
    public void testIPv6AddressWithBracketsAndPort() throws IOException {
        InetSocketAddress addr = AddressParser.parseAddress("[::1]:24554");
        String host = addr.getHostString();
        // Java may resolve ::1 to full form 0:0:0:0:0:0:0:1
        assertTrue("::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host), "Expected ::1 or expanded form");
        assertEquals(24554, addr.getPort());
    }
    
    @Test
    public void testIPv6AddressWithBracketsWithoutPort() throws IOException {
        InetSocketAddress addr = AddressParser.parseAddress("[::1]");
        String host = addr.getHostString();
        assertTrue("::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host), "Expected ::1 or expanded form");
        assertEquals(24554, addr.getPort());
    }
    
    @Test
    public void testIPv6AddressWithoutBrackets() throws IOException {
        InetSocketAddress addr = AddressParser.parseAddress("::1");
        String host = addr.getHostString();
        assertTrue("::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host), "Expected ::1 or expanded form");
        assertEquals(24554, addr.getPort());
    }
    
    @Test
    public void testIPv6FullAddressWithBracketsAndPort() throws IOException {
        InetSocketAddress addr = AddressParser.parseAddress("[2001:db8::1]:25555");
        String host = addr.getHostString();
        assertTrue("2001:db8::1".equals(host) || (host.contains("2001:db8") && host.contains("1")), 
                "Expected 2001:db8::1 or expanded form");
        assertEquals(25555, addr.getPort());
    }
    
    @Test
    public void testIPv6FullAddressWithoutBrackets() throws IOException {
        InetSocketAddress addr = AddressParser.parseAddress("2001:db8::1");
        String host = addr.getHostString();
        assertTrue("2001:db8::1".equals(host) || (host.contains("2001:db8") && host.contains("1")), 
                "Expected 2001:db8::1 or expanded form");
        assertEquals(24554, addr.getPort());
    }
    
    @Test
    public void testHostnameWithPort() throws IOException {
        InetSocketAddress addr = AddressParser.parseAddress("example.com:24554");
        assertEquals("example.com", addr.getHostString());
        assertEquals(24554, addr.getPort());
    }
    
    @Test
    public void testHostnameWithoutPort() throws IOException {
        InetSocketAddress addr = AddressParser.parseAddress("example.com");
        assertEquals("example.com", addr.getHostString());
        assertEquals(24554, addr.getPort());
    }
    
    @Test
    public void testIPv4WithCustomPort() throws IOException {
        InetSocketAddress addr = AddressParser.parseAddress("10.0.0.1:12345");
        assertEquals("10.0.0.1", addr.getHostString());
        assertEquals(12345, addr.getPort());
    }
    
    @Test
    public void testIPv6WithCustomPort() throws IOException {
        InetSocketAddress addr = AddressParser.parseAddress("[fe80::1]:12345");
        String host = addr.getHostString();
        assertTrue(host.contains("fe80") && host.contains("1"), "Expected fe80::1 or expanded form");
        assertEquals(12345, addr.getPort());
    }
    
    @Test
    public void testInvalidIPv6BracketFormat() {
        assertThrows(IOException.class, () -> AddressParser.parseAddress("[::1:invalid"));
    }
    
    @Test
    public void testInvalidIPv6BracketFormatMissingColon() {
        assertThrows(IOException.class, () -> AddressParser.parseAddress("[::1]invalid"));
    }
    
    @Test
    public void testInvalidPortNumber() {
        assertThrows(IOException.class, () -> AddressParser.parseAddress("192.168.1.1:invalid"));
    }
    
    @Test
    public void testInvalidPortNumberIPv6() {
        assertThrows(IOException.class, () -> AddressParser.parseAddress("[::1]:invalid"));
    }
    
    @Test
    public void testEmptyAddress() {
        assertThrows(IOException.class, () -> AddressParser.parseAddress(""));
    }
    
    @Test
    public void testNullAddress() {
        assertThrows(IOException.class, () -> AddressParser.parseAddress(null));
    }
    
    @Test
    public void testEmptyIPv6Address() {
        assertThrows(IOException.class, () -> AddressParser.parseAddress("[]"));
    }
    
    @Test
    public void testEmptyIPv6AddressWithPort() {
        assertThrows(IOException.class, () -> AddressParser.parseAddress("[]:24554"));
    }
    
    @Test
    public void testIPv6LocalhostVariants() throws IOException {
        // Test various localhost IPv6 representations
        String[] localhostVariants = {"::1", "[::1]", "[::1]:24554"};
        int[] expectedPorts = {24554, 24554, 24554};
        
        for (int i = 0; i < localhostVariants.length; i++) {
            InetSocketAddress addr = AddressParser.parseAddress(localhostVariants[i]);
            String host = addr.getHostString();
            assertTrue("::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host), 
                    "IPv6 localhost variant failed: " + localhostVariants[i]);
            assertEquals(expectedPorts[i], addr.getPort(), 
                    "Port mismatch for: " + localhostVariants[i]);
        }
    }
    
    @Test
    public void testComplexIPv6Addresses() throws IOException {
        // Test more complex IPv6 addresses
        String[] complexAddresses = {
            "[2001:0db8:85a3:0000:0000:8a2e:0370:7334]:8080",
            "[2001:db8:85a3::8a2e:370:7334]:9999",
            "[::ffff:192.0.2.1]:24554"  // IPv4-mapped IPv6
        };
        
        int[] expectedPorts = {8080, 9999, 24554};
        
        for (int i = 0; i < complexAddresses.length; i++) {
            InetSocketAddress addr = AddressParser.parseAddress(complexAddresses[i]);
            assertEquals(expectedPorts[i], addr.getPort(), 
                    "Port mismatch for complex address: " + complexAddresses[i]);
            assertNotNull(addr.getHostString(), "Host should not be null");
        }
    }
}