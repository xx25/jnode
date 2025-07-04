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

package org.jnode.httpd.routes.post;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import jnode.dto.NetmailAcceptRule;
import jnode.orm.ORMManager;
import io.javalin.http.Context;

import io.javalin.http.Handler;

/**
 * Netmail acceptance rule form processing
 * 
 * @author jnode
 */
public class NetmailAcceptRuleRoute implements Handler {
	public NetmailAcceptRuleRoute() {
		
	}

	@Override
	public void handle(Context ctx) throws Exception {
		String code = null;
		
		// Handle deletion
		String deleteId = ctx.formParam("did");
		if (deleteId != null) {
			try {
				Long id = Long.valueOf(deleteId);
				NetmailAcceptRule rule = ORMManager.get(NetmailAcceptRule.class).getById(id);
				if (rule != null) {
					ORMManager.get(NetmailAcceptRule.class).delete(rule);
					code = "200"; // Success
				} else {
					code = "404"; // Not found
				}
			} catch (Exception e) {
				code = "500"; // Error
			}
		} else {
			// Handle create/update
			try {
				String idParam = ctx.formParam("id");
				Long id = (idParam != null) ? Long.valueOf(idParam) : 0L;
				NetmailAcceptRule rule;
				
				if (id == 0) {
					rule = new NetmailAcceptRule();
				} else {
					rule = ORMManager.get(NetmailAcceptRule.class).getById(id);
					if (rule == null) {
						rule = new NetmailAcceptRule();
					}
				}
				
				// Set form values
				String priorityParam = ctx.formParam("priority");
				if (priorityParam != null) {
					rule.setPriority(Long.valueOf(priorityParam));
				}
				
				rule.setEnabled("on".equals(ctx.formParam("enabled")));
				rule.setFromAddress(normalizePattern(ctx.formParam("from_addr")));
				rule.setToAddress(normalizePattern(ctx.formParam("to_addr")));
				rule.setFromName(normalizePattern(ctx.formParam("from_name")));
				rule.setToName(normalizePattern(ctx.formParam("to_name")));
				rule.setSubject(normalizePattern(ctx.formParam("subject")));
				
				String actionParam = ctx.formParam("action");
				if (actionParam != null) {
					rule.setAction(NetmailAcceptRule.Action.valueOf(actionParam));
				}
				
				rule.setStopProcessing("on".equals(ctx.formParam("stop_processing")));
				rule.setDescription(ctx.formParam("description"));
				
				// Validate regex patterns
				if (!validateRegexPatterns(rule)) {
					code = "400"; // Invalid regex
				} else {
					ORMManager.get(NetmailAcceptRule.class).save(rule);
					code = "200"; // Success
				}
				
			} catch (Exception e) {
				code = "500"; // Error
			}
		}
		
		ctx.redirect( "/secure/netmail-accept.html" + 
			(code != null ? "?code=" + code : ""));
		
		
	}
	
	private String normalizePattern(String pattern) {
		if (pattern == null || pattern.trim().isEmpty()) {
			
		}
		return pattern.trim();
	}
	
	private boolean validateRegexPatterns(NetmailAcceptRule rule) {
		String[] patterns = {
			rule.getFromAddress(), rule.getToAddress(),
			rule.getFromName(), rule.getToName(), rule.getSubject()
		};
		
		for (String pattern : patterns) {
			if (pattern != null && !pattern.equals("*")) {
				try {
					Pattern.compile(pattern);
				} catch (PatternSyntaxException e) {
					return false;
				}
			}
		}
		return true;
	}
}