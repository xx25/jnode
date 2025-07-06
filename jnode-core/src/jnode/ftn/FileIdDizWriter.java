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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jnode.logger.Logger;

/**
 * Utility class for writing FILE_ID.DIZ files in FTN file echo directories.
 * FILE_ID.DIZ is a standard BBS file description format with specific constraints:
 * - Maximum 10 lines of text
 * - Maximum 45 characters per line
 * - ASCII text only (no ANSI, no formatting)
 * - Must be named exactly "FILE_ID.DIZ"
 * - No blank lines allowed
 */
public class FileIdDizWriter {
    
    private static final Logger logger = Logger.getLogger(FileIdDizWriter.class);
    private static final String FILE_ID_DIZ = "FILE_ID.DIZ";
    private static final int MAX_LINES = 10;
    private static final int MAX_LINE_LENGTH = 45;
    
    /**
     * Appends a file entry to FILE_ID.DIZ in the specified directory.
     * If the file doesn't exist, it will be created. If it exists, the entry
     * will be appended to preserve existing descriptions.
     * 
     * @param directory The directory to create/append to FILE_ID.DIZ in
     * @param filename The filename being described
     * @param description The file description (can be multi-line)
     * @throws IOException if writing fails
     */
    public static void appendEntry(File directory, String filename, String description) throws IOException {
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
        
        // Append to FILE_ID.DIZ with proper encoding and synchronization
        synchronized (FileIdDizWriter.class) {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(fileIdDiz, true), // Append mode
                            StandardCharsets.US_ASCII))) {
                
                for (String line : formattedLines) {
                    writer.write(line);
                    writer.newLine();
                }
                
                writer.flush();
                logger.l4("Successfully flushed data to FILE_ID.DIZ");
            }
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
            lines.add(truncateToMaxLength(filename.toUpperCase()));
            return lines;
        }
        
        // Split description into lines and process each
        String[] descLines = description.split("\\r?\\n");
        
        // First line: filename (uppercase) + description
        if (descLines.length > 0) {
            String firstLine = filename.toUpperCase();
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
            lines.add(truncateToMaxLength(filename.toUpperCase()));
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
}