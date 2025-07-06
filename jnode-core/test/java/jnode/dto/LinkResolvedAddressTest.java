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

package jnode.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LinkResolvedAddressTest {
    
    @Test
    public void testResolvedAddressWithDash() {
        Link link = new Link();
        link.setLinkAddress("2:5020/848");
        link.setProtocolAddress("-");
        
        assertEquals("-", link.getResolvedProtocolAddress());
    }
    
    @Test
    public void testResolvedAddressWithConfiguredAddress() {
        Link link = new Link();
        link.setLinkAddress("2:5020/848");
        link.setProtocolAddress("configured.host.com:24554");
        
        assertEquals("configured.host.com:24554", link.getResolvedProtocolAddress());
    }
    
    @Test
    public void testResolvedAddressWithEmptyProtocolAddress() {
        Link link = new Link();
        link.setLinkAddress("2:5020/848");
        link.setProtocolAddress("");
        
        // Without actual nodelist data, it should return "-"
        assertEquals("-", link.getResolvedProtocolAddress());
    }
    
    @Test
    public void testResolvedAddressWithNullProtocolAddress() {
        Link link = new Link();
        link.setLinkAddress("2:5020/848");
        link.setProtocolAddress(null);
        
        // Without actual nodelist data, it should return "-"
        assertEquals("-", link.getResolvedProtocolAddress());
    }
    
    @Test
    public void testResolvedAddressWithInvalidFtnAddress() {
        Link link = new Link();
        link.setLinkAddress("invalid:address");
        link.setProtocolAddress("");
        
        // Should return "-" on any exception
        assertEquals("-", link.getResolvedProtocolAddress());
    }
}