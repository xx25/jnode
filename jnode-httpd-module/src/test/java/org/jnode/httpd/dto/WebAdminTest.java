package org.jnode.httpd.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

@SuppressWarnings("unused")
public class WebAdminTest {

    @Test
    public void testConstructor() {
        WebAdmin admin = new WebAdmin();
        assertNotNull(admin);
        assertNull(admin.getId());
        assertNull(admin.getUsername());
        assertNull(admin.getPassword());
    }

    @Test
    public void testIdGetterSetter() {
        WebAdmin webAdmin = new WebAdmin();
        Long expectedId = 123L;
        
        webAdmin.setId(expectedId);
        assertEquals(expectedId, webAdmin.getId());
    }

    @Test
    public void testUsernameGetterSetter() {
        WebAdmin webAdmin = new WebAdmin();
        String expectedUsername = "admin";
        
        webAdmin.setUsername(expectedUsername);
        assertEquals(expectedUsername, webAdmin.getUsername());
    }

    @Test
    public void testPasswordGetterSetter() {
        WebAdmin webAdmin = new WebAdmin();
        String expectedPassword = "hashedPassword123";
        
        webAdmin.setPassword(expectedPassword);
        assertEquals(expectedPassword, webAdmin.getPassword());
    }

    @Test
    public void testAllFieldsTogether() {
        WebAdmin webAdmin = new WebAdmin();
        Long expectedId = 456L;
        String expectedUsername = "testuser";
        String expectedPassword = "testpassword";
        
        webAdmin.setId(expectedId);
        webAdmin.setUsername(expectedUsername);
        webAdmin.setPassword(expectedPassword);
        
        assertEquals(expectedId, webAdmin.getId());
        assertEquals(expectedUsername, webAdmin.getUsername());
        assertEquals(expectedPassword, webAdmin.getPassword());
    }
}