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
		String text = String.format(
				FORMAT_TABLE,
				String.format(FORMAT_TR, "Number of cores",
						"" + runtime.availableProcessors())
						+ String.format(FORMAT_TR, "Number of threads",
								Thread.activeCount())
						+ String.format(FORMAT_TR, "Memory usage",
								"Available: " + max + "MB / Used: "
										+ (total - free) + " MB"));
		ctx.html(HTML.start(true).append(text).footer().get());
	}
}
