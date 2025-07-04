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

import com.j256.ormlite.dao.GenericRawResults;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public class LinkEchoesSaveRoute implements Handler {

	@Override
	public void handle(Context ctx) throws Exception {
		String linkId = ctx.formParam("link_id");
		
		if (linkId == null) {
			ctx.status(400).result("Link ID is required");
			return;
		}
		
		try {
			Long lid = Long.valueOf(linkId);
			Link link = ORMManager.get(Link.class).getById(lid);
			
			if (link == null) {
				ctx.status(404).result("Link not found");
				return;
			}
			
			// Get all form parameters to find checked echoes
			Map<String, List<String>> formParams = ctx.formParamMap();
			Set<Long> checkedEchoIds = new HashSet<>();
			
			// Find all checked echo checkboxes
			for (String paramName : formParams.keySet()) {
				if (paramName.startsWith("echo_")) {
					String echoId = ctx.formParam(paramName);
					if (echoId != null) {
						try {
							checkedEchoIds.add(Long.valueOf(echoId));
						} catch (NumberFormatException e) {
							// Skip invalid echo IDs
						}
					}
				}
			}
			
			// Get all current subscriptions for this link
			List<Subscription> currentSubs = ORMManager.get(Subscription.class)
					.getAnd("link_id", "=", link.getId());
			
			// Process unsubscriptions (remove unchecked echoes)
			for (Subscription sub : currentSubs) {
				if (!checkedEchoIds.contains(sub.getArea().getId())) {
					// Use delete with conditions for composite key
					ORMManager.get(Subscription.class).delete("link_id", "=",
							link, "echoarea_id", "=", sub.getArea());
				}
			}
			
			// Process new subscriptions (add checked echoes that aren't already subscribed)
			for (Long echoId : checkedEchoIds) {
				// Check if already subscribed
				Subscription existing = ORMManager.get(Subscription.class)
						.getFirstAnd("echoarea_id", "=", echoId, "link_id", "=", link.getId());
				
				if (existing == null) {
					// Use the SQL pattern from documentation to ensure proper subscription
					Echoarea area = ORMManager.get(Echoarea.class).getById(echoId);
					if (area != null) {
						Subscription newSub = new Subscription();
						newSub.setLink(link);
						newSub.setArea(area);
						ORMManager.get(Subscription.class).save(newSub);
					}
				}
			}
			
			// Redirect back to links page with success
			ctx.redirect("/secure/link-echoes.html?link=" + linkId);
			
		} catch (NumberFormatException e) {
			ctx.status(400).result("Invalid link ID");
		} catch (Exception e) {
			ctx.status(500).result("Error saving subscriptions: " + e.getMessage());
		}
	}
}