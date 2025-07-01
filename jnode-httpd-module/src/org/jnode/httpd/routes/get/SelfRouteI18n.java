package org.jnode.httpd.routes.get;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jnode.httpd.util.HTMLi18n;

/**
 * Example of how to add language selector to a page
 */
public class SelfRouteI18n implements Handler {
    
    @Override
    public void handle(Context ctx) throws Exception {
        HTMLi18n html = HTMLi18n.create(ctx, false);
        
        html.append("<div class=\"content\">\n");
        
        // Add language selector at the top
        html.addLanguageSelector();
        
        html.append("<h1>").append(html.t("about.title")).append("</h1>\n");
        html.append("<p>").append(html.t("about.software")).append(": jNode</p>\n");
        html.append("<p>").append(html.t("about.version")).append(": 2.0</p>\n");
        html.append("</div>\n");
        
        // Include i18n JavaScript
        html.append("<script src=\"/js/i18n.js\"></script>\n");
        
        html.footer();
        
        ctx.html(html.get());
    }
}