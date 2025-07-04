package org.jnode.httpd.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("unused")
public class JSONUtilTest {

    @Test
    public void testPairWithString() {
        String result = JSONUtil.pair("name", "testValue");
        assertEquals("\"name\":\"testValue\"", result);
    }

    @Test
    public void testPairWithInteger() {
        String result = JSONUtil.pair("count", 42);
        assertEquals("\"count\":42", result);
    }

    @Test
    public void testPairWithBoolean() {
        String result = JSONUtil.pair("active", true);
        assertEquals("\"active\":true", result);
    }

    @Test
    public void testPairWithNull() {
        String result = JSONUtil.pair("data", null);
        assertEquals("\"data\":{}", result);
    }

    @Test
    public void testValueWithNull() {
        String result = JSONUtil.value(null);
        assertEquals("{}", result);
    }

    @Test
    public void testValueWithInteger() {
        String result = JSONUtil.value(123);
        assertEquals("123", result);
    }

    @Test
    public void testValueWithString() {
        String result = JSONUtil.value("hello");
        assertEquals("\"hello\"", result);
    }

    @Test
    public void testValueWithStringContainingQuotes() {
        String result = JSONUtil.value("hello \"world\"");
        assertEquals("\"hello \"world\"\"", result);
    }

    @Test
    public void testValueWithEmptyCollection() {
        List<String> empty = Collections.emptyList();
        String result = JSONUtil.value(empty);
        assertEquals("[]", result);
    }

    @Test
    public void testValueWithSingleElementCollection() {
        List<String> single = Collections.singletonList("test");
        String result = JSONUtil.value(single);
        assertEquals("[\"test\"]", result);
    }

    @Test
    public void testValueWithMultipleElementCollection() {
        List<String> multiple = Arrays.asList("first", "second", "third");
        String result = JSONUtil.value(multiple);
        assertEquals("[\"first\",\"second\",\"third\"]", result);
    }
}