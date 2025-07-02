package org.jnode.httpd.routes.get.secure;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;
import org.jnode.httpd.util.HTMLi18n;
import jnode.orm.ORMManager;
import jnode.dto.Jscript;

import java.util.List;

public class ScriptsRoute implements Handler {
    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        HTMLi18n html = HTMLi18n.create(ctx, true);
        
        html.append("<h2>").append(html.t("scripts.title")).append("</h2>");
        
        // Add new script button
        html.append("<div style='margin-bottom: 10px;'>");
        html.append("<button onclick=\"location.href='/secure/script_edit.html?id=0'\">");
        html.append(html.t("scripts.add_new")).append("</button>");
        html.append("</div>");
        
        // Scripts table
        html.append("<table class='info'>");
        html.append("<tr>");
        html.append("<th>").append(html.t("scripts.id")).append("</th>");
        html.append("<th>").append(html.t("scripts.content")).append("</th>");
        html.append("<th>").append(html.t("scripts.actions")).append("</th>");
        html.append("</tr>");
        
        List<Jscript> scripts = ORMManager.get(Jscript.class).getAll();
        for (Jscript script : scripts) {
            html.append("<tr>");
            html.append("<td>").append(String.valueOf(script.getId())).append("</td>");
            html.append("<td><pre style='max-width: 600px; overflow: auto;'>");
            html.append(script.getContent()).append("</pre></td>");
            html.append("<td>");
            html.append("<a href='/secure/script_edit.html?id=").append(String.valueOf(script.getId()));
            html.append("'>").append(html.t("scripts.edit")).append("</a> | ");
            html.append("<a href='/secure/script_delete.html?id=").append(String.valueOf(script.getId()));
            html.append("' onclick='return confirm(\"").append(html.t("scripts.confirm_delete"));
            html.append("\");'>").append(html.t("scripts.delete")).append("</a> | ");
            html.append("<a href='/secure/script_run.html?id=").append(String.valueOf(script.getId()));
            html.append("'>").append(html.t("scripts.run")).append("</a>");
            html.append("</td>");
            html.append("</tr>");
        }
        
        html.append("</table>");
        html.footer();
        ctx.html(html.get());
    }
}