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

import jnode.dto.Echoarea;
import jnode.orm.ORMManager;

import org.jnode.httpd.util.HTML;
import org.jnode.httpd.util.HTMLi18n;
import org.jnode.httpd.util.JSONUtil;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public class EchoareasRoute implements Handler {
	private static String echoareas = null;

	public EchoareasRoute() {
		if (echoareas == null) {
			echoareas = HTML.getContents("/parts/echoes_i18n.html");
		}
	}

	@Override
	public void handle(Context ctx) throws Exception {
		String id = ctx.queryParam("id");
		StringBuilder sb = new StringBuilder();
		if (id == null) {
			HTMLi18n html = HTMLi18n.create(ctx, true);
			for (Echoarea e : ORMManager.get(Echoarea.class).getOrderAnd(
					"name", true)) {
				sb.append(String
						.format("<tr><td>%s</td><td>%s</td><td>r:%d|w:%d|g:%s</td><td><a href=\"#new\" class=\"css-link-1\" onclick=\"edit(%d);\">%s</a>&nbsp;<a href=\"/secure/echo-links.html?echo=%d\" class=\"css-link-1\">%s</a>&nbsp;<a href=\"#\" class=\"css-link-1\" onclick=\"del(%d);\">%s</a></td></tr>",
								e.getName(), e.getDescription(),
								e.getReadlevel(), e.getWritelevel(),
								e.getGroup(), e.getId(), html.t("action.edit"), e.getId(), html.t("label.view_links"), e.getId(), html.t("action.delete")));
			}
			html.append(String.format(echoareas, sb.toString()));
			html.footer();
			ctx.html(html.get());
			return;
		} else {
			try {
				String cb = ctx.queryParam("cb");
				if (cb != null) {
					sb.append(cb + "(");
				}
				Long eid = Long.valueOf(id);
				sb.append(JSONUtil.value(ORMManager.get(Echoarea.class)
						.getById(eid)));
				if (cb != null) {
					sb.append(")");
				}
				ctx.contentType("text/javascript");
				ctx.result(sb.toString());
				return;
			} catch (RuntimeException e) {
			}
		}
	}
}
