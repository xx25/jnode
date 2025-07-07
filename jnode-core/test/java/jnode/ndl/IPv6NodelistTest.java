package jnode.ndl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import jnode.main.MainHandler;

/**
 * Test IPv6 address parsing and prioritization in nodelist processing
 */
public class IPv6NodelistTest {

    private void setupIPv6Enabled() {
        Properties props = new Properties();
        props.setProperty("binkp.ipv6.enable", "true");
        props.setProperty("binkp.temp", System.getProperty("java.io.tmpdir"));
        props.setProperty("binkp.network", "fidonet");
        props.setProperty("binkp.maxmem", "10485760");
        props.setProperty("binkp.size", "32767");
        props.setProperty("binkp.timeout", "30");
        props.setProperty("binkp.connect.timeout", "10");
        
        new MainHandler(props);
    }

    private void setupIPv6Disabled() {
        Properties props = new Properties();
        props.setProperty("binkp.ipv6.enable", "false");
        props.setProperty("binkp.temp", System.getProperty("java.io.tmpdir"));
        props.setProperty("binkp.network", "fidonet");
        props.setProperty("binkp.maxmem", "10485760");
        props.setProperty("binkp.size", "32767");
        props.setProperty("binkp.timeout", "30");
        props.setProperty("binkp.connect.timeout", "10");
        
        new MainHandler(props);
    }

    @Test
    void testIPv6AddressParsing() {
        setupIPv6Enabled();
        
        // Test IPv6 address recognition
        FtnNdlAddress ndlAddr = new FtnNdlAddress("2:5020/848");
        
        // Simulate nodelist line with IPv6 addresses
        String testLine = "2:5020/848,Test Node,Somewhere,Sysop Name,300,CM,IBN:[2001:db8::1]:24554,INA:[2001:db8::2],INA:example.com,IBN:192.168.1.1:25555";
        ndlAddr.setLine(testLine);
        
        // Test IBN hosts with priority
        List<String> ibnHosts = ndlAddr.getIbnHostsByPriority();
        assertNotNull(ibnHosts);
        assertEquals(2, ibnHosts.size());
        
        // IPv6 should come first when IPv6 is enabled
        assertEquals("[2001:db8::1]:24554", ibnHosts.get(0));
        assertEquals("192.168.1.1:25555", ibnHosts.get(1));
        
        // Test INA hosts with priority
        List<String> inaHosts = ndlAddr.getInetHostsByPriority();
        assertNotNull(inaHosts);
        assertEquals(2, inaHosts.size());
        
        // IPv6 should come first when IPv6 is enabled
        assertEquals("[2001:db8::2]", inaHosts.get(0));
        assertEquals("example.com", inaHosts.get(1));
    }

    @Test
    void testIPv6AddressTypes() {
        setupIPv6Enabled();
        
        FtnNdlAddress ndlAddr = new FtnNdlAddress("2:5020/848");
        
        // Test IPv6 address detection
        assertEquals("ipv6", ndlAddr.getAddressType("[2001:db8::1]:24554"));
        assertEquals("ipv6", ndlAddr.getAddressType("[::1]"));
        assertEquals("ipv6", ndlAddr.getAddressType("[2001:db8::1]"));
        
        // Test IPv4 address detection
        assertEquals("ipv4", ndlAddr.getAddressType("192.168.1.1:24554"));
        assertEquals("ipv4", ndlAddr.getAddressType("10.0.0.1"));
        
        // Test hostname detection
        assertEquals("hostname", ndlAddr.getAddressType("example.com"));
        assertEquals("hostname", ndlAddr.getAddressType("fido.example.com:24554"));
    }

    @Test
    void testIPv6WithoutPortHandling() {
        setupIPv6Enabled();
        
        FtnNdlAddress ndlAddr = new FtnNdlAddress("2:5020/848");
        
        // Test IPv6 addresses without ports
        String testLine = "2:5020/848,Test Node,Somewhere,Sysop Name,300,CM,IBN:[2001:db8::1],INA:[2001:db8::2]";
        ndlAddr.setLine(testLine);
        
        List<String> ibnHosts = ndlAddr.getIbnHosts();
        assertNotNull(ibnHosts);
        assertEquals(1, ibnHosts.size());
        assertEquals("[2001:db8::1]", ibnHosts.get(0));
        
        List<String> inaHosts = ndlAddr.getInetHosts();
        assertNotNull(inaHosts);
        assertEquals(1, inaHosts.size());
        assertEquals("[2001:db8::2]", inaHosts.get(0));
    }

    @Test
    void testMixedAddressPriority() {
        setupIPv6Enabled();
        
        FtnNdlAddress ndlAddr = new FtnNdlAddress("2:5020/848");
        
        // Test mixed IPv6, IPv4, and hostname addresses
        String testLine = "2:5020/848,Test Node,Somewhere,Sysop Name,300,CM,IBN:example.com,IBN:192.168.1.1,IBN:[2001:db8::1],INA:test.com,INA:10.0.0.1,INA:[2001:db8::2]";
        ndlAddr.setLine(testLine);
        
        // Test IBN priority order: IPv6 > IPv4 > hostname
        List<String> ibnHosts = ndlAddr.getIbnHostsByPriority();
        assertEquals(3, ibnHosts.size());
        assertEquals("[2001:db8::1]", ibnHosts.get(0)); // IPv6 first
        assertEquals("192.168.1.1", ibnHosts.get(1));   // IPv4 second
        assertEquals("example.com", ibnHosts.get(2));    // hostname last
        
        // Test INA priority order: IPv6 > IPv4 > hostname
        List<String> inaHosts = ndlAddr.getInetHostsByPriority();
        assertEquals(3, inaHosts.size());
        assertEquals("[2001:db8::2]", inaHosts.get(0)); // IPv6 first
        assertEquals("10.0.0.1", inaHosts.get(1));      // IPv4 second
        assertEquals("test.com", inaHosts.get(2));       // hostname last
    }

    @Test
    void testIPv6DisabledBehavior() {
        setupIPv6Disabled();
        
        FtnNdlAddress ndlAddr = new FtnNdlAddress("2:5020/848");
        String testLine = "2:5020/848,Test Node,Somewhere,Sysop Name,300,CM,IBN:[2001:db8::1],IBN:192.168.1.1,INA:[2001:db8::2],INA:example.com";
        ndlAddr.setLine(testLine);
        
        // When IPv6 is disabled, IPv6 addresses should be filtered out
        List<String> ibnHosts = ndlAddr.getIbnHostsByPriority();
        assertEquals(1, ibnHosts.size()); // Should only have IPv4 address
        assertEquals("192.168.1.1", ibnHosts.get(0)); // IPv6 address filtered out
        
        List<String> inaHosts = ndlAddr.getInetHostsByPriority();
        assertEquals(1, inaHosts.size()); // Should only have hostname
        assertEquals("example.com", inaHosts.get(0)); // IPv6 address filtered out
    }

    @Test
    void testRealWorldNodelistExamples() {
        setupIPv6Enabled();
        
        FtnNdlAddress ndlAddr = new FtnNdlAddress("2:5020/848");
        
        // Test real-world examples from FTN nodelistss
        String testLine1 = "2:5020/848,Test Node,Somewhere,Sysop Name,300,CM,IBN:[2001:470:1f0b:b14::2]:24554,INA:fidonet.example.com";
        ndlAddr.setLine(testLine1);
        
        List<String> ibnHosts = ndlAddr.getIbnHostsByPriority();
        assertEquals(1, ibnHosts.size());
        assertEquals("[2001:470:1f0b:b14::2]:24554", ibnHosts.get(0));
        
        List<String> inaHosts = ndlAddr.getInetHostsByPriority();
        assertEquals(1, inaHosts.size());
        assertEquals("fidonet.example.com", inaHosts.get(0));
    }

    @Test
    void testEmptyAndInvalidAddresses() {
        setupIPv6Enabled();
        
        FtnNdlAddress ndlAddr = new FtnNdlAddress("2:5020/848");
        
        // Test handling of empty and invalid addresses
        String testLine = "2:5020/848,Test Node,Somewhere,Sysop Name,300,CM,IBN,INA:-,INA:valid.com";
        ndlAddr.setLine(testLine);
        
        List<String> ibnHosts = ndlAddr.getIbnHostsByPriority();
        assertEquals(0, ibnHosts.size()); // Empty IBN should return empty list
        
        List<String> inaHosts = ndlAddr.getInetHostsByPriority();
        assertEquals(1, inaHosts.size()); // Should skip "-" and return only valid address
        assertEquals("valid.com", inaHosts.get(0));
    }

    @Test
    void testIPv6FilteringWhenDisabled() {
        setupIPv6Disabled();
        
        FtnNdlAddress ndlAddr = new FtnNdlAddress("2:5020/848");
        
        // Test that IPv6 addresses are completely filtered out when IPv6 is disabled
        String testLine = "2:5020/848,Test Node,Somewhere,Sysop Name,300,CM,IBN:[2001:db8::1]:24554,IBN:[2001:db8::2],IBN:192.168.1.1:25555,IBN:example.com,INA:[2001:db8::3]:8080,INA:[2001:db8::4],INA:10.0.0.1,INA:test.com";
        ndlAddr.setLine(testLine);
        
        // Only non-IPv6 addresses should be returned
        List<String> ibnHosts = ndlAddr.getIbnHostsByPriority();
        assertEquals(2, ibnHosts.size());
        assertEquals("192.168.1.1:25555", ibnHosts.get(0)); // IPv4
        assertEquals("example.com", ibnHosts.get(1));       // hostname
        // IPv6 addresses [2001:db8::1]:24554 and [2001:db8::2] should be filtered out
        
        List<String> inaHosts = ndlAddr.getInetHostsByPriority();
        assertEquals(2, inaHosts.size());
        assertEquals("10.0.0.1", inaHosts.get(0));    // IPv4
        assertEquals("test.com", inaHosts.get(1));     // hostname
        // IPv6 addresses [2001:db8::3]:8080 and [2001:db8::4] should be filtered out
    }
}