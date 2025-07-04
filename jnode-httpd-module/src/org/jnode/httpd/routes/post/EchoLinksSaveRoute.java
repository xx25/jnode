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

package org.jnode.httpd.routes.post;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jnode.dto.Link;
import jnode.dto.Echoarea;
import jnode.dto.Subscription;
import jnode.orm.ORMManager;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public class EchoLinksSaveRoute implements Handler {

	@Override
	public void handle(Context ctx) throws Exception {
		String echoId = ctx.formParam("echo_id");
		
		if (echoId == null) {
			ctx.status(400).result("Echo ID is required");
			return;
		}
		
		try {
			Long eid = Long.valueOf(echoId);
			Echoarea echo = ORMManager.get(Echoarea.class).getById(eid);
			
			if (echo == null) {
				ctx.status(404).result("Echo not found");
				return;
			}
			
			// Get all form parameters to find checked links
			Map<String, List<String>> formParams = ctx.formParamMap();
			Set<Long> checkedLinkIds = new HashSet<>();
			
			// Find all checked link checkboxes
			for (String paramName : formParams.keySet()) {
				if (paramName.startsWith("link_")) {
					String linkId = ctx.formParam(paramName);
					if (linkId != null) {
						try {
							checkedLinkIds.add(Long.valueOf(linkId));
						} catch (NumberFormatException e) {
							// Skip invalid link IDs
						}
					}
				}
			}
			
			// Get all current subscriptions for this echo
			List<Subscription> currentSubs = ORMManager.get(Subscription.class)
					.getAnd("echoarea_id", "=", echo.getId());
			
			// Process unsubscriptions (remove unchecked links)
			for (Subscription sub : currentSubs) {
				if (!checkedLinkIds.contains(sub.getLink().getId())) {
					// Use delete with conditions for composite key
					ORMManager.get(Subscription.class).delete("link_id", "=",
							sub.getLink(), "echoarea_id", "=", echo);
				}
			}
			
			// Process new subscriptions (add checked links that aren't already subscribed)
			for (Long linkId : checkedLinkIds) {
				// Check if already subscribed
				Subscription existing = ORMManager.get(Subscription.class)
						.getFirstAnd("echoarea_id", "=", echo.getId(), "link_id", "=", linkId);
				
				if (existing == null) {
					// Create new subscription
					Link link = ORMManager.get(Link.class).getById(linkId);
					if (link != null) {
						Subscription newSub = new Subscription();
						newSub.setLink(link);
						newSub.setArea(echo);
						ORMManager.get(Subscription.class).save(newSub);
					}
				}
			}
			
			// Redirect back to echo links page with success
			ctx.redirect("/secure/echo-links.html?echo=" + echoId);
			
		} catch (NumberFormatException e) {
			ctx.status(400).result("Invalid echo ID");
		} catch (Exception e) {
			ctx.status(500).result("Error saving subscriptions: " + e.getMessage());
		}
	}
}