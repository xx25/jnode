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

package jnode.ftn;

import jnode.ftn.types.Ftn2D;
import jnode.ftn.types.FtnAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Kirill Temnenkov (kirill@temnenkov.com)
 */
public class FtnToolsTest {
    
    @Test
    public void testRead2D() throws Exception {
        List<Ftn2D> r = FtnTools.read2D("250/25 463/68 5000/111");

        assertEquals(3, r.size());
        assertEquals(new Ftn2D(250,25), r.get(0));
        assertEquals(new Ftn2D(463,68), r.get(1));
        assertEquals(new Ftn2D(5000,111), r.get(2));
    }

    @Test
    public void testRead2DSmart() throws Exception {
        List<Ftn2D> r = FtnTools.read2D("5030/2104 2404 5051/41");

        assertEquals(3, r.size());
        assertEquals(new Ftn2D(5030,2104), r.get(0));
        assertEquals(new Ftn2D(5030,2404), r.get(1));
        assertEquals(new Ftn2D(5051,41), r.get(2));
    }

    @Test
    public void testRead2DSmart2() throws Exception {
        List<Ftn2D> r = FtnTools.read2D("5020/2141 2140");

        assertEquals(2, r.size());
        assertEquals(new Ftn2D(5020,2141), r.get(0));
        assertEquals(new Ftn2D(5020,2140), r.get(1));
    }

    @Test
    public void testRead2DBad() throws Exception {
        List<Ftn2D> r = FtnTools.read2D("5020/2141 sd2140 5030/141");

        assertEquals(2, r.size());
        assertEquals(new Ftn2D(5020,2141), r.get(0));
        assertEquals(new Ftn2D(5030,141), r.get(1));
    }

    @Test
    public void testRead2DBad2() throws Exception {
        List<Ftn2D> r = FtnTools.read2D("5020/2141 as2140 141");

        assertEquals(1, r.size());
        assertEquals(new Ftn2D(5020,2141), r.get(0));
    }

    // === Address Utility Tests ===

    @Test
    public void testRead4DBasic() {
        List<FtnAddress> result = FtnTools.read4D("2:5020/1042 1:250/25 3:463/68.10");
        
        assertEquals(3, result.size());
        
        assertEquals(2, result.get(0).getZone());
        assertEquals(5020, result.get(0).getNet());
        assertEquals(1042, result.get(0).getNode());
        assertEquals(0, result.get(0).getPoint());
        
        assertEquals(1, result.get(1).getZone());
        assertEquals(250, result.get(1).getNet());
        assertEquals(25, result.get(1).getNode());
        assertEquals(0, result.get(1).getPoint());
        
        assertEquals(3, result.get(2).getZone());
        assertEquals(463, result.get(2).getNet());
        assertEquals(68, result.get(2).getNode());
        assertEquals(10, result.get(2).getPoint());
    }

    @Test
    public void testRead4DWithPoints() {
        List<FtnAddress> result = FtnTools.read4D("2:5020/1042.5 1:250/25.100");
        
        assertEquals(2, result.size());
        
        assertEquals(2, result.get(0).getZone());
        assertEquals(5020, result.get(0).getNet());
        assertEquals(1042, result.get(0).getNode());
        assertEquals(5, result.get(0).getPoint());
        
        assertEquals(1, result.get(1).getZone());
        assertEquals(250, result.get(1).getNet());
        assertEquals(25, result.get(1).getNode());
        assertEquals(100, result.get(1).getPoint());
    }

    @Test
    public void testRead4DSingleAddress() {
        List<FtnAddress> result = FtnTools.read4D("2:5020/1042");
        
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getZone());
        assertEquals(5020, result.get(0).getNet());
        assertEquals(1042, result.get(0).getNode());
        assertEquals(0, result.get(0).getPoint());
    }

    @Test
    public void testRead4DEmptyString() {
        List<FtnAddress> result = FtnTools.read4D("");
        assertEquals(0, result.size());
    }

    @Test
    public void testRead4DWithInvalidAddresses() {
        List<FtnAddress> result = FtnTools.read4D("2:5020/1042 invalid:format 1:250/25 bad/address");
        
        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getZone());
        assertEquals(5020, result.get(0).getNet());
        assertEquals(1042, result.get(0).getNode());
        
        assertEquals(1, result.get(1).getZone());
        assertEquals(250, result.get(1).getNet());
        assertEquals(25, result.get(1).getNode());
    }

    @Test
    public void testRead4DAllInvalid() {
        List<FtnAddress> result = FtnTools.read4D("invalid format bad");
        assertEquals(0, result.size());
    }

    @Test
    public void testWrite2DBasic() {
        List<Ftn2D> addresses = Arrays.asList(
            new Ftn2D(250, 25),
            new Ftn2D(463, 68),
            new Ftn2D(5000, 111)
        );
        
        String result = FtnTools.write2D(addresses, false);
        assertEquals("250/25 463/68 5000/111", result);
    }

    @Test
    public void testWrite2DWithSorting() {
        List<Ftn2D> addresses = Arrays.asList(
            new Ftn2D(5000, 111),
            new Ftn2D(250, 25),
            new Ftn2D(463, 68),
            new Ftn2D(250, 10)
        );
        
        String result = FtnTools.write2D(addresses, true);
        assertEquals("250/10 250/25 463/68 5000/111", result);
    }

    @Test
    public void testWrite2DWithSortingSameNet() {
        List<Ftn2D> addresses = Arrays.asList(
            new Ftn2D(250, 100),
            new Ftn2D(250, 25),
            new Ftn2D(250, 50)
        );
        
        String result = FtnTools.write2D(addresses, true);
        assertEquals("250/25 250/50 250/100", result);
    }

    @Test
    public void testWrite2DEmptyList() {
        List<Ftn2D> addresses = new ArrayList<>();
        String result = FtnTools.write2D(addresses, false);
        assertEquals("", result);
    }

    @Test
    public void testWrite2DSingleAddress() {
        List<Ftn2D> addresses = Arrays.asList(new Ftn2D(250, 25));
        String result = FtnTools.write2D(addresses, false);
        assertEquals("250/25", result);
    }

    @Test
    public void testWrite4DBasic() {
        List<FtnAddress> addresses = Arrays.asList(
            new FtnAddress(2, 5020, 1042, 0),
            new FtnAddress(1, 250, 25, 0),
            new FtnAddress(3, 463, 68, 10)
        );
        
        String result = FtnTools.write4D(addresses);
        assertEquals("2:5020/1042 1:250/25 3:463/68.10", result);
    }

    @Test
    public void testWrite4DWithPoints() {
        List<FtnAddress> addresses = Arrays.asList(
            new FtnAddress(2, 5020, 1042, 5),
            new FtnAddress(1, 250, 25, 100)
        );
        
        String result = FtnTools.write4D(addresses);
        assertEquals("2:5020/1042.5 1:250/25.100", result);
    }

    @Test
    public void testWrite4DEmptyList() {
        List<FtnAddress> addresses = new ArrayList<>();
        String result = FtnTools.write4D(addresses);
        assertEquals("", result);
    }

    @Test
    public void testWrite4DSingleAddress() {
        List<FtnAddress> addresses = Arrays.asList(new FtnAddress(2, 5020, 1042, 0));
        String result = FtnTools.write4D(addresses);
        assertEquals("2:5020/1042", result);
    }

    @Test
    public void testWrite4DWithZeroPoint() {
        List<FtnAddress> addresses = Arrays.asList(
            new FtnAddress(2, 5020, 1042, 0),
            new FtnAddress(1, 250, 25, 0)
        );
        
        String result = FtnTools.write4D(addresses);
        assertEquals("2:5020/1042 1:250/25", result);
    }

    @Test
    public void testRoundTripRead4DWrite4D() {
        String originalAddresses = "2:5020/1042 1:250/25.100 3:463/68";
        
        List<FtnAddress> parsed = FtnTools.read4D(originalAddresses);
        String reconstructed = FtnTools.write4D(parsed);
        
        assertEquals(originalAddresses, reconstructed);
    }

    @Test
    public void testRoundTripRead2DWrite2D() {
        String originalAddresses = "250/25 463/68 5000/111";
        
        List<Ftn2D> parsed = FtnTools.read2D(originalAddresses);
        String reconstructed = FtnTools.write2D(parsed, false);
        
        assertEquals(originalAddresses, reconstructed);
    }
}
