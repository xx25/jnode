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

package org.jnode.xmpp;

import jnode.event.IEvent;
import jnode.logger.Logger;
import jnode.module.JnodeModule;
import jnode.module.JnodeModuleException;

public class XMPPModule extends JnodeModule {
	private final XMPPClient client;
	private static final Logger logger = Logger.getLogger(XMPPModule.class);

	public XMPPModule(String configFile) throws JnodeModuleException {
		super(configFile);
		client = new XMPPClient(properties);
		if (!client.testConnection()) {
			throw new JnodeModuleException("Invalid XMPP configuration");
		}
	}

	@Override
	public void handle(IEvent event) {
		// TODO Auto-generated method stub

	}

	@Override
	public void start() {
		synchronized (client) {
			while (true) {
				logger.l3("Running XMPP client...");
				client.run();
				try {
					client.wait();
				} catch (InterruptedException e) {
				}
			}
		}
	}

}
