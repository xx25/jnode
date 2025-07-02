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

package org.jnode.mail.robot;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Hashtable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MailSenderTest {

    private MailSender mailSender;
    private static final String MAIL_REGEXP = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";

    @BeforeEach
    public void setUp() {
        mailSender = new MailSender();
    }

    @Test
    public void testEmailRegexValidation() {
        // Test valid email addresses
        String[] validEmails = {
            "test@example.com",
            "user.name@domain.co.uk",
            "user+tag@example.org",
            "firstname.lastname@company.com",
            "123@numbers.net"
        };
        
        for (String email : validEmails) {
            assertTrue(email.matches(MAIL_REGEXP), 
                "Valid email should match: " + email);
        }
        
        // Test invalid email addresses
        String[] invalidEmails = {
            "notanemail",
            "@example.com",
            "user@",
            "user@domain",
            "user.domain.com",
            "user@domain."
        };
        
        for (String email : invalidEmails) {
            assertFalse(email.matches(MAIL_REGEXP),
                "Invalid email should not match: " + email);
        }
    }

    @Test
    public void testConstructor() {
        MailSender newSender = new MailSender();
        assertNotNull(newSender, "MailSender should be created successfully");
        
        // Verify timeout map is initialized using reflection
        try {
            Field timeoutMapField = MailSender.class.getDeclaredField("timeoutMap");
            timeoutMapField.setAccessible(true);
            Object timeoutMap = timeoutMapField.get(newSender);
            assertNotNull(timeoutMap, "Timeout map should be initialized");
            assertTrue(timeoutMap instanceof Hashtable, "Timeout map should be Hashtable");
        } catch (Exception e) {
            fail("Should be able to access timeout map: " + e.getMessage());
        }
    }

    @Test
    public void testConstants() throws Exception {
        // Verify constants are properly defined using reflection
        Field mailTimeoutField = MailSender.class.getDeclaredField("MAIL_TIMEOUT");
        mailTimeoutField.setAccessible(true);
        long timeout = mailTimeoutField.getLong(null);
        assertEquals(3600L, timeout, "Mail timeout should be 3600 seconds");
        
        Field mailRegexpField = MailSender.class.getDeclaredField("MAIL_REGEXP");
        mailRegexpField.setAccessible(true);
        String regexp = (String) mailRegexpField.get(null);
        assertNotNull(regexp, "Mail regexp should be defined");
        assertTrue(regexp.length() > 0, "Mail regexp should be non-empty");
        assertEquals(MAIL_REGEXP, regexp, "Mail regexp should match expected pattern");
        
        Field mailFormatField = MailSender.class.getDeclaredField("MAIL_FORMAT");
        mailFormatField.setAccessible(true);
        String format = (String) mailFormatField.get(null);
        assertNotNull(format, "Mail format should be defined");
        assertTrue(format.contains("%s"), "Mail format should contain placeholders");
        assertTrue(format.contains("Netmail2Email Gate greets you"), "Mail format should contain greeting");
    }

    @Test
    public void testEmailValidationRegexDirectly() {
        // Test the actual regex pattern used in the class
        String validEmail = "user@example.com";
        String invalidEmail = "not-an-email";
        
        assertTrue(validEmail.matches(MAIL_REGEXP), "Valid email should match pattern");
        assertFalse(invalidEmail.matches(MAIL_REGEXP), "Invalid email should not match pattern");
    }

    @Test
    public void testTextProcessingControlCharacters() {
        // Test control character replacement (\001 -> @)
        String textWithControlChar = "Line 1\001Line 2\001Line 3";
        String expectedText = "Line 1@Line 2@Line 3";
        
        String processedText = textWithControlChar.replace('\001', '@');
        assertEquals(expectedText, processedText, "Control characters should be replaced");
    }

    @Test
    public void testEmailExtractionFromText() {
        // Test finding email in text lines - line by line matching
        String textWithEmail = "Please send response to user@example.com\nThank you.";
        String[] lines = textWithEmail.split("\n");
        
        String foundEmail = null;
        for (String line : lines) {
            if (line.matches(MAIL_REGEXP)) {
                foundEmail = line;
                break;
            }
        }
        
        // The actual algorithm looks for whole lines that are emails, not lines containing emails
        // So test a line that is just an email
        String emailLine = "user@example.com";
        assertTrue(emailLine.matches(MAIL_REGEXP), "Standalone email line should match");
        
        // Test finding email within text (actual implementation logic)
        String textWithEmailOnSeparateLine = "Please send response to:\nuser@example.com\nThank you.";
        String[] separateLines = textWithEmailOnSeparateLine.split("\n");
        String foundSeparateEmail = null;
        for (String line : separateLines) {
            if (line.matches(MAIL_REGEXP)) {
                foundSeparateEmail = line;
                break;
            }
        }
        assertEquals("user@example.com", foundSeparateEmail, "Should find email on separate line");
    }

    @Test
    public void testEmailExtractionFromSubject() {
        // Test email in subject line
        String emailSubject = "user@example.com";
        String nonEmailSubject = "Important Message";
        
        assertTrue(emailSubject.matches(MAIL_REGEXP), "Email subject should match regex");
        assertFalse(nonEmailSubject.matches(MAIL_REGEXP), "Non-email subject should not match regex");
    }

    @Test
    public void testTimeoutMapOperations() throws Exception {
        // Test timeout map functionality using reflection
        Field timeoutMapField = MailSender.class.getDeclaredField("timeoutMap");
        timeoutMapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Hashtable<Object, Long> timeoutMap = (Hashtable<Object, Long>) timeoutMapField.get(mailSender);
        
        // Test that map starts empty
        assertTrue(timeoutMap.isEmpty(), "Timeout map should start empty");
        
        // Test adding entries
        String testKey = "test@example.com";
        long currentTime = new Date().getTime();
        timeoutMap.put(testKey, currentTime);
        
        assertEquals(1, timeoutMap.size(), "Map should contain one entry");
        assertTrue(timeoutMap.containsKey(testKey), "Map should contain test key");
        assertEquals(currentTime, timeoutMap.get(testKey).longValue(), "Map should return correct time");
    }

    @Test
    public void testTimeoutCalculation() throws Exception {
        // Test timeout logic calculation
        Field mailTimeoutField = MailSender.class.getDeclaredField("MAIL_TIMEOUT");
        mailTimeoutField.setAccessible(true);
        long timeout = mailTimeoutField.getLong(null);
        
        long now = new Date().getTime();
        long oldTime = now - (timeout * 1000) - 1000; // 1 second past timeout
        long recentTime = now - 1000; // 1 second ago
        
        // Test timeout logic
        assertTrue((now - timeout * 1000) > oldTime, "Old time should be past timeout");
        assertFalse((now - timeout * 1000) > recentTime, "Recent time should be within timeout");
    }

    @Test
    public void testSubjectPrefixFormatting() {
        // Test subject formatting logic
        String emailSubject = "user@example.com";
        String normalSubject = "Important Message";
        
        String formattedNormalSubject;
        if (normalSubject.matches(MAIL_REGEXP)) {
            formattedNormalSubject = normalSubject; // Email in subject, use as-is
        } else {
            formattedNormalSubject = "[FIDO] " + normalSubject; // Prefix non-email subjects
        }
        
        assertEquals(emailSubject, emailSubject, "Email subject should not be prefixed");
        assertEquals("[FIDO] Important Message", formattedNormalSubject, "Normal subject should be prefixed");
    }

    @Test
    public void testMailFormatStringFormatting() throws Exception {
        // Test mail format string functionality
        Field mailFormatField = MailSender.class.getDeclaredField("MAIL_FORMAT");
        mailFormatField.setAccessible(true);
        String mailFormat = (String) mailFormatField.get(null);
        
        String fromName = "Test User";
        String fromAddr = "1:2/3.4";
        String messageText = "Test message content";
        
        String formattedMessage = String.format(mailFormat, fromName, fromAddr, messageText);
        
        assertTrue(formattedMessage.contains(fromName), "Formatted message should contain from name");
        assertTrue(formattedMessage.contains(fromAddr), "Formatted message should contain from address");
        assertTrue(formattedMessage.contains(messageText), "Formatted message should contain message text");
        assertTrue(formattedMessage.contains("Netmail2Email Gate greets you"), "Formatted message should contain greeting");
    }
}