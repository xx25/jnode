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
 * Unit tests for FilesBBSWriter class.
 * Tests FILES.BBS generation according to BBS standards.
 */
public class FilesBBSWriterTest {
    
    private Path tempDir;
    private File testDirectory;
    
    @BeforeEach
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("filesbbs-test");
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
        
        FilesBBSWriter.appendEntry(testDirectory, filename, description);
        
        File filesBbs = new File(testDirectory, "files.bbs");
        assertTrue(filesBbs.exists());
        
        List<String> lines = Files.readAllLines(filesBbs.toPath(), StandardCharsets.US_ASCII);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).startsWith(filename));
        assertTrue(lines.get(0).contains("Test archive file"));
    }
    
    @Test
    public void testAppendEntry_AppendsToExistingFile() throws IOException {
        // Create initial entry
        FilesBBSWriter.appendEntry(testDirectory, "FIRST.ZIP", "First file");
        
        // Append second entry
        FilesBBSWriter.appendEntry(testDirectory, "SECOND.ZIP", "Second file");
        
        File filesBbs = new File(testDirectory, "files.bbs");
        List<String> lines = Files.readAllLines(filesBbs.toPath(), StandardCharsets.US_ASCII);
        
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).startsWith("FIRST.ZIP"));
        assertTrue(lines.get(1).startsWith("SECOND.ZIP"));
    }
    
    @Test
    public void testAppendEntry_FormatsWithProperSpacing() throws IOException {
        String filename = "SHORT.ZIP";
        String description = "Description text";
        
        FilesBBSWriter.appendEntry(testDirectory, filename, description);
        
        File filesBbs = new File(testDirectory, "files.bbs");
        List<String> lines = Files.readAllLines(filesBbs.toPath(), StandardCharsets.US_ASCII);
        
        assertEquals(1, lines.size());
        String line = lines.get(0);
        
        // Check that description is properly spaced from filename
        assertTrue(line.contains(filename + " "));
        int descStart = line.indexOf(description);
        assertTrue(descStart >= 13); // MAX_FILENAME_LENGTH + 1
    }
    
    @Test
    public void testAppendEntry_HandlesLongFilename() throws IOException {
        String longFilename = "VERYLONGFILENAME.ZIP";
        String description = "Description";
        
        FilesBBSWriter.appendEntry(testDirectory, longFilename, description);
        
        File filesBbs = new File(testDirectory, "files.bbs");
        List<String> lines = Files.readAllLines(filesBbs.toPath(), StandardCharsets.US_ASCII);
        
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).startsWith(longFilename));
        assertTrue(lines.get(0).contains(description));
    }
    
    @Test
    public void testAppendEntry_HandlesMultilineDescription() throws IOException {
        String filename = "MULTI.ZIP";
        String description = "Line 1 of description\nLine 2 of description\nLine 3 of description";
        
        FilesBBSWriter.appendEntry(testDirectory, filename, description);
        
        File filesBbs = new File(testDirectory, "files.bbs");
        List<String> lines = Files.readAllLines(filesBbs.toPath(), StandardCharsets.US_ASCII);
        
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).startsWith(filename));
        assertTrue(lines.get(0).contains("Line 1"));
        assertTrue(lines.get(1).startsWith(" "));
        assertTrue(lines.get(1).contains("Line 2"));
        assertTrue(lines.get(2).startsWith(" "));
        assertTrue(lines.get(2).contains("Line 3"));
    }
    
    @Test
    public void testAppendEntry_TruncatesLongLines() throws IOException {
        String filename = "LONG.ZIP";
        String veryLongDesc = "This is an extremely long description that definitely exceeds the maximum line length of 80 characters and should be truncated";
        
        FilesBBSWriter.appendEntry(testDirectory, filename, veryLongDesc);
        
        File filesBbs = new File(testDirectory, "files.bbs");
        List<String> lines = Files.readAllLines(filesBbs.toPath(), StandardCharsets.US_ASCII);
        
        for (String line : lines) {
            assertTrue(line.length() <= 80);
        }
    }
    
    @Test
    public void testAppendEntry_HandlesNullDescription() throws IOException {
        String filename = "NULL.ZIP";
        
        FilesBBSWriter.appendEntry(testDirectory, filename, null);
        
        File filesBbs = new File(testDirectory, "files.bbs");
        List<String> lines = Files.readAllLines(filesBbs.toPath(), StandardCharsets.US_ASCII);
        
        assertEquals(1, lines.size());
        assertEquals("NULL.ZIP", lines.get(0));
    }
    
    @Test
    public void testAppendEntry_HandlesEmptyDescription() throws IOException {
        String filename = "EMPTY.ZIP";
        
        FilesBBSWriter.appendEntry(testDirectory, filename, "");
        
        File filesBbs = new File(testDirectory, "files.bbs");
        List<String> lines = Files.readAllLines(filesBbs.toPath(), StandardCharsets.US_ASCII);
        
        assertEquals(1, lines.size());
        assertEquals("EMPTY.ZIP", lines.get(0));
    }
    
    @Test
    public void testAppendEntry_RejectsNullDirectory() {
        assertThrows(IllegalArgumentException.class, () ->
            FilesBBSWriter.appendEntry(null, "test.zip", "description"));
    }
    
    @Test
    public void testAppendEntry_RejectsNonDirectory() throws IOException {
        File notDirectory = new File(testDirectory, "notadir.txt");
        Files.write(notDirectory.toPath(), "test".getBytes());
        
        assertThrows(IllegalArgumentException.class, () -> 
            FilesBBSWriter.appendEntry(notDirectory, "test.zip", "description"));
    }
    
    @Test
    public void testAppendEntry_RejectsNullFilename() {
        assertThrows(IllegalArgumentException.class, () ->
            FilesBBSWriter.appendEntry(testDirectory, null, "description"));
    }
    
    @Test
    public void testAppendEntry_RejectsEmptyFilename() {
        assertThrows(IllegalArgumentException.class, () ->
            FilesBBSWriter.appendEntry(testDirectory, "", "description"));
    }
    
    @Test
    public void testAppendEntry_With8BitCharset() throws IOException {
        String filename = "CYRILLIC.ZIP";
        String description = "Test file"; // Simple ASCII for this test
        
        FilesBBSWriter.appendEntry(testDirectory, filename, description, true, "CP866");
        
        File filesBbs = new File(testDirectory, "files.bbs");
        assertTrue(filesBbs.exists());
        
        // Verify content can be read
        List<String> lines = Files.readAllLines(filesBbs.toPath(), StandardCharsets.US_ASCII);
        assertEquals(1, lines.size());
    }
    
    @Test
    public void testAppendEntry_FallbackOnInvalidCharset() throws IOException {
        String filename = "FALLBACK.ZIP";
        String description = "Test fallback";
        
        // Should fall back to US-ASCII on invalid charset
        FilesBBSWriter.appendEntry(testDirectory, filename, description, true, "INVALID-CHARSET");
        
        File filesBbs = new File(testDirectory, "files.bbs");
        List<String> lines = Files.readAllLines(filesBbs.toPath(), StandardCharsets.US_ASCII);
        
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).startsWith("FALLBACK.ZIP"));
        assertTrue(lines.get(0).contains("Test fallback"));
    }
    
    @Test
    public void testContinuationLineFormatting() throws IOException {
        String filename = "CONT.ZIP";
        String description = "First line\n\nThird line"; // Empty second line
        
        FilesBBSWriter.appendEntry(testDirectory, filename, description);
        
        File filesBbs = new File(testDirectory, "files.bbs");
        List<String> lines = Files.readAllLines(filesBbs.toPath(), StandardCharsets.US_ASCII);
        
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).startsWith(filename));
        assertEquals(" ", lines.get(1));
        assertTrue(lines.get(2).startsWith(" "));
        assertTrue(lines.get(2).contains("Third line"));
    }
    
    @Test
    public void testLongContinuationLines() throws IOException {
        String filename = "LONGCONT.ZIP";
        String longLine = "This is a very long continuation line that exceeds the maximum allowed length and should be truncated to fit within 80 characters";
        String description = "First line\n" + longLine;
        
        FilesBBSWriter.appendEntry(testDirectory, filename, description);
        
        File filesBbs = new File(testDirectory, "files.bbs");
        List<String> lines = Files.readAllLines(filesBbs.toPath(), StandardCharsets.US_ASCII);
        
        for (String line : lines) {
            assertTrue(line.length() <= 80);
        }
        
        // Check continuation line starts with space
        assertTrue(lines.get(1).startsWith(" "));
    }
    
    @Test
    public void testConcurrentAppends() throws Exception {
        // Test thread safety of synchronized append
        Thread t1 = new Thread(() -> {
            try {
                FilesBBSWriter.appendEntry(testDirectory, "THREAD1.ZIP", "From thread 1");
            } catch (IOException e) {
                fail(e.getMessage());
            }
        });
        
        Thread t2 = new Thread(() -> {
            try {
                FilesBBSWriter.appendEntry(testDirectory, "THREAD2.ZIP", "From thread 2");
            } catch (IOException e) {
                fail(e.getMessage());
            }
        });
        
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        
        File filesBbs = new File(testDirectory, "files.bbs");
        List<String> lines = Files.readAllLines(filesBbs.toPath(), StandardCharsets.US_ASCII);
        
        assertEquals(2, lines.size());
        assertTrue(lines.stream().anyMatch(l -> l.contains("THREAD1.ZIP")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("THREAD2.ZIP")));
    }
    
    @Test
    public void testDescriptionAlignment() throws IOException {
        // Test multiple files to ensure consistent alignment
        FilesBBSWriter.appendEntry(testDirectory, "A.ZIP", "Short filename");
        FilesBBSWriter.appendEntry(testDirectory, "MEDIUM.ZIP", "Medium filename");
        FilesBBSWriter.appendEntry(testDirectory, "VERYLONGNAME.ZIP", "Long filename");
        
        File filesBbs = new File(testDirectory, "files.bbs");
        List<String> lines = Files.readAllLines(filesBbs.toPath(), StandardCharsets.US_ASCII);
        
        assertEquals(3, lines.size());
        
        // Check that descriptions start at consistent positions
        int shortDesc = lines.get(0).indexOf("Short");
        int mediumDesc = lines.get(1).indexOf("Medium");
        
        // Short filenames should have descriptions at same position
        assertEquals(shortDesc, mediumDesc);
        
        // Long filename may have description immediately after
        String longLine = lines.get(2);
        assertTrue(longLine.contains("Long filename"));
    }
    
    @Test
    public void testAppendEntry_ReplacesDuplicateFilename() throws IOException {
        String filename = "DUPLICATE.ZIP";
        
        // Add initial entry
        FilesBBSWriter.appendEntry(testDirectory, filename, "Original description");
        
        File filesBbs = new File(testDirectory, "files.bbs");
        List<String> lines = Files.readAllLines(filesBbs.toPath(), StandardCharsets.US_ASCII);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("Original description"));
        
        // Add same filename again with different description - should replace
        FilesBBSWriter.appendEntry(testDirectory, filename, "Updated description");
        
        lines = Files.readAllLines(filesBbs.toPath(), StandardCharsets.US_ASCII);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("Updated description"));
        assertFalse(lines.get(0).contains("Original description"));
        assertTrue(lines.get(0).startsWith(filename));
    }
}