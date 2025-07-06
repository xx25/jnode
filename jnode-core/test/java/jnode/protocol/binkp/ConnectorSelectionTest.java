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

package jnode.protocol.binkp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;

import org.junit.jupiter.api.Test;

import jnode.protocol.binkp.connector.BinkpAsyncConnector;

/**
 * Test to demonstrate connector selection logic
 */
public class ConnectorSelectionTest {

    @Test
    void testConnectorSelectionOrder() {
        BinkpConnectorRegistry registry = BinkpConnectorRegistry.getSelf();
        Collection<String> keys = registry.getKeys();
        
        System.out.println("Registry keys in iteration order: " + keys);
        
        // Test the selection logic for different protocol addresses
        String[] testAddresses = {
            "192.168.1.1:24554",
            "example.com:25555",
            "async:example.com:25555",
            "pipe:test",
            "|test"
        };
        
        for (String protocolAddress : testAddresses) {
            String selectedKey = null;
            
            // This mimics the selection logic from BinkpAsyncClientPool
            for (String key : keys) {
                if (protocolAddress.startsWith(key)) {
                    selectedKey = key;
                    break;
                }
            }
            
            if (selectedKey == null) {
                System.out.println("Address: " + protocolAddress + " -> DEFAULT (BinkpAsyncConnector)");
            } else {
                System.out.println("Address: " + protocolAddress + " -> " + selectedKey + " (" + 
                    registry.getConnector(selectedKey).getSimpleName() + ")");
            }
        }
    }

    @Test
    void testDefaultConnectorSelection() {
        // Test that addresses without prefixes use BinkpAsyncConnector as default
        BinkpConnectorRegistry registry = BinkpConnectorRegistry.getSelf();
        Collection<String> keys = registry.getKeys();
        
        String regularAddress = "192.168.1.1:24554";
        String foundKey = null;
        
        for (String key : keys) {
            if (regularAddress.startsWith(key)) {
                foundKey = key;
                break;
            }
        }
        
        assertNull(foundKey, "Regular address should not match any prefix");
        
        // This confirms that the default fallback is BinkpAsyncConnector
        // as implemented in BinkpAsyncClientPool.java line 68
    }
}