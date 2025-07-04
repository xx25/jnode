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

import jnode.dto.Echoarea;
import jnode.dto.Link;
import jnode.orm.ORMManager;

import org.jnode.httpd.util.HTML;
import org.jnode.httpd.util.HTMLi18n;

import com.j256.ormlite.dao.GenericRawResults;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public class EchoLinksRoute implements Handler {
	private static String echoLinks = null;

	public EchoLinksRoute() {
		if (echoLinks == null) {
			echoLinks = HTML.getContents("/parts/echo-links_i18n.html");
		}
	}

	@Override
	public void handle(Context ctx) throws Exception {
		String echoId = ctx.queryParam("echo");
		
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
			
			StringBuilder subscribedLinks = new StringBuilder();
			StringBuilder unsubscribedLinks = new StringBuilder();
			
			// Get all links with subscription status for this echo
			GenericRawResults<String[]> links = ORMManager
					.get(Link.class)
					.getRaw(String.format(
						"SELECT l.id, l.station_name, l.ftn_address, " +
						"CASE WHEN s.echoarea_id IS NOT NULL THEN 1 ELSE 0 END as subscribed " +
						"FROM links l " +
						"LEFT JOIN subscription s ON (l.id = s.link_id AND s.echoarea_id = %d) " +
						"ORDER BY l.ftn_address",
						echo.getId()));
			
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
			
			// Format the title with echo name
			String title = html.t("echo_links.title", echo.getName());
			
			ctx.html(html
					.append(String.format(echoLinks, 
						title,
						echo.getDescription(),
						echo.getId(),
						subscribedLinks.toString(),
						unsubscribedLinks.toString()))
					.footer().get());
			
		} catch (NumberFormatException e) {
			ctx.status(400).result("Invalid echo ID");
		} catch (SQLException e) {
			ctx.status(500).result("Database error: " + e.getMessage());
		}
	}
}