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

import jnode.dto.NetmailAcceptRule;
import jnode.orm.ORMManager;
import spark.Request;
import spark.Response;

/**
 * Netmail acceptance rules configuration page
 * 
 * @author jnode
 */
public class NetmailAcceptRulesRoute extends spark.Route {
	private static String template = null;

	public NetmailAcceptRulesRoute() {
		super("/secure/netmail-accept.html");
		if (template == null) {
			template = HTML.getContents("/parts/netmail-accept.html");
		}
	}

	@Override
	public Object handle(Request req, Response resp) {
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
				"<a href=\"javascript:edit(%d)\">Edit</a> | " +
				"<a href=\"javascript:del(%d)\">Delete</a>" +
				"</td>" +
				"</tr>",
				rule.getEnabled() ? "enabled" : "disabled",
				rule.getPriority(),
				rule.getEnabled() ? "Yes" : "No",
				htmlEscape(rule.getFromAddress()),
				htmlEscape(rule.getToAddress()), 
				htmlEscape(rule.getFromName()),
				htmlEscape(rule.getToName()),
				htmlEscape(rule.getSubject()),
				rule.getAction().toString().toLowerCase(),
				rule.getAction(),
				rule.getStopProcessing() ? "Yes" : "No",
				htmlEscape(rule.getDescription()),
				rule.getId(),
				rule.getId()
			));
		}
		
		return HTML.start(true)
			.append(String.format(template, rulesTable.toString()))
			.footer().get();
	}
	
	private String htmlEscape(String str) {
		if (str == null) return "*";
		return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}