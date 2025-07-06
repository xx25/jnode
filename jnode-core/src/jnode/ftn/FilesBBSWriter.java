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
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import jnode.logger.Logger;

/**
 * Utility class for writing FILES.BBS files in FTN file echo directories.
 * FILES.BBS is a plain ASCII text file listing files with descriptions.
 */
public class FilesBBSWriter {
    
    private static final Logger logger = Logger.getLogger(FilesBBSWriter.class);
    private static final String FILES_BBS = "files.bbs";
    private static final int MAX_LINE_LENGTH = 80;
    private static final int MAX_FILENAME_LENGTH = 12;
    
    /**
     * Appends a file entry to FILES.BBS in the specified directory using US-ASCII encoding.
     * 
     * @param directory The directory containing the file and FILES.BBS
     * @param filename The filename to add
     * @param description The file description (can be multi-line)
     * @throws IOException if writing fails
     */
    public static void appendEntry(File directory, String filename, String description) throws IOException {
        appendEntry(directory, filename, description, false, "US-ASCII");
    }
    
    /**
     * Appends a file entry to FILES.BBS in the specified directory with configurable encoding.
     * 
     * @param directory The directory containing the file and FILES.BBS
     * @param filename The filename to add
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
        
        File filesBbs = new File(directory, FILES_BBS);
        logger.l4("FILES.BBS path: " + filesBbs.getAbsolutePath());
        logger.l4("FILES.BBS exists: " + filesBbs.exists());
        if (filesBbs.exists()) {
            logger.l4("FILES.BBS is writable: " + filesBbs.canWrite());
        }
        
        // Format the entry
        List<String> formattedLines = formatEntry(filename, description);
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
        
        // Append to FILES.BBS with file locking for concurrent access
        synchronized (FilesBBSWriter.class) {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(filesBbs, true), 
                            charset))) {
                
                for (String line : formattedLines) {
                    writer.write(line);
                    writer.newLine();
                }
                
                writer.flush();
                logger.l4("Successfully flushed data to FILES.BBS using " + charset.displayName());
            }
        }
        
        logger.l4("Added entry to FILES.BBS: " + filename + " in " + directory.getAbsolutePath());
        logger.l4("FILES.BBS file size after write: " + filesBbs.length() + " bytes");
    }
    
    /**
     * Formats a file entry according to FILES.BBS specification.
     * 
     * @param filename The filename
     * @param description The description (can be null or multi-line)
     * @return List of formatted lines ready to write
     */
    private static List<String> formatEntry(String filename, String description) {
        List<String> lines = new ArrayList<>();
        
        // Ensure filename doesn't exceed recommended length
        String formattedFilename = filename;
        if (formattedFilename.length() > MAX_FILENAME_LENGTH) {
            logger.l3("Filename exceeds recommended length: " + filename);
        }
        
        // Handle empty or null description
        if (description == null || description.trim().isEmpty()) {
            lines.add(formattedFilename);
            return lines;
        }
        
        // Split description by newlines
        String[] descLines = description.split("\\r?\\n");
        
        // First line: filename + first description line
        if (descLines.length > 0) {
            String firstLine = formattedFilename;
            
            // Calculate padding to align descriptions
            int padding = Math.max(1, MAX_FILENAME_LENGTH + 1 - formattedFilename.length());
            StringBuilder spaces = new StringBuilder();
            for (int i = 0; i < padding; i++) {
                spaces.append(' ');
            }
            firstLine += spaces.toString();
            
            // Add first description line
            String firstDesc = descLines[0].trim();
            if (firstLine.length() + firstDesc.length() <= MAX_LINE_LENGTH) {
                firstLine += firstDesc;
            } else {
                // Truncate if too long
                int availableSpace = MAX_LINE_LENGTH - firstLine.length();
                if (availableSpace > 0) {
                    firstLine += firstDesc.substring(0, availableSpace);
                }
            }
            lines.add(firstLine);
            
            // Add continuation lines for remaining description
            for (int i = 1; i < descLines.length; i++) {
                lines.add(formatContinuationLine(descLines[i]));
            }
            
            // If first description line was truncated, add the rest as continuation
            if (firstDesc.length() > MAX_LINE_LENGTH - firstLine.length() + firstDesc.length()) {
                String remainder = firstDesc.substring(MAX_LINE_LENGTH - firstLine.length() + firstDesc.length());
                lines.add(formatContinuationLine(remainder));
            }
        } else {
            lines.add(formattedFilename);
        }
        
        return lines;
    }
    
    /**
     * Formats a continuation line with proper indentation.
     * 
     * @param text The text for the continuation line
     * @return Formatted continuation line
     */
    private static String formatContinuationLine(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return " ";
        }
        
        // Ensure line starts with space for continuation
        String line = " " + trimmed;
        
        // Truncate if too long
        if (line.length() > MAX_LINE_LENGTH) {
            line = line.substring(0, MAX_LINE_LENGTH);
        }
        
        return line;
    }
}