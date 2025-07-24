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
import java.util.ListIterator;

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
     * Adds or updates a file entry in FILES.BBS in the specified directory using US-ASCII encoding.
     * If the filename already exists, its description will be replaced.
     * 
     * @param directory The directory containing the file and FILES.BBS
     * @param filename The filename to add or update
     * @param description The file description (can be multi-line)
     * @throws IOException if writing fails
     */
    public static void appendEntry(File directory, String filename, String description) throws IOException {
        appendEntry(directory, filename, description, false, "US-ASCII");
    }
    
    /**
     * Adds or updates a file entry in FILES.BBS in the specified directory with configurable encoding.
     * If the filename already exists, its description will be replaced.
     * 
     * @param directory The directory containing the file and FILES.BBS
     * @param filename The filename to add or update
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
        
        // Update FILES.BBS with file locking for concurrent access
        synchronized (FilesBBSWriter.class) {
            updateFilesBBS(filesBbs, filename, formattedLines, charset);
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
    
    /**
     * Updates FILES.BBS by either replacing existing entries for the filename or appending new ones.
     * 
     * @param filesBbs The FILES.BBS file
     * @param filename The filename to add or update
     * @param newLines The formatted lines for this filename
     * @param charset The charset to use for encoding
     * @throws IOException if file operations fail
     */
    private static void updateFilesBBS(File filesBbs, String filename, List<String> newLines, Charset charset) throws IOException {
        List<String> allLines = new ArrayList<>();
        
        // Read existing content if file exists
        if (filesBbs.exists()) {
            try {
                allLines = Files.readAllLines(filesBbs.toPath(), charset);
                logger.l4("Read " + allLines.size() + " existing lines from FILES.BBS");
            } catch (Exception e) {
                logger.l3("Could not read existing FILES.BBS with " + charset.displayName() + ", trying with US-ASCII: " + e.getMessage());
                try {
                    allLines = Files.readAllLines(filesBbs.toPath(), StandardCharsets.US_ASCII);
                    logger.l4("Successfully read " + allLines.size() + " lines with US-ASCII fallback");
                } catch (Exception e2) {
                    logger.l2("Could not read existing FILES.BBS, creating new file: " + e2.getMessage());
                    allLines = new ArrayList<>();
                }
            }
        }
        
        // Remove any existing entries for this filename
        boolean foundExisting = false;
        ListIterator<String> iterator = allLines.listIterator();
        while (iterator.hasNext()) {
            String line = iterator.next();
            if (line.startsWith(filename + " ") || line.equals(filename)) {
                foundExisting = true;
                iterator.remove();
                logger.l4("Removed existing entry for " + filename + ": " + line);
                
                // Remove continuation lines (lines starting with space after filename line)
                while (iterator.hasNext()) {
                    String nextLine = iterator.next();
                    if (nextLine.startsWith(" ")) {
                        iterator.remove();
                        logger.l5("Removed continuation line: " + nextLine);
                    } else {
                        // Put back the line that doesn't start with space
                        iterator.previous();
                        break;
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
                        new FileOutputStream(filesBbs, false), // Overwrite mode
                        charset))) {
            
            for (String line : allLines) {
                writer.write(line);
                writer.newLine();
            }
            
            writer.flush();
            logger.l4("Successfully wrote " + allLines.size() + " lines to FILES.BBS using " + charset.displayName());
        }
    }
}