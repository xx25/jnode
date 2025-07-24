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

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import jnode.logger.Logger;

/**
 * Utility class for writing FILE_ID.DIZ files in FTN file echo directories.
 * FILE_ID.DIZ is a standard BBS file description format with specific constraints:
 * - Maximum 10 lines of text
 * - Maximum 45 characters per line
 * - ASCII text only (no ANSI, no formatting)
 * - Must be named exactly "FILE_ID.DIZ"
 * - Filenames are written in original case (consistent with files.bbs)
 * - No blank lines allowed
 */
public class FileIdDizWriter {
    
    private static final Logger logger = Logger.getLogger(FileIdDizWriter.class);
    private static final String FILE_ID_DIZ = "FILE_ID.DIZ";
    private static final int MAX_LINES = 10;
    private static final int MAX_LINE_LENGTH = 45;
    
    /**
     * Adds or updates a file entry in FILE_ID.DIZ in the specified directory using US-ASCII encoding.
     * If the filename already exists, its description will be replaced.
     * 
     * @param directory The directory to create/update FILE_ID.DIZ in
     * @param filename The filename being described
     * @param description The file description (can be multi-line)
     * @throws IOException if writing fails
     */
    public static void appendEntry(File directory, String filename, String description) throws IOException {
        appendEntry(directory, filename, description, false, "US-ASCII");
    }
    
    /**
     * Adds or updates a file entry in FILE_ID.DIZ in the specified directory with configurable encoding.
     * If the filename already exists, its description will be replaced.
     * 
     * @param directory The directory to create/update FILE_ID.DIZ in
     * @param filename The filename being described
     * @param description The file description (can be multi-line)
     * @param use8bit If true, uses specified charset; if false, uses US-ASCII
     * @param charsetName The charset name to use when use8bit is true (e.g., "CP866", "CP437")
     * @throws IOException if writing fails
     */
    public static void appendEntry(File directory, String filename, String description, boolean use8bit, String charsetName) throws IOException {
        if (directory == null || !directory.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory: " + directory);
        }
        
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }
        
        logger.l4("Directory path: " + directory.getAbsolutePath());
        logger.l4("Directory exists: " + directory.exists());
        logger.l4("Directory is writable: " + directory.canWrite());
        
        File fileIdDiz = new File(directory, FILE_ID_DIZ);
        logger.l4("FILE_ID.DIZ path: " + fileIdDiz.getAbsolutePath());
        logger.l4("FILE_ID.DIZ exists: " + fileIdDiz.exists());
        if (fileIdDiz.exists()) {
            logger.l4("FILE_ID.DIZ is writable: " + fileIdDiz.canWrite());
        }
        
        // Format the entry according to FILE_ID.DIZ specification
        List<String> formattedLines = formatFileIdDizEntry(filename, description);
        logger.l4("Formatted lines count: " + formattedLines.size());
        for (int i = 0; i < formattedLines.size(); i++) {
            logger.l5("Line " + i + ": " + formattedLines.get(i));
        }
        
        // Determine charset to use
        Charset charset;
        try {
            if (use8bit && charsetName != null && !charsetName.isEmpty()) {
                charset = Charset.forName(charsetName);
                logger.l4("Using charset: " + charset.displayName());
            } else {
                charset = StandardCharsets.US_ASCII;
                logger.l4("Using charset: US-ASCII (7-bit)");
            }
        } catch (UnsupportedCharsetException e) {
            logger.l2("Unsupported charset: " + charsetName + ", falling back to US-ASCII");
            charset = StandardCharsets.US_ASCII;
        }
        
        // Update FILE_ID.DIZ with proper encoding and synchronization
        synchronized (FileIdDizWriter.class) {
            updateFileIdDiz(fileIdDiz, filename, formattedLines, charset);
        }
        
        logger.l4("Added entry to FILE_ID.DIZ: " + filename + " in " + directory.getAbsolutePath());
        logger.l4("FILE_ID.DIZ file size after write: " + fileIdDiz.length() + " bytes");
    }
    
    /**
     * Formats a single file entry according to FILE_ID.DIZ specification.
     * 
     * @param filename The filename being described
     * @param description The description text (can be null or multi-line)
     * @return List of formatted lines conforming to FILE_ID.DIZ constraints
     */
    private static List<String> formatFileIdDizEntry(String filename, String description) {
        List<String> lines = new ArrayList<>();
        
        // Handle empty or null description
        if (description == null || description.trim().isEmpty()) {
            lines.add(truncateToMaxLength(filename));
            return lines;
        }
        
        // Split description into lines and process each
        String[] descLines = description.split("\\r?\\n");
        
        // First line: filename + description
        if (descLines.length > 0) {
            String firstLine = filename;
            String firstDesc = descLines[0].trim();
            
            // Try to fit filename and description on first line
            if (firstLine.length() + 1 + firstDesc.length() <= MAX_LINE_LENGTH) {
                firstLine += " " + firstDesc;
                lines.add(firstLine);
                
                // Add remaining description lines
                for (int i = 1; i < descLines.length && lines.size() < MAX_LINES; i++) {
                    String trimmed = descLines[i].trim();
                    if (!trimmed.isEmpty()) {
                        lines.addAll(splitTextToLines(trimmed));
                        if (lines.size() >= MAX_LINES) break;
                    }
                }
            } else {
                // Filename alone on first line if it fits
                if (firstLine.length() <= MAX_LINE_LENGTH) {
                    lines.add(firstLine);
                    
                    // Add description starting from second line
                    if (!firstDesc.isEmpty()) {
                        lines.addAll(splitTextToLines(firstDesc));
                    }
                    
                    // Add remaining description lines
                    for (int i = 1; i < descLines.length && lines.size() < MAX_LINES; i++) {
                        String trimmed = descLines[i].trim();
                        if (!trimmed.isEmpty()) {
                            lines.addAll(splitTextToLines(trimmed));
                            if (lines.size() >= MAX_LINES) break;
                        }
                    }
                } else {
                    // Truncate filename if it's too long
                    lines.add(truncateToMaxLength(firstLine));
                    
                    // Add description if there's room
                    if (lines.size() < MAX_LINES && !firstDesc.isEmpty()) {
                        lines.addAll(splitTextToLines(firstDesc));
                    }
                }
            }
        } else {
            lines.add(truncateToMaxLength(filename));
        }
        
        return truncateToMaxLines(lines);
    }
    
    /**
     * Splits text into lines that fit within the maximum line length.
     * 
     * @param text The text to split
     * @return List of lines, each within the maximum length
     */
    private static List<String> splitTextToLines(String text) {
        List<String> lines = new ArrayList<>();
        
        if (text == null || text.trim().isEmpty()) {
            return lines;
        }
        
        String remaining = text.trim();
        
        while (!remaining.isEmpty() && lines.size() < MAX_LINES) {
            if (remaining.length() <= MAX_LINE_LENGTH) {
                lines.add(remaining);
                break;
            }
            
            // Find the best break point (prefer space)
            int breakPoint = MAX_LINE_LENGTH;
            for (int i = MAX_LINE_LENGTH - 1; i >= MAX_LINE_LENGTH - 10 && i > 0; i--) {
                if (remaining.charAt(i) == ' ') {
                    breakPoint = i;
                    break;
                }
            }
            
            lines.add(remaining.substring(0, breakPoint));
            remaining = remaining.substring(breakPoint).trim();
        }
        
        return lines;
    }
    
    /**
     * Truncates a line to the maximum allowed length.
     * 
     * @param line The line to truncate
     * @return Truncated line
     */
    private static String truncateToMaxLength(String line) {
        if (line == null) {
            return "";
        }
        
        if (line.length() <= MAX_LINE_LENGTH) {
            return line;
        }
        
        return line.substring(0, MAX_LINE_LENGTH);
    }
    
    /**
     * Truncates the list of lines to the maximum allowed number.
     * 
     * @param lines The list of lines to truncate
     * @return Truncated list
     */
    private static List<String> truncateToMaxLines(List<String> lines) {
        if (lines.size() <= MAX_LINES) {
            return lines;
        }
        
        return lines.subList(0, MAX_LINES);
    }
    
    /**
     * Checks if a FILE_ID.DIZ file exists in the specified directory.
     * 
     * @param directory The directory to check
     * @return true if FILE_ID.DIZ exists, false otherwise
     */
    public static boolean fileIdDizExists(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return false;
        }
        
        File fileIdDiz = new File(directory, FILE_ID_DIZ);
        return fileIdDiz.exists() && fileIdDiz.isFile();
    }
    
    /**
     * Updates FILE_ID.DIZ by either replacing existing entries for the filename or appending new ones.
     * 
     * @param fileIdDiz The FILE_ID.DIZ file
     * @param filename The filename to add or update
     * @param newLines The formatted lines for this filename
     * @param charset The charset to use for encoding
     * @throws IOException if file operations fail
     */
    private static void updateFileIdDiz(File fileIdDiz, String filename, List<String> newLines, Charset charset) throws IOException {
        List<String> allLines = new ArrayList<>();
        
        // Read existing content if file exists
        if (fileIdDiz.exists()) {
            try {
                allLines = Files.readAllLines(fileIdDiz.toPath(), charset);
                logger.l4("Read " + allLines.size() + " existing lines from FILE_ID.DIZ");
            } catch (Exception e) {
                logger.l3("Could not read existing FILE_ID.DIZ with " + charset.displayName() + ", trying with US-ASCII: " + e.getMessage());
                try {
                    allLines = Files.readAllLines(fileIdDiz.toPath(), StandardCharsets.US_ASCII);
                    logger.l4("Successfully read " + allLines.size() + " lines with US-ASCII fallback");
                } catch (Exception e2) {
                    logger.l2("Could not read existing FILE_ID.DIZ, creating new file: " + e2.getMessage());
                    allLines = new ArrayList<>();
                }
            }
        }
        
        // Remove any existing entries for this filename
        boolean foundExisting = false;
        ListIterator<String> iterator = allLines.listIterator();
        while (iterator.hasNext()) {
            String line = iterator.next();
            // Check if line starts with filename followed by space or equals filename exactly
            if (line.startsWith(filename + " ") || line.equals(filename)) {
                foundExisting = true;
                iterator.remove();
                logger.l4("Removed existing entry for " + filename + ": " + line);
                
                // Remove subsequent lines until we find another filename (line not starting with space)
                while (iterator.hasNext()) {
                    String nextLine = iterator.next();
                    if (!nextLine.startsWith(" ") && !nextLine.trim().isEmpty()) {
                        // This looks like another filename, put it back and stop
                        iterator.previous();
                        break;
                    } else {
                        iterator.remove();
                        logger.l5("Removed continuation line: " + nextLine);
                    }
                }
                break; // Only remove the first match
            }
        }
        
        // Add the new lines
        allLines.addAll(newLines);
        
        if (foundExisting) {
            logger.l4("Replaced existing entry for " + filename);
        } else {
            logger.l4("Added new entry for " + filename);
        }
        
        // Write all lines back to the file
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(fileIdDiz, false), // Overwrite mode
                        charset))) {
            
            for (String line : allLines) {
                writer.write(line);
                writer.newLine();
            }
            
            writer.flush();
            logger.l4("Successfully wrote " + allLines.size() + " lines to FILE_ID.DIZ using " + charset.displayName());
        }
    }
}