package org.jnode.httpd.filters;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

@SuppressWarnings("unused")
public class CharsetFilterTest {

    @Test
    public void testConstructor() {
        CharsetFilter filter = new CharsetFilter();
        assertNotNull(filter);
    }
}