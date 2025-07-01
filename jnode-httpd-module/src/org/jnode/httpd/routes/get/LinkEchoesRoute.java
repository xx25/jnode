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

package org.jnode.httpd.routes.get;

import java.sql.SQLException;

import jnode.dto.Link;
import jnode.dto.Echoarea;
import jnode.orm.ORMManager;

import org.jnode.httpd.util.HTML;
import org.jnode.httpd.util.HTMLi18n;

import com.j256.ormlite.dao.GenericRawResults;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public class LinkEchoesRoute implements Handler {
	private static String linkEchoes = null;

	public LinkEchoesRoute() {
		if (linkEchoes == null) {
			linkEchoes = HTML.getContents("/parts/link-echoes_i18n.html");
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
			
			StringBuilder subscribedEchoes = new StringBuilder();
			StringBuilder unsubscribedEchoes = new StringBuilder();
			
			// Get all echoes with subscription status
			GenericRawResults<String[]> echoes = ORMManager
					.get(Echoarea.class)
					.getRaw(String.format(
						"SELECT e.id, e.name, e.description, " +
						"CASE WHEN s.link_id IS NOT NULL THEN 1 ELSE 0 END as subscribed " +
						"FROM echoarea e " +
						"LEFT JOIN subscription s ON (e.id = s.echoarea_id AND s.link_id = %d) " +
						"ORDER BY e.name",
						link.getId()));
			
			for (String[] echo : echoes.getResults()) {
				String echoId = echo[0];
				String echoName = echo[1] != null ? echo[1] : "";
				String echoDescription = echo[2] != null ? echo[2] : "";
				boolean isSubscribed = "1".equals(echo[3]);
				
				String row = String.format(
					"<tr><td><input type=\"checkbox\" name=\"echo_%s\" value=\"%s\" %s /></td>" +
					"<td>%s</td><td>%s</td></tr>", 
					echoId, echoId, isSubscribed ? "checked" : "",
					echoName, echoDescription);
				
				if (isSubscribed) {
					subscribedEchoes.append(row);
				} else {
					unsubscribedEchoes.append(row);
				}
			}
			echoes.close();
			
			HTMLi18n html = HTMLi18n.create(ctx, true);
			ctx.html(html
					.append(String.format(linkEchoes, 
						link.getLinkName(), 
						link.getLinkAddress(),
						link.getId(),
						subscribedEchoes.toString(),
						unsubscribedEchoes.toString()))
					.footer().get());
			
		} catch (NumberFormatException e) {
			ctx.status(400).result("Invalid link ID");
		} catch (SQLException e) {
			ctx.status(500).result("Database error: " + e.getMessage());
		}
	}
}