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

import jnode.dto.Echoarea;
import jnode.ftn.FtnTools;
import jnode.orm.ORMManager;
import io.javalin.http.Context;

import io.javalin.http.Handler;

public class EchoareaRoute implements Handler {

	public EchoareaRoute() {
		
	}

	@Override
	public void handle(Context ctx) throws Exception {
		String id = ctx.formParam("id");
		String name = ctx.formParam("name");
		String descr = ctx.formParam("descr");
		String rl = ctx.formParam("rl");
		String wl = ctx.formParam("wl");
		String gr = ctx.formParam("gr");
		String code = null;
		String delete = ctx.formParam("did");
		if (delete != null) {
			try {
				Long eid = Long.valueOf(delete);
				Echoarea deleteArea = ORMManager.get(Echoarea.class).getById(
						eid);
				if (deleteArea != null) {
					FtnTools.delete(deleteArea);
				}
			} catch (RuntimeException e) {
				code = "ERROR";
			}
		} else {
			try {
				Echoarea ea;
				if (!name.matches("^[-a-zA-Z0-9_\\.\\$]+$")) {
					code = "ENAME";
				} else {
					if (id == null || "0".equals(id)) {
						ea = new Echoarea();
						ea.setName(name.toLowerCase());
					} else {
						Long eid = Long.valueOf(id);
						ea = ORMManager.get(Echoarea.class).getById(eid);
					}
					ea.setDescription(descr);
					ea.setReadlevel(Long.valueOf(rl));
					ea.setWritelevel(Long.valueOf(wl));
					ea.setGroup(gr);
					synchronized (Echoarea.class) {
						if (ea.getId() == null
								&& ORMManager.get(Echoarea.class).getFirstAnd(
										"name", "=", name.toLowerCase()) != null) {
							code = "EXISTS";
						} else {
							ORMManager.get(Echoarea.class).saveOrUpdate(ea);
						}
					}
				}
			} catch (RuntimeException e) {
				code = "INVALID";
			}
		}
		ctx.redirect( "/secure/echoes.html"
				+ ((code != null) ? "?code=" + code : ""));
		
		
	}

}
