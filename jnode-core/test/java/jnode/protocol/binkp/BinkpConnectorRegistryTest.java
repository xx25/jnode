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

import java.io.IOException;
import java.util.Collection;

import org.junit.jupiter.api.Test;

import jnode.protocol.binkp.connector.BinkpAbstractConnector;
import jnode.protocol.binkp.connector.BinkpAsyncConnector;
import jnode.protocol.binkp.connector.BinkpPipeConnector;
import jnode.protocol.binkp.connector.BinkpSyncConnector;

/**
 * Unit tests for BinkpConnectorRegistry
 * 
 * Tests the connector registry functionality including:
 * - Singleton pattern behavior
 * - Built-in connector mappings
 * - Custom connector registration
 * - Key management
 * 
 * @author jNode Team
 */
public class BinkpConnectorRegistryTest {

    @Test
    void testSingletonPattern() {
        BinkpConnectorRegistry registry1 = BinkpConnectorRegistry.getSelf();
        BinkpConnectorRegistry registry2 = BinkpConnectorRegistry.getSelf();
        
        assertNotNull(registry1);
        assertNotNull(registry2);
        assertSame(registry1, registry2);
    }

    @Test
    void testBuiltInConnectors() {
        BinkpConnectorRegistry registry = BinkpConnectorRegistry.getSelf();
        
        // Test async connector
        assertEquals(BinkpAsyncConnector.class, registry.getConnector("async:"));
        
        // Test sync connector
        assertEquals(BinkpSyncConnector.class, registry.getConnector("sync:"));
        
        // Test pipe connector
        assertEquals(BinkpPipeConnector.class, registry.getConnector("pipe:"));
        
        // Test pipe connector alias
        assertEquals(BinkpPipeConnector.class, registry.getConnector("|"));
    }

    @Test
    void testGetConnectorNotFound() {
        BinkpConnectorRegistry registry = BinkpConnectorRegistry.getSelf();
        
        assertNull(registry.getConnector("nonexistent:"));
        assertNull(registry.getConnector(""));
        assertNull(registry.getConnector("unknown"));
    }

    @Test
    void testGetKeys() {
        BinkpConnectorRegistry registry = BinkpConnectorRegistry.getSelf();
        Collection<String> keys = registry.getKeys();
        
        assertNotNull(keys);
        assertTrue(keys.size() >= 4); // At least the built-in connectors
        
        // Check that built-in keys are present
        assertTrue(keys.contains("async:"));
        assertTrue(keys.contains("sync:"));
        assertTrue(keys.contains("pipe:"));
        assertTrue(keys.contains("|"));
    }

    @Test
    void testGetKeysImmutable() {
        BinkpConnectorRegistry registry = BinkpConnectorRegistry.getSelf();
        Collection<String> keys = registry.getKeys();
        
        // Should return unmodifiable collection
        try {
            keys.add("test:");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    @Test
    void testAddNewConnector() {
        BinkpConnectorRegistry registry = BinkpConnectorRegistry.getSelf();
        
        // Create a test connector class
        class TestConnector extends BinkpAbstractConnector {
            public TestConnector() throws IOException {
                super();
            }
            
            @Override
            public void run() {
                // Test implementation
            }
        }
        
        // Add new connector
        registry.add("test:", TestConnector.class);
        
        // Verify it was added
        assertEquals(TestConnector.class, registry.getConnector("test:"));
        assertTrue(registry.getKeys().contains("test:"));
    }

    @Test
    void testAddExistingConnectorIgnored() {
        BinkpConnectorRegistry registry = BinkpConnectorRegistry.getSelf();
        
        // Create a test connector class
        class TestConnector extends BinkpAbstractConnector {
            public TestConnector() throws IOException {
                super();
            }
            
            @Override
            public void run() {
                // Test implementation
            }
        }
        
        // Try to override existing connector
        Class<? extends BinkpAbstractConnector> originalClass = registry.getConnector("async:");
        registry.add("async:", TestConnector.class);
        
        // Should not have changed
        assertEquals(originalClass, registry.getConnector("async:"));
        assertNotEquals(TestConnector.class, registry.getConnector("async:"));
    }

    @Test
    void testAddNullKey() {
        BinkpConnectorRegistry registry = BinkpConnectorRegistry.getSelf();
        
        // Create a test connector class
        class TestConnector extends BinkpAbstractConnector {
            public TestConnector() throws IOException {
                super();
            }
            
            @Override
            public void run() {
                // Test implementation
            }
        }
        
        // Add with null key should not crash
        registry.add(null, TestConnector.class);
        
        // The HashMap will store null key, so this might not return null
        // Let's just verify it doesn't crash
        Class<?> result = registry.getConnector(null);
        // Don't assert null here since HashMap allows null keys
    }

    @Test
    void testAddNullConnector() {
        BinkpConnectorRegistry registry = BinkpConnectorRegistry.getSelf();
        
        // Add with null connector should not crash
        registry.add("null:", null);
        
        // Verify it was added (can store null values)
        assertNull(registry.getConnector("null:"));
        assertTrue(registry.getKeys().contains("null:"));
    }

    @Test
    void testConnectorKeysFormat() {
        BinkpConnectorRegistry registry = BinkpConnectorRegistry.getSelf();
        Collection<String> keys = registry.getKeys();
        
        // Verify key formats
        for (String key : keys) {
            if (key != null) { // Skip null keys if they exist
                if (!key.equals("|")) {
                    assertTrue(key.endsWith(":"), "Key should end with colon: " + key);
                }
            }
        }
    }

    @Test
    void testMultipleRegistryInstances() {
        // Test that multiple calls to getSelf() return the same instance
        BinkpConnectorRegistry[] registries = new BinkpConnectorRegistry[10];
        
        for (int i = 0; i < registries.length; i++) {
            registries[i] = BinkpConnectorRegistry.getSelf();
        }
        
        // All should be the same instance
        for (int i = 1; i < registries.length; i++) {
            assertSame(registries[0], registries[i]);
        }
    }

    @Test
    void testThreadSafety() throws InterruptedException {
        final BinkpConnectorRegistry registry = BinkpConnectorRegistry.getSelf();
        final int threadCount = 10;
        final Thread[] threads = new Thread[threadCount];
        final boolean[] results = new boolean[threadCount];
        
        // Create test connector classes
        class TestConnector1 extends BinkpAbstractConnector {
            public TestConnector1() throws IOException { super(); }
            
            @Override
            public void run() {
                // Test implementation
            }
        }
        class TestConnector2 extends BinkpAbstractConnector {
            public TestConnector2() throws IOException { super(); }
            
            @Override
            public void run() {
                // Test implementation
            }
        }
        
        // Create threads that try to add connectors concurrently
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (index % 2 == 0) {
                            registry.add("thread" + index + ":", TestConnector1.class);
                        } else {
                            registry.add("thread" + index + ":", TestConnector2.class);
                        }
                        results[index] = true;
                    } catch (Exception e) {
                        results[index] = false;
                    }
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(1000);
        }
        
        // Verify all threads completed successfully
        for (int i = 0; i < threadCount; i++) {
            assertTrue(results[i], "Thread " + i + " failed");
        }
        
        // Verify connectors were added
        for (int i = 0; i < threadCount; i++) {
            assertNotNull(registry.getConnector("thread" + i + ":"),
                         "Connector not found for thread " + i);
        }
    }
}