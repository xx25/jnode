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

import java.util.List;

import org.jnode.httpd.util.HTML;
import org.jnode.httpd.util.HTMLi18n;

import jnode.dto.NetmailAcceptRule;
import jnode.orm.ORMManager;
import io.javalin.http.Context;
import io.javalin.http.Handler;

/**
 * Netmail acceptance rules configuration page
 * 
 * @author jnode
 */
public class NetmailAcceptRulesRoute implements Handler {
	private static String template = null;

	public NetmailAcceptRulesRoute() {
		if (template == null) {
			template = HTML.getContents("/parts/netmail-accept_i18n.html");
		}
	}

	@Override
	public void handle(Context ctx) throws Exception {
		HTMLi18n html = HTMLi18n.create(ctx, true);
		List<NetmailAcceptRule> rules = ORMManager.get(NetmailAcceptRule.class)
			.getOrderAnd("nice", true);
			
		StringBuilder rulesTable = new StringBuilder();
		
		for (NetmailAcceptRule rule : rules) {
			rulesTable.append(String.format(
				"<tr class=\"%s\">" +
				"<td>%d</td>" +
				"<td>%s</td>" + 
				"<td><code>%s</code></td>" +
				"<td><code>%s</code></td>" +
				"<td><code>%s</code></td>" +
				"<td><code>%s</code></td>" +
				"<td><code>%s</code></td>" +
				"<td class=\"action-%s\">%s</td>" +
				"<td>%s</td>" +
				"<td>%s</td>" +
				"<td>" +
				"<a href=\"javascript:edit(%d)\">%s</a> | " +
				"<a href=\"javascript:del(%d)\">%s</a>" +
				"</td>" +
				"</tr>",
				rule.getEnabled() ? "enabled" : "disabled",
				rule.getPriority(),
				rule.getEnabled() ? html.t("label.yes") : html.t("label.no"),
				htmlEscape(rule.getFromAddress()),
				htmlEscape(rule.getToAddress()), 
				htmlEscape(rule.getFromName()),
				htmlEscape(rule.getToName()),
				htmlEscape(rule.getSubject()),
				rule.getAction().toString().toLowerCase(),
				rule.getAction(),
				rule.getStopProcessing() ? html.t("label.yes") : html.t("label.no"),
				htmlEscape(rule.getDescription()),
				rule.getId(), html.t("action.edit"),
				rule.getId(), html.t("action.delete")
			));
		}
		
		ctx.html(html
			.append(String.format(template, rulesTable.toString()))
			.footer().get());
	}
	
	private String htmlEscape(String str) {
		if (str == null) return "*";
		return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}