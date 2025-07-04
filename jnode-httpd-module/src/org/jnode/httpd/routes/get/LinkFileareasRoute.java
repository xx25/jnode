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

package org.jnode.httpd.routes.get;

import java.sql.SQLException;

import jnode.dto.Link;
import jnode.dto.Filearea;
import jnode.orm.ORMManager;

import org.jnode.httpd.util.HTML;
import org.jnode.httpd.util.HTMLi18n;

import com.j256.ormlite.dao.GenericRawResults;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public class LinkFileareasRoute implements Handler {
	private static String linkFileareas = null;

	public LinkFileareasRoute() {
		if (linkFileareas == null) {
			linkFileareas = HTML.getContents("/parts/link-fileareas_i18n.html");
		}
	}

	@Override
	public void handle(Context ctx) throws Exception {
		String linkId = ctx.queryParam("link");
		
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
			
			StringBuilder subscribedFileareas = new StringBuilder();
			StringBuilder unsubscribedFileareas = new StringBuilder();
			
			// Get all file areas with subscription status
			GenericRawResults<String[]> fileareas = ORMManager
					.get(Filearea.class)
					.getRaw(String.format(
						"SELECT fa.id, fa.name, fa.description, " +
						"CASE WHEN fs.link_id IS NOT NULL THEN 1 ELSE 0 END as subscribed " +
						"FROM filearea fa " +
						"LEFT JOIN filesubscription fs ON (fa.id = fs.filearea_id AND fs.link_id = %d) " +
						"ORDER BY fa.name",
						link.getId()));
			
			for (String[] filearea : fileareas.getResults()) {
				String fileareaId = filearea[0];
				String fileareaName = filearea[1] != null ? filearea[1] : "";
				String fileareaDescription = filearea[2] != null ? filearea[2] : "";
				boolean isSubscribed = "1".equals(filearea[3]);
				
				String row = String.format(
					"<tr><td><input type=\"checkbox\" name=\"filearea_%s\" value=\"%s\" %s /></td>" +
					"<td>%s</td><td>%s</td></tr>", 
					fileareaId, fileareaId, isSubscribed ? "checked" : "",
					fileareaName, fileareaDescription);
				
				if (isSubscribed) {
					subscribedFileareas.append(row);
				} else {
					unsubscribedFileareas.append(row);
				}
			}
			fileareas.close();
			
			HTMLi18n html = HTMLi18n.create(ctx, true);
			
			// Format the title with link name and address
			String title = html.t("link_fileareas.title", link.getLinkName(), link.getLinkAddress());
			
			ctx.html(html
					.append(String.format(linkFileareas, 
						title,
						link.getId(),
						subscribedFileareas.toString(),
						unsubscribedFileareas.toString()))
					.footer().get());
			
		} catch (NumberFormatException e) {
			ctx.status(400).result("Invalid link ID");
		} catch (SQLException e) {
			ctx.status(500).result("Database error: " + e.getMessage());
		}
	}
}