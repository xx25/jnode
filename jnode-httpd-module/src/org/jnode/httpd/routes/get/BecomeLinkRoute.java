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

public class BecomeLinkRoute implements Handler {
	private static String request = null;
	private boolean enabled;

	public BecomeLinkRoute(boolean enabled) {
		this.enabled = enabled;
		if (enabled) {
			if (request == null) {
				request = HTML.getContents("/parts/requestlink.html");
			}
		}
	}

	@Override
	public void handle(Context ctx) throws Exception {
		if (!enabled) {
			ctx.html(HTML
					.start(false)
					.append("<b>Unfortunately, link requests are disabled by sysop</b>")
					.footer().get());
			return;
		}
		ctx.html(HTML.start(false).append(request).footer().get());
	}

}
