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

import org.jnode.httpd.dto.PointRequest;

import jnode.event.Notifier;
import jnode.event.SharedModuleEvent;
import jnode.ftn.FtnTools;
import jnode.ftn.types.FtnAddress;
import jnode.main.MainHandler;
import jnode.orm.ORMManager;
import io.javalin.http.Context;

import io.javalin.http.Handler;

public class PointRequestRoute implements Handler {
	public PointRequestRoute() {
		
	}

	@Override
	public void handle(Context ctx) throws Exception {
		String code = null;
		String node = ctx.formParam("node");
		String point = ctx.formParam("point");
		String fname = ctx.formParam("fname");
		String lname = ctx.formParam("lname");
		String email = ctx.formParam("email");

		PointRequest pr = new PointRequest();
		// check node
		try {
			FtnAddress a = new FtnAddress(node);
			if (!MainHandler.getCurrentInstance().getInfo().getAddressList()
					.contains(a)) {
				code = "NOTNODE";
			}
		} catch (NumberFormatException e) {
			code = "NOTNODE";
		}
		if (code == null) {
			try {
				FtnAddress a = new FtnAddress(node + "." + point);
				if (null != FtnTools.getLinkByFtnAddress(a)) {
					code = "EXISTS";
				}
				pr.setAddr(a.toString());
			} catch (NumberFormatException e) {
				code = "NOTPOINT";
			}
		}
		if (code == null) {
			if (fname != null && lname != null && fname.length() > 3
					&& lname.length() >= 3) {
				pr.setName(fname + " " + lname);
			} else {
				code = "NOTNAME";
			}
		}
		if (code == null) {
			if (email != null
					&& email.matches("^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$")) {
				pr.setEmail(email);
			} else {
				code = "NOTEMAIL";
			}
		}
		if (code == null) {
			String password = FtnTools.generate8d();
			pr.setPassword(password);
			synchronized (PointRequest.class) {
				if (null != ORMManager.get(PointRequest.class).getFirstAnd(
						"addr", "=", pr.getAddr())) {
					code = "EXISTS";
				} else {
					ORMManager.get(PointRequest.class).save(pr);
					writeRequestNetmail(pr);
					writeConfirmEmail(ctx, pr);
				}
			}
		}
		ctx.redirect( "/requestpointresult.html"
				+ ((code != null) ? "?code=" + code : ""));
		
		
	}

	private void writeConfirmEmail(Context ctx, PointRequest pr) {

		String text = "Somebody reuested a point AKA from your email address.\n"
				+ "If that was you, click the link below to complete your registration\n"
				+ "In other cases ignore this letter\n"
				+"Point's Name: "+pr.getName()+"\n"
				+"Point's AKA: "+pr.getAddr()+"\n"
				+ " Link: "
				+ ctx.url()
				+ "?key="
				+ pr.getId()
				+ "  \n"
				+ "\n--\n"
				+ MainHandler.getVersion();
		Notifier.INSTANCE.notify(new SharedModuleEvent(
				"org.jnode.mail.MailModule", "to", pr.getEmail(), "subject",
				"Point request confirmation", "text", text));

	}

	private void writeRequestNetmail(PointRequest pr) {
		String text = String
				.format("New point request:\n > Addr: %s\n > Name: %s\n > Email: %s\n > Password: %s\n",
						pr.getAddr(), pr.getName(), pr.getEmail(),
						pr.getPassword());
		FtnTools.writeNetmail(FtnTools.getPrimaryFtnAddress(),
				FtnTools.getPrimaryFtnAddress(), MainHandler
						.getCurrentInstance().getInfo().getStationName(),
				MainHandler.getCurrentInstance().getInfo().getSysop(),
				"New point request", text);

	}
}
