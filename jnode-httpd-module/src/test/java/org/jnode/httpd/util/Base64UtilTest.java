package org.jnode.httpd.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

@SuppressWarnings("unused")
public class Base64UtilTest {

    @Test
    public void testEncodeEmptyArray() {
        byte[] empty = new byte[0];
        String result = Base64Util.encode(empty);
        assertEquals("", result);
    }

    @Test
    public void testEncodeSingleByte() {
        byte[] single = {65}; // 'A'
        String result = Base64Util.encode(single);
        assertEquals("QQ==", result);
    }

    @Test
    public void testEncodeTwoBytes() {
        byte[] two = {65, 66}; // 'AB'
        String result = Base64Util.encode(two);
        assertEquals("QUI=", result);
    }

    @Test
    public void testEncodeThreeBytes() {
        byte[] three = {65, 66, 67}; // 'ABC'
        String result = Base64Util.encode(three);
        assertEquals("QUJD", result);
    }

    @Test
    public void testEncodeTextString() {
        String text = "Hello World";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        String result = Base64Util.encode(bytes);
        assertEquals("SGVsbG8gV29ybGQ=", result);
    }

    @Test
    public void testDecodeTextString() {
        String encoded = "SGVsbG8gV29ybGQ=";
        byte[] result = Base64Util.decode(encoded);
        String text = new String(result, StandardCharsets.UTF_8);
        assertEquals("Hello World", text);
    }

    @Test
    public void testEncodeDecodeRoundtrip() {
        String original = "This is a test string";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
        
        String encoded = Base64Util.encode(originalBytes);
        byte[] decoded = Base64Util.decode(encoded);
        String reconstructed = new String(decoded, StandardCharsets.UTF_8);
        
        assertEquals(original, reconstructed);
    }
}