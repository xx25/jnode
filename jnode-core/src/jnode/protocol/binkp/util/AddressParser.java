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

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Utility class for parsing IPv4 and IPv6 addresses with proper bracket notation support
 * 
 * @author jNode Team
 */
public class AddressParser {
    
    /**
     * Parses protocol address string and returns InetSocketAddress
     * Supports formats:
     * - IPv4: "192.168.1.1:24554" or "192.168.1.1" (defaults to port 24554)
     * - IPv6: "[::1]:24554" or "[2001:db8::1]:24554" or "::1" (defaults to port 24554)
     * - Hostname: "example.com:24554" or "example.com" (defaults to port 24554)
     * 
     * @param protocolAddress the address string to parse
     * @return InetSocketAddress for the parsed address
     * @throws IOException if the address format is invalid
     */
    public static InetSocketAddress parseAddress(String protocolAddress) throws IOException {
        if (protocolAddress == null || protocolAddress.trim().isEmpty()) {
            throw new IOException("Invalid protocolAddress: null or empty");
        }
        
        protocolAddress = protocolAddress.trim();
        
        // Check for IPv6 bracket notation: [::1]:24554
        if (protocolAddress.startsWith("[")) {
            int closeBracket = protocolAddress.indexOf(']');
            if (closeBracket == -1) {
                throw new IOException("Invalid IPv6 address format: missing closing bracket in " + protocolAddress);
            }
            
            String ipv6Address = protocolAddress.substring(1, closeBracket);
            if (ipv6Address.isEmpty()) {
                throw new IOException("Invalid IPv6 address format: empty address in " + protocolAddress);
            }
            
            // Check for port after bracket
            if (closeBracket + 1 < protocolAddress.length()) {
                if (protocolAddress.charAt(closeBracket + 1) != ':') {
                    throw new IOException("Invalid IPv6 address format: expected ':' after bracket in " + protocolAddress);
                }
                try {
                    int port = Integer.parseInt(protocolAddress.substring(closeBracket + 2));
                    return new InetSocketAddress(ipv6Address, port);
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid port number in " + protocolAddress);
                }
            } else {
                // No port specified, use default
                return new InetSocketAddress(ipv6Address, 24554);
            }
        }
        
        // Check for IPv4 or hostname with port: "192.168.1.1:24554" or "example.com:24554"
        int lastColon = protocolAddress.lastIndexOf(':');
        if (lastColon != -1) {
            // Check if this is an IPv6 address without brackets (contains multiple colons)
            if (protocolAddress.indexOf(':') != lastColon) {
                // Multiple colons, likely IPv6 without brackets
                // Assume no port, treat entire string as IPv6 address
                return new InetSocketAddress(protocolAddress, 24554);
            }
            
            // Single colon, likely IPv4:port or hostname:port
            String address = protocolAddress.substring(0, lastColon);
            try {
                int port = Integer.parseInt(protocolAddress.substring(lastColon + 1));
                return new InetSocketAddress(address, port);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid port number in " + protocolAddress);
            }
        }
        
        // No port specified, use default
        return new InetSocketAddress(protocolAddress, 24554);
    }
}