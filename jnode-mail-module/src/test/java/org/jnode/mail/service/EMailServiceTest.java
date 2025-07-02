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

package org.jnode.mail.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EMailServiceTest {

    private EMailService emailService;
    
    @BeforeEach
    public void setUp() {
        emailService = new EMailService();
        emailService.setHost("smtp.example.com");
        emailService.setPort("25");
        emailService.setFromAddr("test@example.com");
    }

    @Test
    public void testGettersAndSetters() {
        emailService.setHost("smtp.test.com");
        assertEquals("smtp.test.com", emailService.getHost());
        
        emailService.setPort("587");
        assertEquals("587", emailService.getPort());
        
        emailService.setUsername("testuser");
        assertEquals("testuser", emailService.getUsername());
        
        emailService.setPassword("testpass");
        assertEquals("testpass", emailService.getPassword());
        
        emailService.setFromAddr("sender@test.com");
        assertEquals("sender@test.com", emailService.getFromAddr());
    }

    @Test
    public void testSendEmailSingleRecipientExpectsConnectionFailure() {
        String recipient = "recipient@example.com";
        String subject = "Test Subject";
        String text = "Test message content";
        
        try {
            emailService.sendEMail(recipient, subject, text);
            fail("Should fail with connection error since no real SMTP server");
        } catch (Exception e) {
            // Expected to fail with connection error
            assertNotNull(e.getMessage(), "Should have exception message");
        }
    }

    @Test
    public void testSendEmailMultipleRecipientsExpectsConnectionFailure() {
        String[] recipients = {"recipient1@example.com", "recipient2@example.com"};
        String subject = "Test Subject";
        String text = "Test message content";
        String[] attachments = null;
        
        try {
            emailService.sendEMail(recipients, subject, text, attachments);
            fail("Should fail with connection error since no real SMTP server");
        } catch (Exception e) {
            // Expected to fail with connection error
            assertNotNull(e.getMessage(), "Should have exception message");
        }
    }

    @Test
    public void testSendEmailWithNonexistentAttachment() {
        String recipient = "recipient@example.com";
        String subject = "Test Subject";
        String text = "Test message content";
        String[] attachments = {"/nonexistent/file.txt"};
        
        try {
            emailService.sendEMail(recipient, subject, text, attachments);
            fail("Should fail due to connection or attachment error");
        } catch (Exception e) {
            // Should fail with connection error or attachment warning
            assertNotNull(e.getMessage(), "Should have exception message");
        }
    }

    @Test
    public void testSendEmailVarargsMethod() {
        String recipient = "recipient@example.com";
        String subject = "Test Subject";
        String text = "Test message content";
        
        try {
            emailService.sendEMail(recipient, subject, text, "file1.txt", "file2.txt");
            fail("Should fail with connection error");
        } catch (Exception e) {
            // Expected connection failure
            assertNotNull(e.getMessage(), "Should have exception message");
        }
    }

    @Test
    public void testSendEmailMultiVarargsMethod() {
        String[] recipients = {"recipient@example.com"};
        String subject = "Test Subject";
        String text = "Test message content";
        
        try {
            emailService.sendEMailMulti(recipients, subject, text, "attachment1.txt");
            fail("Should fail with connection error");
        } catch (Exception e) {
            // Expected connection failure
            assertNotNull(e.getMessage(), "Should have exception message");
        }
    }

    @Test
    public void testNullParameterHandling() {
        try {
            emailService.sendEMail(null, "subject", "text");
            fail("Should throw exception for null recipient");
        } catch (Exception e) {
            assertNotNull(e, "Should have exception for null recipient");
        }
    }

    @Test
    public void testEmptyStringParameterHandling() {
        try {
            emailService.sendEMail("", "subject", "text");
            fail("Should fail with invalid email address");
        } catch (Exception e) {
            assertNotNull(e, "Should have exception for empty recipient");
        }
    }

    @Test
    public void testPortConfiguration() {
        emailService.setPort("25");
        assertEquals("25", emailService.getPort());
        
        emailService.setPort("587");
        assertEquals("587", emailService.getPort());
        
        emailService.setPort("465");
        assertEquals("465", emailService.getPort());
        
        emailService.setPort("2525");
        assertEquals("2525", emailService.getPort());
    }

    @Test
    public void testHostConfiguration() {
        emailService.setHost("mail.google.com");
        assertEquals("mail.google.com", emailService.getHost());
        
        emailService.setHost("localhost");
        assertEquals("localhost", emailService.getHost());
        
        emailService.setHost("192.168.1.100");
        assertEquals("192.168.1.100", emailService.getHost());
    }

    @Test
    public void testAuthenticationConfiguration() {
        emailService.setUsername("authuser");
        emailService.setPassword("authpass");
        
        assertEquals("authuser", emailService.getUsername());
        assertEquals("authpass", emailService.getPassword());
        
        // Test null credentials
        emailService.setUsername(null);
        emailService.setPassword(null);
        
        assertNull(emailService.getUsername());
        assertNull(emailService.getPassword());
    }

    @Test
    public void testFromAddressConfiguration() {
        emailService.setFromAddr("noreply@company.com");
        assertEquals("noreply@company.com", emailService.getFromAddr());
        
        emailService.setFromAddr("admin@localhost");
        assertEquals("admin@localhost", emailService.getFromAddr());
    }
}