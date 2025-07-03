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

package org.jnode.httpd.routes.post;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jnode.dto.Link;
import jnode.dto.Filearea;
import jnode.dto.FileSubscription;
import jnode.orm.ORMManager;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public class LinkFileareasSaveRoute implements Handler {

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
			
			// Get all form parameters to find checked file areas
			Map<String, List<String>> formParams = ctx.formParamMap();
			Set<Long> checkedFileareaIds = new HashSet<>();
			
			// Find all checked filearea checkboxes
			for (String paramName : formParams.keySet()) {
				if (paramName.startsWith("filearea_")) {
					String fileareaId = ctx.formParam(paramName);
					if (fileareaId != null) {
						try {
							checkedFileareaIds.add(Long.valueOf(fileareaId));
						} catch (NumberFormatException e) {
							// Skip invalid file area IDs
						}
					}
				}
			}
			
			// Get all current subscriptions for this link
			List<FileSubscription> currentSubs = ORMManager.get(FileSubscription.class)
					.getAnd("link_id", "=", link.getId());
			
			// Process unsubscriptions (remove unchecked file areas)
			for (FileSubscription sub : currentSubs) {
				if (!checkedFileareaIds.contains(sub.getArea().getId())) {
					// Use delete with conditions for composite key
					ORMManager.get(FileSubscription.class).delete("link_id", "=",
							link, "filearea_id", "=", sub.getArea());
				}
			}
			
			// Process new subscriptions (add checked file areas that aren't already subscribed)
			for (Long fileareaId : checkedFileareaIds) {
				// Check if already subscribed
				FileSubscription existing = ORMManager.get(FileSubscription.class)
						.getFirstAnd("filearea_id", "=", fileareaId, "link_id", "=", link.getId());
				
				if (existing == null) {
					// Create new subscription
					Filearea area = ORMManager.get(Filearea.class).getById(fileareaId);
					if (area != null) {
						FileSubscription newSub = new FileSubscription();
						newSub.setLink(link);
						newSub.setArea(area);
						ORMManager.get(FileSubscription.class).save(newSub);
					}
				}
			}
			
			// Redirect back to link file areas page with success
			ctx.redirect("/secure/link-fileareas.html?link=" + linkId);
			
		} catch (NumberFormatException e) {
			ctx.status(400).result("Invalid link ID");
		} catch (Exception e) {
			ctx.status(500).result("Error saving subscriptions: " + e.getMessage());
		}
	}
}