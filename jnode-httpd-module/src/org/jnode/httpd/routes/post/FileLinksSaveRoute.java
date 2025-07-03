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

public class FileLinksSaveRoute implements Handler {

	@Override
	public void handle(Context ctx) throws Exception {
		String fileareaId = ctx.formParam("filearea_id");
		
		if (fileareaId == null) {
			ctx.status(400).result("File area ID is required");
			return;
		}
		
		try {
			Long fid = Long.valueOf(fileareaId);
			Filearea filearea = ORMManager.get(Filearea.class).getById(fid);
			
			if (filearea == null) {
				ctx.status(404).result("File area not found");
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
			
			// Get all current subscriptions for this file area
			List<FileSubscription> currentSubs = ORMManager.get(FileSubscription.class)
					.getAnd("filearea_id", "=", filearea.getId());
			
			// Process unsubscriptions (remove unchecked links)
			for (FileSubscription sub : currentSubs) {
				if (!checkedLinkIds.contains(sub.getLink().getId())) {
					// Use delete with conditions for composite key
					ORMManager.get(FileSubscription.class).delete("link_id", "=",
							sub.getLink(), "filearea_id", "=", filearea);
				}
			}
			
			// Process new subscriptions (add checked links that aren't already subscribed)
			for (Long linkId : checkedLinkIds) {
				// Check if already subscribed
				FileSubscription existing = ORMManager.get(FileSubscription.class)
						.getFirstAnd("filearea_id", "=", filearea.getId(), "link_id", "=", linkId);
				
				if (existing == null) {
					// Create new subscription
					Link link = ORMManager.get(Link.class).getById(linkId);
					if (link != null) {
						FileSubscription newSub = new FileSubscription();
						newSub.setLink(link);
						newSub.setArea(filearea);
						ORMManager.get(FileSubscription.class).save(newSub);
					}
				}
			}
			
			// Redirect back to file links page with success
			ctx.redirect("/secure/file-links.html?filearea=" + fileareaId);
			
		} catch (NumberFormatException e) {
			ctx.status(400).result("Invalid file area ID");
		} catch (Exception e) {
			ctx.status(500).result("Error saving subscriptions: " + e.getMessage());
		}
	}
}