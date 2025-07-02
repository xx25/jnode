package org.jnode.nntp.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HeaderEncodingUtilTest {

    @Test
    public void testAsciiSubjectNotEncoded() {
        String ascii = "This is ASCII text";
        String result = HeaderEncodingUtil.encodeSubject(ascii);
        assertEquals(ascii, result);
    }

    @Test
    public void testCyrillicSubjectEncoded() {
        String cyrillic = "Фидо мейлер под Linux";
        String result = HeaderEncodingUtil.encodeSubject(cyrillic);
        assertTrue(result.startsWith("=?UTF-8?B?"));
        assertTrue(result.endsWith("?="));
        assertNotEquals(cyrillic, result);
    }

    @Test
    public void testNullSubject() {
        String result = HeaderEncodingUtil.encodeSubject(null);
        assertNull(result);
    }

    @Test
    public void testEmptySubject() {
        String empty = "";
        String result = HeaderEncodingUtil.encodeSubject(empty);
        assertEquals(empty, result);
    }

    @Test
    public void testMixedContent() {
        String mixed = "Re: Фидо ASCII тест";
        String result = HeaderEncodingUtil.encodeSubject(mixed);
        assertTrue(result.startsWith("=?UTF-8?B?"));
        assertTrue(result.endsWith("?="));
    }
}