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

package org.jnode.httpd.filters;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public class CharsetFilter implements Handler {
	public CharsetFilter() {
	}

	@Override
	public void handle(Context ctx) throws Exception {
		// Set UTF-8 for all text responses
		String contentType = ctx.res().getContentType();
		if (contentType == null || contentType.startsWith("text/")) {
			ctx.contentType("text/html; charset=utf-8");
		}
		
		if (ctx.path().endsWith(".html")) {
			ctx.header("Cache-Control",
					"no-cache, no-store, must-revalidate");
			ctx.header("Pragma", "no-cache");
			ctx.header("Expires", "0");
		}
	}

}
