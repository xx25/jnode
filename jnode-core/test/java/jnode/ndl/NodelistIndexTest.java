/*
 * Licensed to the jNode FTN Platform Develpoment Team (jNode Team)
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

import java.util.Date;

import jnode.ftn.types.FtnAddress;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class NodelistIndexTest {

	@Test
	public void testNodelist() {
		NodelistIndex idx = NodelistScanner.getInstance().createIndex(
				NodelistIndexTest.class.getResourceAsStream("nodelist"),
				new Date().getTime());
		
		FtnAddress a = new FtnAddress("2:5020/848");
		FtnNdlAddress ndl = idx.exists(a);
		int binkpPort = ndl.getBinkpPort();
		String hostname = ndl.getInetHost();
		
		assertNotNull(ndl);
		assertNotNull(idx);
		assertEquals(24554, binkpPort);
		assertEquals("fidonode.in", hostname);
		
		a = new FtnAddress("2:5020/2141");
		ndl = idx.exists(a);
		binkpPort = ndl.getBinkpPort();
		hostname = ndl.getInetHost();
		
		assertNotNull(ndl);
		assertNotNull(idx);
		assertEquals(24555, binkpPort);
		assertEquals("vp.propush.ru", hostname);
		
	}
}
