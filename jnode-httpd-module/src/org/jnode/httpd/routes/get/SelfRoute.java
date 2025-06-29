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

import java.util.List;

import jnode.ftn.types.FtnAddress;
import jnode.main.MainHandler;
import jnode.main.SystemInfo;

import org.jnode.httpd.util.HTML;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public class SelfRoute implements Handler {
	private final String FORMAT_TABLE = "<table class=\"info\">%s</table>";
	private final String FORMAT_TR = "<tr><th>%s</th><td>%s</td></tr>";

	@Override
	public void handle(Context ctx) throws Exception {
		String index = HTML.getContents("index.html");
		if (index.length() > 0) {
			ctx.html(index);
			return;
		}
		SystemInfo info = MainHandler.getCurrentInstance().getInfo();

		String text = String.format(
				FORMAT_TABLE,
				String.format(FORMAT_TR, "Node name", info.getStationName())
						+ String.format(FORMAT_TR, "Node location",
								info.getLocation())
						+ String.format(FORMAT_TR, "Sysop", info.getSysop())
						+ String.format(FORMAT_TR, "FTN address(es)",
								getAddrList(info.getAddressList()))
						+ String.format(FORMAT_TR, "Software version",
								MainHandler.getVersion())
						+ String.format(FORMAT_TR, "OS", getOS()));
		ctx.html(HTML.start(false).append(text).footer().get());
	}

	private String getOS() {
		return System.getProperty("os.name") + " "
				+ System.getProperty("os.version") + " ("
				+ System.getProperty("os.arch") + ")";
	}

	private String getAddrList(List<FtnAddress> list) {
		boolean first = true;
		StringBuilder sb = new StringBuilder();
		for (FtnAddress address : list) {
			if (first) {
				first = false;
			} else {
				sb.append(", ");
			}
			sb.append(address.toString());
		}
		return sb.toString();
	}
}
