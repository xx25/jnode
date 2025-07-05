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

package jnode.protocol.binkp.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for FilenameEscaper
 * 
 * Tests BinkP filename escaping according to FTS-1026 specification:
 * - Space and backslash MUST be escaped  
 * - Escape format: \xHH (two hex digits, lowercase preferred)
 * - Compatibility: Accept \20 format for space
 * - Non-printable characters should be escaped
 * 
 * @author jNode Team
 */
public class FilenameEscaperTest {

    @Test
    void testEscapeSpace() {
        assertEquals("my\\x20file.txt", FilenameEscaper.escape("my file.txt"));
        assertEquals("test\\x20with\\x20spaces.dat", FilenameEscaper.escape("test with spaces.dat"));
    }

    @Test
    void testEscapeBackslash() {
        assertEquals("path\\x5cfile.txt", FilenameEscaper.escape("path\\file.txt"));
        assertEquals("C:\\x5cUsers\\x5ctest.txt", FilenameEscaper.escape("C:\\Users\\test.txt"));
    }

    @Test
    void testEscapeControlCharacters() {
        assertEquals("file\\x00name.txt", FilenameEscaper.escape("file\0name.txt"));
        assertEquals("test\\x09tab.txt", FilenameEscaper.escape("test\ttab.txt"));
        assertEquals("new\\x0aline.txt", FilenameEscaper.escape("new\nline.txt"));
        assertEquals("carriage\\x0dreturn.txt", FilenameEscaper.escape("carriage\rreturn.txt"));
    }

    @Test
    void testEscapeExtendedASCII() {
        assertEquals("test\\x7fname.txt", FilenameEscaper.escape("test\u007fname.txt"));
        assertEquals("file\\x80name.txt", FilenameEscaper.escape("file\u0080name.txt"));
        assertEquals("unicode\\xfftest.txt", FilenameEscaper.escape("unicode\u00fftest.txt"));
    }

    @Test
    void testEscapeUnsafeCharacters() {
        // According to BinkP spec, only space and backslash MUST be escaped
        // Other characters like quotes, pipes etc. are optional and we're being conservative
        assertEquals("file\"name.txt", FilenameEscaper.escape("file\"name.txt"));
        assertEquals("test'file.txt", FilenameEscaper.escape("test'file.txt"));
        assertEquals("pipe|test.txt", FilenameEscaper.escape("pipe|test.txt"));
        assertEquals("question?mark.txt", FilenameEscaper.escape("question?mark.txt"));
        assertEquals("asterisk*test.txt", FilenameEscaper.escape("asterisk*test.txt"));
    }

    @Test
    void testNoEscapingNeeded() {
        assertEquals("normalfile.txt", FilenameEscaper.escape("normalfile.txt"));
        assertEquals("test123.dat", FilenameEscaper.escape("test123.dat"));
        assertEquals("file_with-dashes.txt", FilenameEscaper.escape("file_with-dashes.txt"));
        assertEquals("UPPERCASE.TXT", FilenameEscaper.escape("UPPERCASE.TXT"));
    }

    @Test
    void testUnescapeStandardFormat() {
        assertEquals("my file.txt", FilenameEscaper.unescape("my\\x20file.txt"));
        assertEquals("path\\file.txt", FilenameEscaper.unescape("path\\x5cfile.txt"));
        assertEquals("test\ttab.txt", FilenameEscaper.unescape("test\\x09tab.txt"));
    }

    @Test
    void testUnescapeCompatibilityFormat() {
        assertEquals("my file.txt", FilenameEscaper.unescape("my\\20file.txt"));
        assertEquals("test\ttab.txt", FilenameEscaper.unescape("test\\09tab.txt"));
    }

    @Test
    void testUnescapeMixedFormats() {
        assertEquals("my file\\path.txt", FilenameEscaper.unescape("my\\x20file\\x5cpath.txt"));
        assertEquals("old new.txt", FilenameEscaper.unescape("old\\20new.txt"));
    }

    @Test
    void testUnescapeNoEscaping() {
        assertEquals("normalfile.txt", FilenameEscaper.unescape("normalfile.txt"));
        assertEquals("test123.dat", FilenameEscaper.unescape("test123.dat"));
    }

    @Test
    void testUnescapeInvalidSequences() {
        // Invalid hex sequences should be treated as literals
        assertEquals("test\\xGGfile.txt", FilenameEscaper.unescape("test\\xGGfile.txt"));
        assertEquals("bad\\x1gfile.txt", FilenameEscaper.unescape("bad\\x1gfile.txt")); // \x1g is incomplete (g is not hex), treated as literal
        assertEquals("incomplete\\x", FilenameEscaper.unescape("incomplete\\x"));
        
        // Invalid numeric sequences - these are not recognized as escape sequences
        assertEquals("test\\99file.txt", FilenameEscaper.unescape("test\\99file.txt"));
        assertEquals("bad\\256file.txt", FilenameEscaper.unescape("bad\\256file.txt"));
    }

    @Test
    void testRoundTripConversion() {
        String[] testFilenames = {
            "my file.txt",
            "path\\file.txt", 
            "test\ttab.txt",
            "quote\"file.txt",
            "pipe|test.txt",
            "normalfile.txt",
            "file with spaces and\\backslashes.txt"
        };
        
        for (String original : testFilenames) {
            String escaped = FilenameEscaper.escape(original);
            String unescaped = FilenameEscaper.unescape(escaped);
            assertEquals(original, unescaped, "Round trip failed for: " + original);
        }
    }

    @Test
    void testIsProperlyEscapedValid() {
        assertTrue(FilenameEscaper.isProperlyEscaped("normalfile.txt"));
        assertTrue(FilenameEscaper.isProperlyEscaped("my\\x20file.txt"));
        assertTrue(FilenameEscaper.isProperlyEscaped("path\\x5cfile.txt"));
        assertTrue(FilenameEscaper.isProperlyEscaped("test\\20file.txt")); // Compatibility format
        assertTrue(FilenameEscaper.isProperlyEscaped("mixed\\20and\\09formats.txt"));
    }

    @Test
    void testIsProperlyEscapedInvalid() {
        assertFalse(FilenameEscaper.isProperlyEscaped("my file.txt")); // Unescaped space
        assertFalse(FilenameEscaper.isProperlyEscaped("path\\file.txt")); // Unescaped backslash
        assertFalse(FilenameEscaper.isProperlyEscaped("test\ttab.txt")); // Unescaped tab
        // Note: quotes are now considered valid (not required to be escaped)
        assertFalse(FilenameEscaper.isProperlyEscaped("test\\xGGfile.txt")); // Invalid hex
        assertFalse(FilenameEscaper.isProperlyEscaped("bad\\x1gfile.txt")); // Incomplete hex
        assertFalse(FilenameEscaper.isProperlyEscaped("test\\999file.txt")); // Invalid number
    }

    @Test
    void testNullHandling() {
        assertNull(FilenameEscaper.escape(null));
        assertNull(FilenameEscaper.unescape(null));
        assertFalse(FilenameEscaper.isProperlyEscaped(null));
    }

    @Test
    void testEmptyString() {
        assertEquals("", FilenameEscaper.escape(""));
        assertEquals("", FilenameEscaper.unescape(""));
        assertTrue(FilenameEscaper.isProperlyEscaped(""));
    }

    @Test
    void testBinkPSpecificationExamples() {
        // Examples from BinkP specification
        assertEquals("my\\x20file.txt", FilenameEscaper.escape("my file.txt"));
        assertEquals("path\\x5cfile.txt", FilenameEscaper.escape("path\\file.txt"));
        assertEquals("file@domain.txt", FilenameEscaper.escape("file@domain.txt")); // @ is safe
        
        // Both \x20 and \20 should be accepted for space
        assertEquals("my file.txt", FilenameEscaper.unescape("my\\x20file.txt"));
        assertEquals("my file.txt", FilenameEscaper.unescape("my\\20file.txt"));
    }

    @Test
    void testSpecialCharactersAtBoundaries() {
        assertEquals("\\x20startswithspace", FilenameEscaper.escape(" startswithspace"));
        assertEquals("endswithspace\\x20", FilenameEscaper.escape("endswithspace "));
        assertEquals("\\x5cstartswithbackslash", FilenameEscaper.escape("\\startswithbackslash"));
        assertEquals("endswithbackslash\\x5c", FilenameEscaper.escape("endswithbackslash\\"));
    }

    @Test
    void testLongFilenames() {
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longName.append("test ");
        }
        String original = longName.toString();
        String escaped = FilenameEscaper.escape(original);
        String unescaped = FilenameEscaper.unescape(escaped);
        
        assertEquals(original, unescaped);
        assertTrue(escaped.contains("\\x20"));
        assertFalse(escaped.contains(" "));
    }
}