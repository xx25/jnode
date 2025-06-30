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

package org.jnode.httpd.routes.post;

import jnode.dto.Link;
import jnode.orm.ORMManager;
import io.javalin.http.Context;

import io.javalin.http.Handler;

public class RoutingRoute implements Handler {

	public RoutingRoute() {
		
	}

	@Override
	public void handle(Context ctx) throws Exception {
		String nice = ctx.formParam("nice");
		String fa = ctx.formParam("fa");
		String fn = ctx.formParam("fn");
		String ta = ctx.formParam("ta");
		String tn = ctx.formParam("tn");
		String s = ctx.formParam("s");
		String v = ctx.formParam("v");
		String code = null;
		String delete = ctx.formParam("did");
		if (delete != null) {
			try {
				Long eid = Long.valueOf(delete);
				jnode.dto.Route del = ORMManager.get(jnode.dto.Route.class)
						.getById(eid);
				if (del != null) {
					ORMManager.get(jnode.dto.Route.class).delete(del);
				}
			} catch (RuntimeException e) {
				code = "ERROR";
			}
		} else {
			try {
				jnode.dto.Route route = new jnode.dto.Route();
				route.setNice(Long.valueOf(nice));
				route.setFromAddr(fa);
				route.setFromName(fn);
				route.setToAddr(ta);
				route.setToName(tn);
				route.setSubject(s);
				Link l = ORMManager.get(Link.class).getById(Long.valueOf(v));
				if (l != null) {
					route.setRouteVia(l);
					ORMManager.get(jnode.dto.Route.class).save(route);
				}
			} catch (RuntimeException e) {
				code = "INVALID";
			}
		}
		ctx.redirect( "/secure/route.html"
				+ ((code != null) ? "?code=" + code : ""));
		
		
	}

}
