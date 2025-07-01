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

import jnode.dto.Link;
import jnode.event.Notifier;
import jnode.event.SharedModuleEvent;
import jnode.ftn.FtnTools;
import jnode.main.MainHandler;
import jnode.orm.ORMManager;

import org.jnode.httpd.dto.PointRequest;
import org.jnode.httpd.util.HTML;
import org.jnode.httpd.util.HTMLi18n;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public class PointRequestConfirmRoute implements Handler {

	@Override
	public void handle(Context ctx) throws Exception {
		HTMLi18n html = HTMLi18n.create(ctx, false);
		String key = ctx.queryParam("key");
		String messageKey = "";
		if (key != null) {
			PointRequest pr = ORMManager.get(PointRequest.class).getById(key);
			if (pr != null) {
				synchronized (PointRequest.class) {
					if (null == ORMManager.get(Link.class).getFirstAnd(
							"ftn_address", "=", pr.getAddr())) {
						Link l = new Link();
						l.setLinkName(pr.getName());
						l.setLinkAddress(pr.getAddr());
						l.setPaketPassword(pr.getPassword());
						l.setProtocolHost("-");
						l.setProtocolPort(0);
						l.setProtocolPassword(pr.getPassword());
						ORMManager.get(Link.class).save(l);
						ORMManager.get(PointRequest.class).delete(pr);
						writeMails(pr);
						messageKey = "point.confirm.check_email";
					} else {
						messageKey = "point.confirm.already_registered";
					}
				}
			} else {
				messageKey = "point.confirm.invalid_key";
			}
		} else {
			messageKey = "point.confirm.invalid_request";
		}
		html.append("<b>").append(html.t("point.confirm.status")).append(": ")
			.append(html.t(messageKey)).append("</b>");
		html.footer();
		ctx.html(html.get());
	}

	private void writeMails(PointRequest pr) {
		String text = String
				.format("Point data:\n > Addr: %s\n > Name: %s\n > Email: %s\n > Password: %s\n",
						pr.getAddr(), pr.getName(), pr.getEmail(),
						pr.getPassword());
		FtnTools.writeNetmail(FtnTools.getPrimaryFtnAddress(),
				FtnTools.getPrimaryFtnAddress(), MainHandler
						.getCurrentInstance().getInfo().getStationName(),
				MainHandler.getCurrentInstance().getInfo().getSysop(),
				"Point confirmed", text);

		Notifier.INSTANSE.notify(new SharedModuleEvent(
				"org.jnode.mail.MailModule", "to", pr.getEmail(), "subject",
				"Point connection info", "text", text));

	}
}
