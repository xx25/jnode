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

import jnode.dto.Filearea;
import jnode.dto.Link;
import jnode.orm.ORMManager;

import org.jnode.httpd.util.HTML;
import org.jnode.httpd.util.HTMLi18n;

import com.j256.ormlite.dao.GenericRawResults;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public class FileLinksRoute implements Handler {
	private static String fileLinks = null;

	public FileLinksRoute() {
		if (fileLinks == null) {
			fileLinks = HTML.getContents("/parts/file-links_i18n.html");
		}
	}

	@Override
	public void handle(Context ctx) throws Exception {
		String fileareaId = ctx.queryParam("filearea");
		
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
			
			StringBuilder subscribedLinks = new StringBuilder();
			StringBuilder unsubscribedLinks = new StringBuilder();
			
			// Get all links with subscription status for this file area
			GenericRawResults<String[]> links = ORMManager
					.get(Link.class)
					.getRaw(String.format(
						"SELECT l.id, l.station_name, l.ftn_address, " +
						"CASE WHEN fs.filearea_id IS NOT NULL THEN 1 ELSE 0 END as subscribed " +
						"FROM links l " +
						"LEFT JOIN filesubscription fs ON (l.id = fs.link_id AND fs.filearea_id = %d) " +
						"ORDER BY l.ftn_address",
						filearea.getId()));
			
			for (String[] link : links.getResults()) {
				String linkId = link[0];
				String linkName = link[1] != null ? link[1] : "";
				String linkAddress = link[2] != null ? link[2] : "";
				boolean isSubscribed = "1".equals(link[3]);
				
				String row = String.format(
					"<tr><td><input type=\"checkbox\" name=\"link_%s\" value=\"%s\" %s /></td>" +
					"<td>%s</td><td>%s</td></tr>", 
					linkId, linkId, isSubscribed ? "checked" : "",
					linkName, linkAddress);
				
				if (isSubscribed) {
					subscribedLinks.append(row);
				} else {
					unsubscribedLinks.append(row);
				}
			}
			links.close();
			
			HTMLi18n html = HTMLi18n.create(ctx, true);
			
			// Format the title with file area name
			String title = html.t("file_links.title", filearea.getName());
			
			ctx.html(html
					.append(String.format(fileLinks, 
						title,
						filearea.getDescription(),
						filearea.getId(),
						subscribedLinks.toString(),
						unsubscribedLinks.toString()))
					.footer().get());
			
		} catch (NumberFormatException e) {
			ctx.status(400).result("Invalid file area ID");
		} catch (SQLException e) {
			ctx.status(500).result("Database error: " + e.getMessage());
		}
	}
}