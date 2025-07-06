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

package jnode.ndl;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FtnNdlAddressTest {

    @Test
    public void testGetIbnHostWithDomainName() {
        FtnNdlAddress addr = new FtnNdlAddress("2:5020/848");
        addr.setLine(",848,NodeX_Station,Moscow,Ivan_Agarkov,00-00-000000,300,MO,CM,IBN:fidonode.in,INA:fidonode.in,U,NPK");
        
        assertEquals("fidonode.in", addr.getIbnHost());
        assertEquals(24554, addr.getBinkpPort()); // Default port
    }
    
    @Test
    public void testGetIbnHostWithDomainAndPort() {
        FtnNdlAddress addr = new FtnNdlAddress("2:5020/2141");
        addr.setLine(",2141,Pushkins_F2141,Moscow,Sergey_Poziturin,00-00-000000,300,MO,CM,IBN:vp.propush.ru:24555,INA:vp.propush.ru");
        
        assertEquals("vp.propush.ru", addr.getIbnHost());
        assertEquals(24555, addr.getBinkpPort());
    }
    
    @Test
    public void testGetIbnHostWithIPAddress() {
        FtnNdlAddress addr = new FtnNdlAddress("2:5020/100");
        addr.setLine(",100,Test_Node,Moscow,Test_Sysop,00-00-000000,300,MO,CM,IBN:192.168.1.1,INA:test.com");
        
        assertEquals("192.168.1.1", addr.getIbnHost());
        assertEquals(24554, addr.getBinkpPort());
    }
    
    @Test
    public void testGetIbnHostWithIPAndPort() {
        FtnNdlAddress addr = new FtnNdlAddress("2:5020/101");
        addr.setLine(",101,Test_Node2,Moscow,Test_Sysop,00-00-000000,300,MO,CM,IBN:10.0.0.1:25555,INA:test2.com");
        
        assertEquals("10.0.0.1", addr.getIbnHost());
        assertEquals(25555, addr.getBinkpPort());
    }
    
    @Test
    public void testGetIbnHostWithNoParameter() {
        FtnNdlAddress addr = new FtnNdlAddress("2:5020/102");
        addr.setLine(",102,Test_Node3,Moscow,Test_Sysop,00-00-000000,300,MO,CM,IBN,INA:fallback.com");
        
        assertNull(addr.getIbnHost()); // No host in IBN
        assertEquals(24554, addr.getBinkpPort()); // Default port
        assertEquals("fallback.com", addr.getInetHost()); // Should fall back to INA
    }
    
    @Test
    public void testGetIbnHostWithPortOnly() {
        FtnNdlAddress addr = new FtnNdlAddress("2:5020/103");
        addr.setLine(",103,Test_Node4,Moscow,Test_Sysop,00-00-000000,300,MO,CM,IBN:24555,INA:another.com");
        
        assertNull(addr.getIbnHost()); // Just port, no host
        assertEquals(24555, addr.getBinkpPort());
        assertEquals("another.com", addr.getInetHost());
    }
    
    @Test
    public void testNoIbnFlag() {
        FtnNdlAddress addr = new FtnNdlAddress("2:5020/104");
        addr.setLine(",104,Test_Node5,Moscow,Test_Sysop,00-00-000000,300,MO,CM,INA:only-ina.com");
        
        assertNull(addr.getIbnHost());
        assertEquals(-1, addr.getBinkpPort()); // No IBN flag
        assertEquals("only-ina.com", addr.getInetHost());
    }
    
    @Test
    public void testInaWithNoParameter() {
        FtnNdlAddress addr = new FtnNdlAddress("2:5020/105");
        addr.setLine(",105,Test_Node6,Moscow,Test_Sysop,00-00-000000,300,MO,CM,IBN,INA");
        
        assertNull(addr.getIbnHost());
        assertEquals(24554, addr.getBinkpPort());
        assertEquals("-", addr.getInetHost()); // INA with no parameter returns "-"
    }
    
}