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

package org.jnode.httpd.filters;

import java.util.HashMap;

import jnode.ftn.FtnTools;
import jnode.orm.ORMManager;

import org.jnode.httpd.dto.WebAdmin;
import org.jnode.httpd.util.Base64Util;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public class SecureFilter implements Handler {
	private HashMap<String, Boolean> cache = new HashMap<>();

	public SecureFilter() {
	}

	public SecureFilter(String path, String acceptType) {
	}

	public SecureFilter(String path) {
	}

	@Override
	public void handle(Context ctx) throws Exception {
		boolean authenticated = false;
		String authBase64 = ctx.header("Authorization");
		if (authBase64 != null) {
			Boolean test = cache.get(authBase64);
			if (test != null && test) {
				authenticated = true;
			} else {
				try {
					String authText = new String(Base64Util.decode(authBase64
							.split(" ")[1]));
					String[] creds = authText.split(":");
					WebAdmin admin = ORMManager.get(WebAdmin.class)
							.getFirstAnd("username", "=", creds[0]);
					if (admin != null) {
						String password = FtnTools.md5(creds[1]);
						if (password.equals(admin.getPassword())) {
							authenticated = true;
							cache.put(authBase64, authenticated);
						}
					}
				} catch (RuntimeException e) {
				}
			}
		}
		if (!authenticated) {
			ctx.header("WWW-Authenticate",
					"Basic realm=\"Secure area for operators only\"");
			ctx.status(401);
		}
	}

}
