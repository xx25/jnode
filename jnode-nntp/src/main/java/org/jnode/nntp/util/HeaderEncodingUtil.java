package org.jnode.nntp.util;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility class for encoding NNTP headers according to RFC 2047
 * to properly handle non-ASCII characters like Cyrillic text.
 */
public class HeaderEncodingUtil {

    /**
     * Encode a header value according to RFC 2047 if it contains non-ASCII characters.
     * Uses UTF-8 charset with Base64 encoding.
     * 
     * @param headerValue the header value to encode
     * @return encoded header value in format =?UTF-8?B?base64data?= if needed, or original value
     */
    public static String encodeHeaderIfNeeded(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        
        // Check if the string contains non-ASCII characters
        if (isAsciiOnly(headerValue)) {
            return headerValue;
        }
        
        // Encode using RFC 2047: =?charset?encoding?encoded-text?=
        try {
            byte[] utf8Bytes = headerValue.getBytes(StandardCharsets.UTF_8);
            String base64Encoded = Base64.getEncoder().encodeToString(utf8Bytes);
            return "=?UTF-8?B?" + base64Encoded + "?=";
        } catch (Exception e) {
            // If encoding fails, return original value
            return headerValue;
        }
    }
    
    /**
     * Check if a string contains only ASCII characters
     * 
     * @param str the string to check
     * @return true if string contains only ASCII characters (0-127)
     */
    private static boolean isAsciiOnly(String str) {
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Encode a Subject header field
     * 
     * @param subject the subject to encode
     * @return properly encoded subject header
     */
    public static String encodeSubject(String subject) {
        return encodeHeaderIfNeeded(subject);
    }
    
    /**
     * Encode a From header field
     * 
     * @param from the from field to encode
     * @return properly encoded from header
     */
    public static String encodeFrom(String from) {
        return encodeHeaderIfNeeded(from);
    }
}