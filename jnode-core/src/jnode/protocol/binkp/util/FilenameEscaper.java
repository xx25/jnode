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

/**
 * Utility class for BinkP filename escaping according to FTS-1026 specification.
 * 
 * From BinkP specification:
 * - Illegal characters: Space and backslash MUST be escaped
 * - Unsafe characters: Non-alphanumeric should be escaped  
 * - Escape format: \xHH (two hex digits, lowercase preferred)
 * - Compatibility: Accept \20 format for space
 * 
 * @author jNode Team
 */
public class FilenameEscaper {
    
    /**
     * Escapes a filename for BinkP transmission according to FTS-1026.
     * 
     * Escapes:
     * - Space (0x20) as \x20
     * - Backslash (0x5C) as \x5c  
     * - Non-printable characters (below 0x20 or above 0x7E) as \xHH
     * - Other unsafe characters that could cause parsing issues
     * 
     * @param filename Original filename
     * @return Escaped filename safe for BinkP transmission
     */
    public static String escape(String filename) {
        if (filename == null) {
            return null;
        }
        
        StringBuilder escaped = new StringBuilder();
        
        for (int i = 0; i < filename.length(); i++) {
            char c = filename.charAt(i);
            
            if (needsEscaping(c)) {
                escaped.append(String.format("\\x%02x", (int) c));
            } else {
                escaped.append(c);
            }
        }
        
        return escaped.toString();
    }
    
    /**
     * Unescapes a filename received via BinkP according to FTS-1026.
     * 
     * Supports both:
     * - Standard format: \xHH (two hex digits)
     * - Compatibility format: \20 for space (legacy support)
     * 
     * @param escapedFilename Escaped filename from BinkP
     * @return Original filename with escaping removed
     */
    public static String unescape(String escapedFilename) {
        if (escapedFilename == null) {
            return null;
        }
        
        StringBuilder unescaped = new StringBuilder();
        
        for (int i = 0; i < escapedFilename.length(); i++) {
            char c = escapedFilename.charAt(i);
            
            if (c == '\\' && i + 1 < escapedFilename.length()) {
                char next = escapedFilename.charAt(i + 1);
                
                if (next == 'x' && i + 4 <= escapedFilename.length()) {
                    // Standard \xHH format - must have exactly 2 hex digits
                    // Need to check we have enough characters for full sequence
                    if (isHexDigit(escapedFilename.charAt(i + 2)) && 
                        isHexDigit(escapedFilename.charAt(i + 3))) {
                        try {
                            String hexStr = escapedFilename.substring(i + 2, i + 4);
                            int charCode = Integer.parseInt(hexStr, 16);
                            unescaped.append((char) charCode);
                            i += 3; // Skip \xHH
                        } catch (NumberFormatException e) {
                            // Invalid hex sequence, treat as literal
                            unescaped.append(c);
                        }
                    } else {
                        // Invalid or incomplete hex sequence, treat as literal
                        unescaped.append(c);
                    }
                } else if (i + 3 <= escapedFilename.length() && 
                          escapedFilename.substring(i + 1, i + 3).equals("20")) {
                    // Legacy \20 format for space
                    unescaped.append(' ');
                    i += 2; // Skip \20
                } else if (i + 3 <= escapedFilename.length() && 
                          escapedFilename.substring(i + 1, i + 3).equals("09")) {
                    // Legacy \09 format for tab
                    unescaped.append('\t');
                    i += 2; // Skip \09
                } else {
                    // Escape sequence we don't recognize, treat as literal
                    unescaped.append(c);
                }
            } else {
                unescaped.append(c);
            }
        }
        
        return unescaped.toString();
    }
    
    /**
     * Determines if a character needs escaping according to BinkP specification.
     * 
     * Characters that need escaping:
     * - Space (0x20) - MUST be escaped per spec
     * - Backslash (0x5C) - MUST be escaped per spec
     * - Control characters (0x00-0x1F) - Non-printable
     * - DEL and above (0x7F-0xFF) - Non-printable or extended ASCII
     * - Other potentially problematic characters for filename parsing
     * 
     * @param c Character to check
     * @return true if character should be escaped
     */
    private static boolean needsEscaping(char c) {
        // Must escape: space and backslash
        if (c == ' ' || c == '\\') {
            return true;
        }
        
        // Must escape: control characters (0x00-0x1F)
        if (c < 0x20) {
            return true;
        }
        
        // Must escape: DEL and extended ASCII (0x7F-0xFF)
        if (c >= 0x7F) {
            return true;
        }
        
        // According to BinkP specification, only space and backslash MUST be escaped
        // Being conservative and only escaping what's absolutely necessary
        // Colon (:) should NOT be escaped as it's used in addresses like C:\path
        
        // All other printable ASCII characters are safe
        return false;
    }
    
    /**
     * Validates that a filename is properly escaped for BinkP transmission.
     * 
     * @param filename Filename to validate
     * @return true if filename is properly escaped and safe for transmission
     */
    public static boolean isProperlyEscaped(String filename) {
        if (filename == null) {
            return false;
        }
        
        for (int i = 0; i < filename.length(); i++) {
            char c = filename.charAt(i);
            
            // Check for properly formatted escape sequences first
            if (c == '\\' && i + 1 < filename.length()) {
                char next = filename.charAt(i + 1);
                if (next == 'x' && i + 4 <= filename.length()) {
                    // Validate \xHH format - need exactly 2 hex digits
                    if (isHexDigit(filename.charAt(i + 2)) && 
                        isHexDigit(filename.charAt(i + 3))) {
                        try {
                            String hexStr = filename.substring(i + 2, i + 4);
                            Integer.parseInt(hexStr, 16);
                            i += 3; // Skip the escape sequence
                        } catch (NumberFormatException e) {
                            return false; // Invalid escape sequence
                        }
                    } else {
                        return false; // Invalid hex digits
                    }
                } else if (i + 3 <= filename.length() && 
                          (filename.substring(i + 1, i + 3).equals("20") || 
                           filename.substring(i + 1, i + 3).equals("09"))) {
                    // Validate specific \NN formats we support
                    i += 2; // Skip the escape sequence
                } else {
                    return false; // Invalid escape sequence
                }
            } else if (needsEscaping(c)) {
                return false; // Found unescaped character that should be escaped
            }
        }
        
        return true;
    }
    
    /**
     * Helper method to check if a character is a valid hexadecimal digit.
     * 
     * @param c Character to check
     * @return true if character is 0-9, a-f, or A-F
     */
    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || 
               (c >= 'a' && c <= 'f') || 
               (c >= 'A' && c <= 'F');
    }
}