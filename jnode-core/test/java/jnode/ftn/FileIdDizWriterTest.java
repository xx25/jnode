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

package jnode.ftn;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Unit tests for FileIdDizWriter class.
 * Tests FILE_ID.DIZ generation according to BBS standards.
 */
public class FileIdDizWriterTest {
    
    private Path tempDir;
    private File testDirectory;
    
    @BeforeEach
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("fileid-test");
        testDirectory = tempDir.toFile();
    }
    
    @AfterEach
    public void tearDown() throws IOException {
        // Clean up temp directory
        Files.walk(tempDir)
            .sorted((a, b) -> b.compareTo(a))
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    // Ignore
                }
            });
    }
    
    @Test
    public void testAppendEntry_CreatesNewFile() throws IOException {
        String filename = "TEST.ZIP";
        String description = "Test archive file";
        
        FileIdDizWriter.appendEntry(testDirectory, filename, description);
        
        File fileIdDiz = new File(testDirectory, "FILE_ID.DIZ");
        assertTrue(fileIdDiz.exists());
        
        List<String> lines = Files.readAllLines(fileIdDiz.toPath(), StandardCharsets.US_ASCII);
        assertEquals(1, lines.size());
        assertEquals("TEST.ZIP Test archive file", lines.get(0));
    }
    
    @Test
    public void testAppendEntry_AppendsToExistingFile() throws IOException {
        // Create initial entry
        FileIdDizWriter.appendEntry(testDirectory, "FIRST.ZIP", "First file");
        
        // Append second entry
        FileIdDizWriter.appendEntry(testDirectory, "SECOND.ZIP", "Second file");
        
        File fileIdDiz = new File(testDirectory, "FILE_ID.DIZ");
        List<String> lines = Files.readAllLines(fileIdDiz.toPath(), StandardCharsets.US_ASCII);
        
        assertEquals(2, lines.size());
        assertEquals("FIRST.ZIP First file", lines.get(0));
        assertEquals("SECOND.ZIP Second file", lines.get(1));
    }
    
    @Test
    public void testAppendEntry_HandlesLongFilename() throws IOException {
        String longFilename = "VERYLONGFILENAME.ZIP";
        String description = "Description";
        
        FileIdDizWriter.appendEntry(testDirectory, longFilename, description);
        
        File fileIdDiz = new File(testDirectory, "FILE_ID.DIZ");
        List<String> lines = Files.readAllLines(fileIdDiz.toPath(), StandardCharsets.US_ASCII);
        
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).length() <= 45);
        assertTrue(lines.get(0).startsWith(longFilename));
    }
    
    @Test
    public void testAppendEntry_HandlesMultilineDescription() throws IOException {
        String filename = "MULTI.ZIP";
        String description = "Line 1 of description\nLine 2 of description\nLine 3 of description";
        
        FileIdDizWriter.appendEntry(testDirectory, filename, description);
        
        File fileIdDiz = new File(testDirectory, "FILE_ID.DIZ");
        List<String> lines = Files.readAllLines(fileIdDiz.toPath(), StandardCharsets.US_ASCII);
        
        assertTrue(lines.size() > 1);
        assertTrue(lines.get(0).startsWith(filename));
        assertTrue(lines.get(0).contains("Line 1"));
        assertEquals("Line 2 of description", lines.get(1).trim());
        assertEquals("Line 3 of description", lines.get(2).trim());
    }
    
    @Test
    public void testAppendEntry_RespectsMaxLines() throws IOException {
        String filename = "MANY.ZIP";
        StringBuilder description = new StringBuilder();
        for (int i = 1; i <= 15; i++) {
            description.append("Line ").append(i).append(" of description\n");
        }
        
        FileIdDizWriter.appendEntry(testDirectory, filename, description.toString());
        
        File fileIdDiz = new File(testDirectory, "FILE_ID.DIZ");
        List<String> lines = Files.readAllLines(fileIdDiz.toPath(), StandardCharsets.US_ASCII);
        
        assertTrue(lines.size() <= 10);
    }
    
    @Test
    public void testAppendEntry_HandlesVeryLongLine() throws IOException {
        String filename = "LONG.ZIP";
        String longLine = "This is a very long description line that exceeds the maximum allowed length of 45 characters per line and should be split";
        
        FileIdDizWriter.appendEntry(testDirectory, filename, longLine);
        
        File fileIdDiz = new File(testDirectory, "FILE_ID.DIZ");
        List<String> lines = Files.readAllLines(fileIdDiz.toPath(), StandardCharsets.US_ASCII);
        
        assertTrue(lines.size() > 1);
        for (String line : lines) {
            assertTrue(line.length() <= 45);
        }
    }
    
    @Test
    public void testAppendEntry_HandlesNullDescription() throws IOException {
        String filename = "NULL.ZIP";
        
        FileIdDizWriter.appendEntry(testDirectory, filename, null);
        
        File fileIdDiz = new File(testDirectory, "FILE_ID.DIZ");
        List<String> lines = Files.readAllLines(fileIdDiz.toPath(), StandardCharsets.US_ASCII);
        
        assertEquals(1, lines.size());
        assertEquals("NULL.ZIP", lines.get(0));
    }
    
    @Test
    public void testAppendEntry_HandlesEmptyDescription() throws IOException {
        String filename = "EMPTY.ZIP";
        
        FileIdDizWriter.appendEntry(testDirectory, filename, "");
        
        File fileIdDiz = new File(testDirectory, "FILE_ID.DIZ");
        List<String> lines = Files.readAllLines(fileIdDiz.toPath(), StandardCharsets.US_ASCII);
        
        assertEquals(1, lines.size());
        assertEquals("EMPTY.ZIP", lines.get(0));
    }
    
    @Test
    public void testAppendEntry_RejectsNullDirectory() {
        assertThrows(IllegalArgumentException.class, () ->
            FileIdDizWriter.appendEntry(null, "test.zip", "description"));
    }
    
    @Test
    public void testAppendEntry_RejectsNonDirectory() throws IOException {
        File notDirectory = new File(testDirectory, "notadir.txt");
        Files.write(notDirectory.toPath(), "test".getBytes());
        
        assertThrows(IllegalArgumentException.class, () -> 
            FileIdDizWriter.appendEntry(notDirectory, "test.zip", "description"));
    }
    
    @Test
    public void testAppendEntry_RejectsNullFilename() {
        assertThrows(IllegalArgumentException.class, () ->
            FileIdDizWriter.appendEntry(testDirectory, null, "description"));
    }
    
    @Test
    public void testAppendEntry_RejectsEmptyFilename() {
        assertThrows(IllegalArgumentException.class, () ->
            FileIdDizWriter.appendEntry(testDirectory, "", "description"));
    }
    
    @Test
    public void testAppendEntry_With8BitCharset() throws IOException {
        String filename = "CYRILLIC.ZIP";
        String description = "Test file"; // Simple ASCII for this test
        
        FileIdDizWriter.appendEntry(testDirectory, filename, description, true, "CP866");
        
        File fileIdDiz = new File(testDirectory, "FILE_ID.DIZ");
        assertTrue(fileIdDiz.exists());
        
        // Verify content can be read
        List<String> lines = Files.readAllLines(fileIdDiz.toPath(), StandardCharsets.US_ASCII);
        assertEquals(1, lines.size());
    }
    
    @Test
    public void testAppendEntry_FallbackOnInvalidCharset() throws IOException {
        String filename = "FALLBACK.ZIP";
        String description = "Test fallback";
        
        // Should fall back to US-ASCII on invalid charset
        FileIdDizWriter.appendEntry(testDirectory, filename, description, true, "INVALID-CHARSET");
        
        File fileIdDiz = new File(testDirectory, "FILE_ID.DIZ");
        List<String> lines = Files.readAllLines(fileIdDiz.toPath(), StandardCharsets.US_ASCII);
        
        assertEquals(1, lines.size());
        assertEquals("FALLBACK.ZIP Test fallback", lines.get(0));
    }
    
    @Test
    public void testFileIdDizExists_ReturnsTrueWhenExists() throws IOException {
        File fileIdDiz = new File(testDirectory, "FILE_ID.DIZ");
        Files.write(fileIdDiz.toPath(), "test".getBytes());
        
        assertTrue(FileIdDizWriter.fileIdDizExists(testDirectory));
    }
    
    @Test
    public void testFileIdDizExists_ReturnsFalseWhenNotExists() {
        assertFalse(FileIdDizWriter.fileIdDizExists(testDirectory));
    }
    
    @Test
    public void testFileIdDizExists_ReturnsFalseForNullDirectory() {
        assertFalse(FileIdDizWriter.fileIdDizExists(null));
    }
    
    @Test
    public void testFileIdDizExists_ReturnsFalseForNonDirectory() throws IOException {
        File notDirectory = new File(testDirectory, "notadir.txt");
        Files.write(notDirectory.toPath(), "test".getBytes());
        
        assertFalse(FileIdDizWriter.fileIdDizExists(notDirectory));
    }
    
    @Test
    public void testWordWrapping() throws IOException {
        String filename = "WRAP.ZIP";
        String description = "This is a line that should be wrapped at a word boundary rather than in the middle";
        
        FileIdDizWriter.appendEntry(testDirectory, filename, description);
        
        File fileIdDiz = new File(testDirectory, "FILE_ID.DIZ");
        List<String> lines = Files.readAllLines(fileIdDiz.toPath(), StandardCharsets.US_ASCII);
        
        // Check that words are not split across lines
        for (String line : lines) {
            assertTrue(line.length() <= 45);
            if (line.trim().length() > 0) {
                assertFalse(line.trim().endsWith("-"));
            }
        }
    }
    
    @Test
    public void testConcurrentAppends() throws Exception {
        // Test thread safety of synchronized append
        Thread t1 = new Thread(() -> {
            try {
                FileIdDizWriter.appendEntry(testDirectory, "THREAD1.ZIP", "From thread 1");
            } catch (IOException e) {
                fail(e.getMessage());
            }
        });
        
        Thread t2 = new Thread(() -> {
            try {
                FileIdDizWriter.appendEntry(testDirectory, "THREAD2.ZIP", "From thread 2");
            } catch (IOException e) {
                fail(e.getMessage());
            }
        });
        
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        
        File fileIdDiz = new File(testDirectory, "FILE_ID.DIZ");
        List<String> lines = Files.readAllLines(fileIdDiz.toPath(), StandardCharsets.US_ASCII);
        
        assertEquals(2, lines.size());
        assertTrue(lines.stream().anyMatch(l -> l.contains("THREAD1.ZIP")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("THREAD2.ZIP")));
    }
    
    @Test
    public void testAppendEntry_ReplacesDuplicateFilename() throws IOException {
        String filename = "DUPLICATE.ZIP";
        
        // Add initial entry
        FileIdDizWriter.appendEntry(testDirectory, filename, "Original description");
        
        File fileIdDiz = new File(testDirectory, "FILE_ID.DIZ");
        List<String> lines = Files.readAllLines(fileIdDiz.toPath(), StandardCharsets.US_ASCII);
        assertEquals(1, lines.size());
        assertEquals("DUPLICATE.ZIP Original description", lines.get(0));
        
        // Add same filename again with different description - should replace
        FileIdDizWriter.appendEntry(testDirectory, filename, "Updated description");
        
        lines = Files.readAllLines(fileIdDiz.toPath(), StandardCharsets.US_ASCII);
        assertEquals(1, lines.size());
        assertEquals("DUPLICATE.ZIP Updated description", lines.get(0));
    }
}