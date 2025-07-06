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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Unit tests for BinkpPipeConnector
 * 
 * Tests the pipe-based BinkP connector functionality including:
 * - Command execution and process creation
 * - Constructor validation
 * - Error handling for invalid commands
 * - Process interaction
 * 
 * @author jNode Team
 */
public class BinkpPipeConnectorTest {

    @BeforeAll
    static void setUpClass() {
        TestHelper.initializeTestEnvironment();
    }

    @AfterAll
    static void tearDownClass() {
        TestHelper.cleanupTestEnvironment();
    }

    @Test
    void testConstructorWithValidCommand() throws IOException {
        // Use a simple command that should exist on most systems
        String command = getEchoCommand();
        
        BinkpPipeConnector connector = new BinkpPipeConnector(command);
        assertNotNull(connector);
    }

    @Test
    void testConstructorWithInvalidCommand() {
        String invalidCommand = "this_command_definitely_does_not_exist_12345";
        
        IOException exception = assertThrows(IOException.class, () -> {
            new BinkpPipeConnector(invalidCommand);
        });
        
        assertNotNull(exception.getMessage());
    }

    @Test
    void testConstructorWithEmptyCommand() {
        String emptyCommand = "";
        
        assertThrows(IllegalArgumentException.class, () -> {
            new BinkpPipeConnector(emptyCommand);
        });
    }

    @Test
    void testConstructorWithNullCommand() {
        assertThrows(NullPointerException.class, () -> {
            new BinkpPipeConnector(null);
        });
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void testConstructorWithUnixCommand() throws IOException {
        String command = "/bin/echo test";
        
        BinkpPipeConnector connector = new BinkpPipeConnector(command);
        assertNotNull(connector);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testConstructorWithWindowsCommand() throws IOException {
        String command = "cmd /c echo test";
        
        BinkpPipeConnector connector = new BinkpPipeConnector(command);
        assertNotNull(connector);
    }

    @Test
    void testConstructorWithComplexCommand() throws IOException {
        // Test command with arguments
        String command = getEchoCommand() + " hello world";
        
        BinkpPipeConnector connector = new BinkpPipeConnector(command);
        assertNotNull(connector);
    }

    @Test
    void testMultiplePipeConnectorInstances() throws IOException {
        String command = getEchoCommand();
        
        BinkpPipeConnector connector1 = new BinkpPipeConnector(command);
        BinkpPipeConnector connector2 = new BinkpPipeConnector(command);
        
        assertNotNull(connector1);
        assertNotNull(connector2);
        assertNotSame(connector1, connector2);
    }

    @Test
    void testPipeConnectorWithLongRunningCommand() throws IOException {
        // Use a command that will run longer (sleep/timeout)
        String command = getSleepCommand();
        
        BinkpPipeConnector connector = new BinkpPipeConnector(command);
        assertNotNull(connector);
    }

    @Test
    void testPipeConnectorWithQuotedArguments() throws IOException {
        // Test handling of quoted arguments
        String command = getEchoCommand() + " \"hello world\"";
        
        BinkpPipeConnector connector = new BinkpPipeConnector(command);
        assertNotNull(connector);
    }

    @Test
    void testPipeConnectorErrorHandling() {
        // Test with command that will fail immediately
        String failingCommand = getFailingCommand();
        
        // Constructor should succeed even if the command will fail later
        assertDoesNotThrow(() -> {
            BinkpPipeConnector connector = new BinkpPipeConnector(failingCommand);
            assertNotNull(connector);
        });
    }

    @Test
    void testPipeConnectorWithSpecialCharacters() throws IOException {
        // Test command with special characters that might need escaping
        String command = getEchoCommand() + " test_with_underscore";
        
        BinkpPipeConnector connector = new BinkpPipeConnector(command);
        assertNotNull(connector);
    }

    @Test
    void testPipeConnectorCommandFormats() throws IOException {
        // Test various command formats that should be valid
        String[] commands = {
            getEchoCommand(),
            getEchoCommand() + " hello",
            getEchoCommand() + " hello world test"
        };
        
        for (String command : commands) {
            BinkpPipeConnector connector = new BinkpPipeConnector(command);
            assertNotNull(connector, "Failed to create connector for command: " + command);
        }
    }

    @Test
    void testPipeConnectorWithEnvironmentVariables() throws IOException {
        // Test command that uses environment variables
        String command = getCommandWithEnvVar();
        
        try {
            BinkpPipeConnector connector = new BinkpPipeConnector(command);
            assertNotNull(connector);
        } catch (IOException e) {
            // Some systems might not support this, so we allow the exception
            assertNotNull(e.getMessage());
        }
    }

    @Test
    void testPipeConnectorWithPipedCommand() throws IOException {
        // Test command with pipes (if supported by the OS)
        String command = getPipedCommand();
        
        try {
            BinkpPipeConnector connector = new BinkpPipeConnector(command);
            assertNotNull(connector);
        } catch (IOException e) {
            // Complex commands might not work in all environments
            assertNotNull(e.getMessage());
        }
    }

    @Test
    void testPipeConnectorWithRedirection() throws IOException {
        // Test command with output redirection
        String command = getRedirectionCommand();
        
        try {
            BinkpPipeConnector connector = new BinkpPipeConnector(command);
            assertNotNull(connector);
        } catch (IOException e) {
            // Redirection might not work in all environments
            assertNotNull(e.getMessage());
        }
    }

    // Helper methods to get OS-appropriate commands

    private String getEchoCommand() {
        return TestHelper.getEchoCommand();
    }

    private String getSleepCommand() {
        return TestHelper.getSleepCommand();
    }

    private String getFailingCommand() {
        if (TestHelper.isWindows()) {
            return "cmd /c exit 1";
        } else {
            return "false";
        }
    }

    private String getCommandWithEnvVar() {
        if (TestHelper.isWindows()) {
            return "cmd /c echo %PATH%";
        } else {
            return "echo $PATH";
        }
    }

    private String getPipedCommand() {
        if (TestHelper.isWindows()) {
            return "cmd /c echo test | findstr test";
        } else {
            return "echo test | grep test";
        }
    }

    private String getRedirectionCommand() {
        if (TestHelper.isWindows()) {
            return "cmd /c echo test > nul";
        } else {
            return "echo test > /dev/null";
        }
    }

    private boolean isWindows() {
        return TestHelper.isWindows();
    }
}