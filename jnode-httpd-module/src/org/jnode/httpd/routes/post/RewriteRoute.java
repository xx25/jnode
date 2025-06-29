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

import jnode.dto.Rewrite;
import jnode.orm.ORMManager;
import io.javalin.http.Context;

import io.javalin.http.Handler;

public class RewriteRoute implements Handler {

	public RewriteRoute() {
		
	}

	@Override
	public void handle(Context ctx) throws Exception {
		String n = ctx.queryParam("n");
		String l = ctx.queryParam("l");
		String t = ctx.queryParam("t");
		String ofa = ctx.queryParam("ofa");
		String nfa = ctx.queryParam("nfa");
		String ofn = ctx.queryParam("ofn");
		String nfn = ctx.queryParam("nfn");
		String ota = ctx.queryParam("ota");
		String nta = ctx.queryParam("nta");
		String otn = ctx.queryParam("otn");
		String ntn = ctx.queryParam("ntn");
		String os = ctx.queryParam("os");
		String ns = ctx.queryParam("ns");

		String did = ctx.queryParam("did");

		String code = null;

		if (did != null) {
			try {
				Long id = Long.valueOf(did);
				Rewrite rew = ORMManager.get(Rewrite.class).getById(id);
				if (rew != null) {
					ORMManager.get(Rewrite.class).delete(rew);
				}
			} catch (NumberFormatException e) {
				e.printStackTrace();
				code = "ERROR";
			}
		} else {
			try {
				Long nice = Long.valueOf(n);
				boolean last = Boolean.valueOf(l);
				Rewrite rew = new Rewrite();
				rew.setNice(nice);
				rew.setLast(last);
				
				rew.setType(Rewrite.Type.valueOf(t));
				
				rew.setOrig_from_addr(ofa);
				rew.setNew_from_addr(nfa);

				rew.setOrig_from_name(ofn);
				rew.setNew_from_name(nfn);

				rew.setOrig_to_addr(ota);
				rew.setNew_to_addr(nta);

				rew.setOrig_to_name(otn);
				rew.setNew_to_name(ntn);

				rew.setOrig_subject(os);
				rew.setNew_subject(ns);

				ORMManager.get(Rewrite.class).save(rew);
			} catch (RuntimeException e) {
				e.printStackTrace();
				code = "ERROR";
			}
		}
		ctx.redirect( "/secure/rewrite.html"
				+ ((code != null) ? "?code=" + code : ""));
		
		
	}
}
