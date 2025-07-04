package org.jnode.httpd;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import jnode.event.IEvent;
import jnode.module.JnodeModuleException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

@SuppressWarnings("unused")
public class HttpdModuleTest {

    @Test
    public void testConstructorWithInvalidConfig() {
        String invalidPath = "/nonexistent/path/config.cfg";
        
        assertThrows(JnodeModuleException.class, () -> {
            new HttpdModule(invalidPath);
        });
    }

    @Test
    public void testHandleEvent() throws IOException, JnodeModuleException {
        File tempConfig = File.createTempFile("test", ".cfg");
        tempConfig.deleteOnExit();
        
        Properties props = new Properties();
        props.setProperty("port", "8080");
        try (FileWriter writer = new FileWriter(tempConfig)) {
            props.store(writer, "Test config");
        }
        
        HttpdModule module = new HttpdModule(tempConfig.getAbsolutePath());
        
        IEvent mockEvent = new IEvent() {
            @Override
            public String getEvent() {
                return "TestEvent";
            }
        };
        
        assertDoesNotThrow(() -> {
            module.handle(mockEvent);
        });
    }

    @Test
    public void testStopWithoutStart() throws IOException, JnodeModuleException {
        File tempConfig = File.createTempFile("test", ".cfg");
        tempConfig.deleteOnExit();
        
        Properties props = new Properties();
        props.setProperty("port", "8080");
        try (FileWriter writer = new FileWriter(tempConfig)) {
            props.store(writer, "Test config");
        }
        
        HttpdModule module = new HttpdModule(tempConfig.getAbsolutePath());
        
        assertDoesNotThrow(() -> {
            module.stop();
        });
    }
}