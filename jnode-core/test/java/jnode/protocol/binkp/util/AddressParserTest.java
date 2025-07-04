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

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.junit.Test;

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
        assertTrue("Expected ::1 or expanded form", "::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host));
        assertEquals(24554, addr.getPort());
    }
    
    @Test
    public void testIPv6AddressWithBracketsWithoutPort() throws IOException {
        InetSocketAddress addr = AddressParser.parseAddress("[::1]");
        String host = addr.getHostString();
        assertTrue("Expected ::1 or expanded form", "::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host));
        assertEquals(24554, addr.getPort());
    }
    
    @Test
    public void testIPv6AddressWithoutBrackets() throws IOException {
        InetSocketAddress addr = AddressParser.parseAddress("::1");
        String host = addr.getHostString();
        assertTrue("Expected ::1 or expanded form", "::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host));
        assertEquals(24554, addr.getPort());
    }
    
    @Test
    public void testIPv6FullAddressWithBracketsAndPort() throws IOException {
        InetSocketAddress addr = AddressParser.parseAddress("[2001:db8::1]:25555");
        String host = addr.getHostString();
        assertTrue("Expected 2001:db8::1 or expanded form", 
                "2001:db8::1".equals(host) || (host.contains("2001:db8") && host.contains("1")));
        assertEquals(25555, addr.getPort());
    }
    
    @Test
    public void testIPv6FullAddressWithoutBrackets() throws IOException {
        InetSocketAddress addr = AddressParser.parseAddress("2001:db8::1");
        String host = addr.getHostString();
        assertTrue("Expected 2001:db8::1 or expanded form", 
                "2001:db8::1".equals(host) || (host.contains("2001:db8") && host.contains("1")));
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
        assertTrue("Expected fe80::1 or expanded form", host.contains("fe80") && host.contains("1"));
        assertEquals(12345, addr.getPort());
    }
    
    @Test(expected = IOException.class)
    public void testInvalidIPv6BracketFormat() throws IOException {
        AddressParser.parseAddress("[::1:invalid");
    }
    
    @Test(expected = IOException.class)
    public void testInvalidIPv6BracketFormatMissingColon() throws IOException {
        AddressParser.parseAddress("[::1]invalid");
    }
    
    @Test(expected = IOException.class)
    public void testInvalidPortNumber() throws IOException {
        AddressParser.parseAddress("192.168.1.1:invalid");
    }
    
    @Test(expected = IOException.class)
    public void testInvalidPortNumberIPv6() throws IOException {
        AddressParser.parseAddress("[::1]:invalid");
    }
    
    @Test(expected = IOException.class)
    public void testEmptyAddress() throws IOException {
        AddressParser.parseAddress("");
    }
    
    @Test(expected = IOException.class)
    public void testNullAddress() throws IOException {
        AddressParser.parseAddress(null);
    }
    
    @Test(expected = IOException.class)
    public void testEmptyIPv6Address() throws IOException {
        AddressParser.parseAddress("[]");
    }
    
    @Test(expected = IOException.class)
    public void testEmptyIPv6AddressWithPort() throws IOException {
        AddressParser.parseAddress("[]:24554");
    }
    
    @Test
    public void testIPv6LocalhostVariants() throws IOException {
        // Test various localhost IPv6 representations
        String[] localhostVariants = {"::1", "[::1]", "[::1]:24554"};
        int[] expectedPorts = {24554, 24554, 24554};
        
        for (int i = 0; i < localhostVariants.length; i++) {
            InetSocketAddress addr = AddressParser.parseAddress(localhostVariants[i]);
            String host = addr.getHostString();
            assertTrue("IPv6 localhost variant failed: " + localhostVariants[i], 
                    "::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host));
            assertEquals("Port mismatch for: " + localhostVariants[i], expectedPorts[i], addr.getPort());
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
            assertEquals("Port mismatch for complex address: " + complexAddresses[i], 
                    expectedPorts[i], addr.getPort());
            assertNotNull("Host should not be null", addr.getHostString());
        }
    }
}