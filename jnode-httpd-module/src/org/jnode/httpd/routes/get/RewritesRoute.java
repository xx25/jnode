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

import java.util.List;

import jnode.dto.Rewrite;
import jnode.orm.ORMManager;

import org.jnode.httpd.util.HTML;
import org.jnode.httpd.util.HTMLi18n;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public class RewritesRoute implements Handler {
	private static String rewrites = null;

	public RewritesRoute() {
		if (rewrites == null) {
			rewrites = HTML.getContents("/parts/rewrite_i18n.html");
		}
	}

	@Override
	public void handle(Context ctx) throws Exception {
		String id = ctx.queryParam("id");
		if (id != null) {
			// AJAX call for editing
			String cb = ctx.queryParam("cb");
			StringBuilder sb = new StringBuilder();
			
			if (cb != null) {
				sb.append(cb + "(");
			}
			
			Rewrite rewrite = ORMManager.get(Rewrite.class).getById(Long.valueOf(id));
			if (rewrite != null) {
				sb.append(String.format(
					"{\"id\":%d,\"nice\":%d,\"type\":\"%s\",\"last\":%b,\"origFromAddr\":\"%s\",\"newFromAddr\":\"%s\",\"origFromName\":\"%s\",\"newFromName\":\"%s\",\"origToAddr\":\"%s\",\"newToAddr\":\"%s\",\"origToName\":\"%s\",\"newToName\":\"%s\",\"origSubject\":\"%s\",\"newSubject\":\"%s\"}",
					rewrite.getId(), rewrite.getNice(), rewrite.getType().name(), rewrite.isLast(),
					jsonEscape(rewrite.getOrig_from_addr()), jsonEscape(rewrite.getNew_from_addr()),
					jsonEscape(rewrite.getOrig_from_name()), jsonEscape(rewrite.getNew_from_name()),
					jsonEscape(rewrite.getOrig_to_addr()), jsonEscape(rewrite.getNew_to_addr()),
					jsonEscape(rewrite.getOrig_to_name()), jsonEscape(rewrite.getNew_to_name()),
					jsonEscape(rewrite.getOrig_subject()), jsonEscape(rewrite.getNew_subject())));
			} else {
				sb.append("null");
			}
			
			if (cb != null) {
				sb.append(")");
			}
			
			ctx.contentType("text/javascript");
			ctx.result(sb.toString());
			return;
		}
		
		HTMLi18n html = HTMLi18n.create(ctx, true);
		List<Rewrite> rewrites = ORMManager.get(Rewrite.class).getOrderAnd(
				"nice", true);
		StringBuilder sb = new StringBuilder();
		for (Rewrite r : rewrites) {
			sb.append(String
					.format("<tr>"
							+ "<td>%d</td>"
							+ "<td>%s/%b</td>"
							+ "<td><b>%s</b> -&gt; <b>%s</b></td>"
							+ "<td><b>%s</b> -&gt; <b>%s</b></td>"
							+ "<td><b>%s</b> -&gt; <b>%s</b></td>"
							+ "<td><b>%s</b> -&gt; <b>%s</b></td>"
							+ "<td><b>%s</b> -&gt; <b>%s</b></td>"
							+ "<td><a href=\"#\" class=\"css-link-1\" onclick=\"edit(%d);\">%s</a>&nbsp;<a href=\"#\" class=\"css-link-1\" onclick=\"del(%d);\">%s</a></td>"
							+ "</tr>", r.getNice(), r.getType().name(),
							r.isLast(),

							r.getOrig_from_addr(), r.getNew_from_addr(),
							r.getOrig_from_name(), r.getNew_from_name(),
							r.getOrig_to_addr(), r.getNew_to_addr(),
							r.getOrig_to_name(), r.getNew_to_name(),
							r.getOrig_subject(), r.getNew_subject(), 
							r.getId(), html.t("action.edit"),
							r.getId(), html.t("action.delete")));
		}
		ctx.html(html
				.append(String.format(RewritesRoute.rewrites, sb.toString()))
				.footer().get());
	}
	
	private String jsonEscape(String str) {
		if (str == null) return "";
		return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

}
