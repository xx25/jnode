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

package org.jnode.httpd;

import jnode.event.IEvent;
import jnode.ftn.FtnTools;
import jnode.logger.Logger;
import jnode.module.JnodeModule;
import jnode.module.JnodeModuleException;
import jnode.orm.ORMManager;

import org.jnode.httpd.dto.WebAdmin;
import org.jnode.httpd.filters.*;
import org.jnode.httpd.routes.get.*;
import org.jnode.httpd.routes.post.*;
import org.jnode.httpd.util.HTML;

/**
 * HttpdModule - module listening to port and serving pages
 * 
 * @author kreon
 * 
 */
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

/**
 * 
 * @author kreon
 * 
 */
public class HttpdModule extends JnodeModule {
	private static final String CONFIG_PORT = "port";
	private static final String CONFIG_LINK_REG = "linkreg";
	private static final String CONFIG_POINT_REG = "pointreg";
	private static final String CONFIG_HDG_REG = "hdgpointreg";
	private static final String CONFIG_EXTERNAL = "external";

	public static final Logger logger = Logger.getLogger(HttpdModule.class);
	private short port;
	private boolean linkreg;
	private boolean pointreg;
	private boolean hdgpointreg;
	private String external;
	private Javalin app;

	public HttpdModule(String configFile) throws JnodeModuleException {
		super(configFile);
		port = Short.valueOf(properties.getProperty(CONFIG_PORT, "8080"));
		linkreg = Boolean.valueOf(properties.getProperty(CONFIG_LINK_REG,
				"false"));
		pointreg = Boolean.valueOf(properties.getProperty(CONFIG_POINT_REG,
				"false"));
		external = properties.getProperty(CONFIG_EXTERNAL);
		hdgpointreg = Boolean.valueOf(properties.getProperty(CONFIG_HDG_REG,
				"false"));
		HTML.setExternalPath(external);
	}

	@Override
	public void handle(IEvent event) {

	}

	@Override
	public void start() {

		app = Javalin.create(config -> {
			config.staticFiles.add(staticFiles -> {
				staticFiles.hostedPath = "/";
				staticFiles.directory = "/www";
				staticFiles.location = Location.CLASSPATH;
			});
			if (external != null) {
				config.staticFiles.add(staticFiles -> {
					staticFiles.hostedPath = "/";
					staticFiles.directory = external;
					staticFiles.location = Location.EXTERNAL;
				});
			}
		}).start(port);

		/**** PUBLIC LINKS *****/
		app.get("/", new SelfRoute());
		app.get("/index.html", new SelfRoute());
		app.get("/echoareas.csv", new EchoareaCSVRoute());

		app.post("/hdg-point-request", new HDGPointRequestRoute(hdgpointreg));

		if (pointreg) {
			app.get("/become-point", new BecomePointRoute(true));
			app.get("/requestpoint.html", new BecomePointRoute(true));
			app.get("/point-request-confirm", new PointRequestConfirmRoute());
			app.post("/point-request", new PointRequestRoute());
		} else {
			app.get("/become-point", new BecomePointRoute(false));
			app.get("/requestpoint.html", new BecomePointRoute(false));
		}
		if (linkreg) {
			app.get("/become-link", new BecomeLinkRoute(true));
			app.get("/requestlink.html", new BecomeLinkRoute(true));
			app.post("/link-request", new LinkRequestRoute());
		} else {
			app.get("/become-link", new BecomeLinkRoute(false));
			app.get("/requestlink.html", new BecomeLinkRoute(false));
		}

		app.before("/secure/*", new SecureFilter("/secure/*"));
		app.after(new CharsetFilter());

		/**** SECURE LINKS ****/
		app.get("/secure/index.html", new HealthRoute());
		app.get("/secure/links", new LinksRoute());
		app.get("/secure/links.html", new LinksRoute());
		app.get("/secure/linkoptions", new LinkoptionsRoute());
		app.get("/secure/link-echoes.html", new LinkEchoesRoute());
		app.get("/secure/echo-links.html", new EchoLinksRoute());
		app.get("/secure/echoareas", new EchoareasRoute());
		app.get("/secure/echoes.html", new EchoareasRoute());
		app.get("/secure/fileareas", new FileareasRoute());
		app.get("/secure/fechoes.html", new FileareasRoute());
		app.get("/secure/routings", new RoutingsRoute());
		app.get("/secure/route.html", new RoutingsRoute());
		app.get("/secure/rewrites", new RewritesRoute());
		app.get("/secure/rewrite.html", new RewritesRoute());
		app.get("/secure/netmail-accept-rules", new NetmailAcceptRulesRoute());
		app.get("/secure/netmail-accept.html", new NetmailAcceptRulesRoute());
		app.get("/secure/users", new UsersRoute());
		app.get("/secure/users.html", new UsersRoute());

		app.post("/secure/link", new LinkRoute());
		app.post("/secure/linkoption", new LinkoptionRoute());
		app.post("/secure/link-echoes-save", new LinkEchoesSaveRoute());
		app.post("/secure/echo-links-save", new EchoLinksSaveRoute());
		app.post("/secure/echoarea", new EchoareaRoute());
		app.post("/secure/filearea", new FileareaRoute());
		app.post("/secure/routing", new RoutingRoute());
		app.post("/secure/rewrite", new RewriteRoute());
		app.post("/secure/netmail-accept-rule", new NetmailAcceptRuleRoute());
		app.post("/secure/user", new UserRoute());

		try {
			WebAdmin admin = ORMManager.get(WebAdmin.class).getFirstAnd();
			if (admin == null) {
				admin = new WebAdmin();
				admin.setUsername("admin");
				String password = FtnTools.generate8d();
				admin.setPassword(FtnTools.md5(password));
				ORMManager.get(WebAdmin.class).save(admin);
				String text = "You can login to jNode site with those credentials:\n  > login: admin\n > password "
						+ password + "\n";
				// write netmail to primary address
				FtnTools.writeNetmail(FtnTools.getPrimaryFtnAddress(),
						FtnTools.getPrimaryFtnAddress(), "HTTPD Module",
						"Sysop of Node", "Web password", text);
				logger.l1("Admin created\n" + text);
			}
		} catch (RuntimeException e) {
		}
	}

	public void stop() {
		if (app != null) {
			app.stop();
		}
	}
}
