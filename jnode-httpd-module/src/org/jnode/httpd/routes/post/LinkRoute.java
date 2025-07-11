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

import jnode.dto.Link;
import jnode.ftn.FtnTools;
import jnode.ftn.types.FtnAddress;
import jnode.orm.ORMManager;
import io.javalin.http.Context;
import io.javalin.http.Handler;

public class LinkRoute implements Handler {

	@Override
	public void handle(Context ctx) throws Exception {
		String _id = ctx.formParam("id");
		String name = ctx.formParam("name");
		String _ftn = ctx.formParam("addr");
		String pass = ctx.formParam("password");
		String pktpass = ctx.formParam("pktpassword");
		String address = ctx.formParam("address");
		String code = null;
		String delete = ctx.formParam("did");
		if (delete != null) {
			try {
				Long eid = Long.valueOf(delete);
				Link deleteLink = ORMManager.get(Link.class).getById(eid);
				if (deleteLink != null) {
					FtnTools.delete(deleteLink);
				}
			} catch (RuntimeException e) {
				code = "ERROR";
			}
		} else {
			try {
				FtnAddress ftn = new FtnAddress(_ftn);
				Link l = null;
				if (!_id.equals("0")) {
					Long id = Long.valueOf(_id);
					l = ORMManager.get(Link.class).getById(id);
				}
				if (l == null) {
					l = new Link();
				}
				l.setLinkAddress(ftn.toString());
				l.setLinkName(name);
				l.setPaketPassword(pktpass);
				l.setProtocolPassword(pass);
				l.setProtocolAddress(address);
				ORMManager.get(Link.class).saveOrUpdate(l);
			} catch (RuntimeException e) {
				code = "ERROR";
			}
		}
		ctx.redirect("/secure/links.html"
				+ ((code != null) ? "?error=" + code : ""));
	}
}
