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

import org.jnode.httpd.util.HTML;
import org.jnode.httpd.util.HTMLi18n;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public class HealthRoute implements Handler {
	private final String FORMAT_TABLE = "<table class=\"info\">%s</table>";
	private final String FORMAT_TR = "<tr><th>%s</th><td>%s</td></tr>";

	@Override
	public void handle(Context ctx) throws Exception {
		Runtime runtime = Runtime.getRuntime();
		int free = Math.round(runtime.freeMemory() / (1024 * 1024));
		int max = Math.round(runtime.maxMemory() / (1024 * 1024));
		int total = Math.round(runtime.totalMemory() / (1024 * 1024));
		HTMLi18n html = HTMLi18n.create(ctx, true);
		
		// System Health section with improved styling
		html.append("<h3>System Health</h3>");
		html.append("<p>Current system status and resource usage information.</p>");
		
		String text = String.format(
				FORMAT_TABLE,
				String.format(FORMAT_TR, html.t("health.number_of_cores"),
						"" + runtime.availableProcessors())
						+ String.format(FORMAT_TR, html.t("health.number_of_threads"),
								Thread.activeCount())
						+ String.format(FORMAT_TR, html.t("health.memory_usage"),
								html.t("health.available") + ": " + max + "MB / " + 
								html.t("health.used") + ": " + (total - free) + " MB"));
		html.append(text);
		
		// Settings Export/Import section with consistent styling
		html.append("<br/><h3>Settings Import/Export</h3>");
		html.append("<p>Backup and restore system configuration settings.</p>");
		
		
		// Export Form
		html.append("<span class=\"info\" id=\"_export_status\"></span>");
		html.append("<form action=\"/secure/settings_export.html\" method=\"post\" id=\"exportForm\">");
		html.append("<table class=\"info\" id=\"exportTable\">");
		html.append("<tr><th colspan=\"2\">Export Configuration</th></tr>");
		html.append("<tr>");
		html.append("<th>Operation:</th>");
		html.append("<td>Download all current settings as a JSON file for backup or transfer to another system.</td>");
		html.append("</tr>");
		html.append("</table>");
		html.append("<input type=\"submit\" value=\"Export Settings\" class=\"button\" />");
		html.append("</form>");
		
		// Import Form
		html.append("<span class=\"info\" id=\"_import_status\"></span>");
		html.append("<form action=\"/secure/settings_import.html\" method=\"post\" enctype=\"multipart/form-data\" id=\"importForm\">");
		html.append("<table class=\"info\" id=\"importTable\">");
		html.append("<tr><th colspan=\"2\">Import Configuration</th></tr>");
		html.append("<tr>");
		html.append("<th>Settings File:</th>");
		html.append("<td><input type=\"file\" name=\"settings_file\" accept=\".json\" required /></td>");
		html.append("</tr>");
		html.append("<tr>");
		html.append("<th>Clear Existing:</th>");
		html.append("<td><input type=\"checkbox\" name=\"clear_existing\" checked /> Remove all current settings before import</td>");
		html.append("</tr>");
		html.append("<tr>");
		html.append("<th>Warning:</th>");
		html.append("<td><strong>Importing settings will replace ALL current configuration. Make sure to export current settings first as backup.</strong></td>");
		html.append("</tr>");
		html.append("</table>");
		html.append("<input type=\"submit\" value=\"Import Settings\" class=\"button\" ");
		html.append("onclick=\"return confirm('Are you sure you want to import settings? This will replace your current configuration!');\" />");
		html.append("</form>");
		
		// Information Table
		html.append("<table class=\"info\">");
		html.append("<tr>");
		html.append("<th>Data Type</th><th>Included</th><th>Excluded</th><th>Description</th>");
		html.append("</tr>");
		html.append("<tr>");
		html.append("<td>Configuration</td><td>Links, Echo Areas, File Areas</td><td>Message content</td><td>Core FTN network configuration</td>");
		html.append("</tr>");
		html.append("<tr>");
		html.append("<td>Rules</td><td>Routing, Rewrite, Netmail Accept</td><td>Waiting queues</td><td>Message processing rules</td>");
		html.append("</tr>");
		html.append("<tr>");
		html.append("<td>Scripts</td><td>JavaScript code, Scheduled tasks</td><td>User accounts</td><td>Automation and scripting</td>");
		html.append("</tr>");
		html.append("<tr>");
		html.append("<td>Subscriptions</td><td>Echo area subscriptions</td><td>Temporary data</td><td>Area subscription settings</td>");
		html.append("</tr>");
		html.append("</table>");
		
		html.append("<style>");
		html.append(".enabled { background-color: #f0f8f0; }");
		html.append(".disabled { background-color: #f8f0f0; opacity: 0.7; }");
		html.append(".action-accept { color: green; font-weight: bold; }");
		html.append(".action-reject { color: red; font-weight: bold; }");
		html.append("code {");
		html.append("    background: #f4f4f4;");
		html.append("    padding: 2px 4px;");
		html.append("    border-radius: 2px;");
		html.append("    font-family: monospace;");
		html.append("}");
		html.append("</style>");
		
		html.footer();
		ctx.html(html.get());
	}
}
