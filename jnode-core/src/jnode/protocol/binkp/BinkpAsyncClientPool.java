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

package jnode.protocol.binkp;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;

import jnode.dto.Link;
import jnode.logger.Logger;
import jnode.main.MainHandler;
import jnode.main.threads.PollQueue;
import jnode.main.threads.ThreadPool;
import jnode.protocol.binkp.connector.BinkpAbstractConnector;
import jnode.protocol.binkp.connector.BinkpAsyncConnector;

public class BinkpAsyncClientPool implements Runnable {
	private static final Logger logger = Logger
			.getLogger(BinkpAsyncClientPool.class);
	private static final String BINKD_CLIENT = "binkp.client";

	@Override
	public void run() {
		if (!MainHandler.getCurrentInstance().getBooleanProperty(BINKD_CLIENT,
				true)) {
			return;
		}
		while (true) {
			Link l = null;
			synchronized (PollQueue.getSelf()) {
				if (PollQueue.getSelf().isEmpty()) {
					try {
						PollQueue.getSelf().wait();
					} catch (InterruptedException e) {
					}
				}
				l = PollQueue.getSelf().getNext();
			}
			// Try all available addresses with retry logic
			boolean connected = false;
			List<String> addresses = l.getAllResolvedProtocolAddresses();
			
			if (addresses.isEmpty()) {
				logger.l3("No addresses available for link " + l.getLinkAddress());
				continue;
			}
			
			for (String pa : addresses) {
				if ("-".equals(pa)) {
					logger.l3("Skipping disabled address for link " + l.getLinkAddress());
					continue;
				}
				
				try {
					BinkpAbstractConnector conn = null;
					// Check for custom connector types
					for (String key : BinkpConnectorRegistry.getSelf().getKeys()) {
						if (pa.startsWith(key)) {
							conn = createConnector(pa, key);
							break;
						}
					}
					if (conn == null) {
						conn = new BinkpAsyncConnector(pa);
					}
					
					logger.l4("Attempting connection to " + extractHostFromProtocolAddress(pa) + " for link " + l.getLinkAddress());
					ThreadPool.execute(conn);
					connected = true;
					break; // Successfully created connector, break from address loop
					
				} catch (RuntimeException e) {
					logger.l2("Runtime exception connecting to " + extractHostFromProtocolAddress(pa) + ": " + e.getLocalizedMessage(), e);
				} catch (ConnectException e) {
					String host = extractHostFromProtocolAddress(pa);
					logger.l3("Connection failed to " + host + ": " + e.getLocalizedMessage());
					// Continue to next address
				} catch (SocketTimeoutException e) {
					String host = extractHostFromProtocolAddress(pa);
					logger.l3("Socket timeout connecting to " + host + ": " + e.getLocalizedMessage());
					// Continue to next address
				} catch (UnknownHostException e) {
					String host = extractHostFromProtocolAddress(pa);
					logger.l3("Unknown host " + host + ": " + e.getLocalizedMessage());
					// Continue to next address
				} catch (IOException e) {
					String host = extractHostFromProtocolAddress(pa);
					logger.l3("IO error connecting to " + host + ": " + e.getLocalizedMessage());
					// Continue to next address
				}
			}
			
			if (!connected) {
				logger.l2("Failed to connect to any address for link " + l.getLinkAddress() + " (tried " + addresses.size() + " addresses)");
			}
		}
	}

	protected BinkpAbstractConnector createConnector(String protocolAddress,
			String key) throws IOException {
		Class<? extends BinkpAbstractConnector> connectorClass = BinkpConnectorRegistry
				.getSelf().getConnector(key);
		try {
			return connectorClass.getConstructor(String.class).newInstance(
					protocolAddress.substring(key.length()));
		} catch (Exception e) {
			throw new IOException("Error instatiating class "
					+ connectorClass.getName() + " ( " + protocolAddress
					+ " ) ", e);
		}
	}

	private String extractHostFromProtocolAddress(String protocolAddress) {
		if (protocolAddress == null) {
			return "unknown";
		}
		int portIndex = protocolAddress.lastIndexOf(':');
		if (portIndex > 0) {
			return protocolAddress.substring(0, portIndex);
		}
		return protocolAddress;
	}
}
