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

package jnode.report;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.List;

/**
 * @author Kirill Temnenkov (ktemnenkov@intervale.ru)
 */
public class ReportBuilderTest {

    @Test
    public void testArrayString() throws Exception {
        List<String> res = ReportBuilder.asStrList("1,2,3", ",");
        assertNotNull(res);
        assertEquals(3, res.size());
        assertEquals("1", res.get(0));
        assertEquals("2", res.get(1));
        assertEquals("3", res.get(2));
    }

    @Test
    public void testArrayInt() throws Exception {
        List<Integer> res = ReportBuilder.asIntList("1,2,3", ",");
        assertNotNull(res);
        assertEquals(3, res.size());
        assertEquals(Integer.valueOf(1), res.get(0));
        assertEquals(Integer.valueOf(2), res.get(1));
        assertEquals(Integer.valueOf(3), res.get(2));
    }

    @Test
    public void testAddItem() throws Exception {
        StringBuilder sb = new StringBuilder();
        ReportBuilder.addItem(sb, "123", 5);
        assertEquals("123  ", sb.toString());
    }

    @Test
    public void testAddItemShort() throws Exception {
        StringBuilder sb = new StringBuilder();
        ReportBuilder.addItem(sb, "12345", 3);
        assertEquals("123", sb.toString());
    }

    @Test
    public void testAddItemNoConvert() throws Exception {
        StringBuilder sb = new StringBuilder();
        ReportBuilder.addItem(sb, "12345", 5);
        assertEquals("12345", sb.toString());
    }

    @Test
    public void testAddItemConvert() throws Exception {
        StringBuilder sb = new StringBuilder();
        ReportBuilder b = new ReportBuilder();
        b.setColumns("Test", ",");
        b.setColLength("40", ",");
        b.setFormats(Arrays.asList("S"));
        ReportBuilder.addItem(sb, b.convert("12345", 0), 5);
        assertEquals("12345", sb.toString());
    }

    @Test
    public void testAddItemConvertDate() throws Exception {
        StringBuilder sb = new StringBuilder();
        ReportBuilder b = new ReportBuilder();
        b.setColumns("Test", ",");
        b.setColLength("10", ",");
        b.setFormats("D",",");
        ReportBuilder.addItem(sb, b.convert("1384007690000", 0), 10);
        assertEquals("09.11.2013", sb.toString());
    }

    @Test
    public void testAddCenterItem() throws Exception {
        StringBuilder sb = new StringBuilder();
        ReportBuilder.addCenterItem(sb, "123", 5);
        assertEquals(" 123 ", sb.toString());
    }

    @Test
    public void testAddCenterItem2() throws Exception {
        StringBuilder sb = new StringBuilder();
        ReportBuilder.addCenterItem(sb, "123", 6);
        assertEquals(" 123  ", sb.toString());
    }

    @Test
    public void testVer() throws Exception {
        StringBuilder sb = new StringBuilder();
        ReportBuilder.addVer(sb);
        assertEquals("|", sb.toString());
    }

    @Test
    public void testNewLine() throws Exception {
        StringBuilder sb = new StringBuilder();
        ReportBuilder.newLine(sb);
        assertEquals("\n", sb.toString());
    }

    @Test
    public void testWidth() throws Exception {
        ReportBuilder builder = new ReportBuilder();
        builder.setColLength(Arrays.asList(1, 2, 4));
        assertEquals(11, builder.getWidth());
    }

    @Test
    public void testHorLine() throws Exception {
        ReportBuilder builder = new ReportBuilder();
        builder.setColLength(Arrays.asList(1, 2, 4));
        StringBuilder sb = new StringBuilder();
        builder.horLine(sb);
        assertEquals("+-+--+----+\n", sb.toString());
    }
}
