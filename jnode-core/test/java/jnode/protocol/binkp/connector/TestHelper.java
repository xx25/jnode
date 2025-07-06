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

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;

import jnode.main.MainHandler;

/**
 * Test helper utility for BinkP connector tests
 * 
 * Provides mock initialization and utilities for testing
 * BinkP connectors without full jNode initialization.
 * 
 * @author jNode Team
 */
public class TestHelper {

    private static boolean initialized = false;
    private static MainHandler mockMainHandler;

    /**
     * Initialize minimal test environment for BinkP connector testing
     */
    public static void initializeTestEnvironment() {
        if (initialized) {
            return;
        }

        try {
            // Create a mock MainHandler with minimal required configuration
            mockMainHandler = createMockMainHandler();
            
            // Set the MainHandler instance using reflection
            setMainHandlerInstance(mockMainHandler);
            
            initialized = true;
        } catch (Exception e) {
            System.err.println("Failed to initialize test environment: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Clean up test environment
     */
    public static void cleanupTestEnvironment() {
        try {
            if (mockMainHandler != null) {
                setMainHandlerInstance(null);
                mockMainHandler = null;
            }
            initialized = false;
        } catch (Exception e) {
            System.err.println("Failed to cleanup test environment: " + e.getMessage());
        }
    }

    private static MainHandler createMockMainHandler() throws Exception {
        // Create a properties object with minimal required properties
        Properties props = new Properties();
        props.setProperty("binkp.temp", System.getProperty("java.io.tmpdir"));
        props.setProperty("binkp.network", "fidonet");
        props.setProperty("binkp.maxmem", "10485760");
        props.setProperty("binkp.size", "32767");
        props.setProperty("binkp.timeout", "30");
        props.setProperty("binkp.connect.timeout", "1");
        
        // Create MainHandler instance using Properties constructor
        MainHandler handler = new MainHandler(props);
        
        return handler;
    }

    private static void setMainHandlerInstance(MainHandler handler) throws Exception {
        // Use reflection to set the static instance field
        Field instanceField = MainHandler.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, handler);
    }

    /**
     * Get a temporary directory for testing
     */
    public static File getTempDirectory() {
        return new File(System.getProperty("java.io.tmpdir"));
    }

    /**
     * Check if we're running on Windows
     */
    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    /**
     * Get an echo command appropriate for the current OS
     */
    public static String getEchoCommand() {
        if (isWindows()) {
            return "cmd /c echo";
        } else {
            return "echo";
        }
    }

    /**
     * Get a sleep command appropriate for the current OS
     */
    public static String getSleepCommand() {
        if (isWindows()) {
            return "cmd /c timeout /t 1 /nobreak";
        } else {
            return "sleep 1";
        }
    }

    /**
     * Check if IPv6 is supported in the test environment
     */
    public static boolean supportsIPv6() {
        try {
            java.net.InetSocketAddress addr = new java.net.InetSocketAddress("::1", 0);
            return addr.getAddress() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Skip test if condition is not met
     */
    public static void assumeTrue(boolean condition, String message) {
        if (!condition) {
            org.junit.jupiter.api.Assumptions.assumeTrue(condition, message);
        }
    }
}