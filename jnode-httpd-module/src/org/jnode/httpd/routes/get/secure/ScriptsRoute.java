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
        
        // Help description
        html.append("<p>").append("Manage JavaScript automation scripts for system automation and reporting.").append("</p>");
        
        // Help table
        html.append("<table class=\"info\">");
        html.append("<tr>");
        html.append("<th>Helper Class</th><th>Function</th><th>Usage</th><th>Description</th>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<td>reporthelper</td><td>report()</td><td>Database reports</td><td>Generate database reports to echoareas</td>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<td>shellhelper</td><td>execCommand()</td><td>Execute shell commands</td><td>Run system commands and capture output</td>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<td>writefilehelper</td><td>writeFileToEchoarea()</td><td>File operations</td><td>Write files and content to echoareas</td>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<td>writestathelper</td><td>writeStatToEchoarea()</td><td>Statistics</td><td>Generate and post statistics reports</td>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<td>corehelper</td><td>getClassByName()</td><td>Reflection</td><td>Access Java classes and methods</td>");
        html.append("</tr>");
        html.append("</table>");
        
        // Add new script button
        html.append("<div style='margin-bottom: 10px;'>");
        html.append("<a href='/secure/script_edit.html?id=0' class='css-link-1'>");
        html.append(html.t("scripts.add_new")).append("</a>");
        html.append("</div>");
        
        // Scripts table
        html.append("<table class='links'>");
        html.append("<tr>");
        html.append("<th colspan='3' class='center'>").append(html.t("scripts.title")).append("</th>");
        html.append("</tr>");
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
            html.append("' class='css-link-1'>").append(html.t("scripts.edit")).append("</a>&nbsp;");
            html.append("<a href='/secure/script_delete.html?id=").append(String.valueOf(script.getId()));
            html.append("' class='css-link-1' onclick='return confirm(\"").append(html.t("scripts.confirm_delete"));
            html.append("\");'>").append(html.t("scripts.delete")).append("</a>&nbsp;");
            html.append("<a href='/secure/script_run.html?id=").append(String.valueOf(script.getId()));
            html.append("' class='css-link-1'>").append(html.t("scripts.run")).append("</a>");
            html.append("</td>");
            html.append("</tr>");
        }
        
        html.append("</table>");
        html.footer();
        ctx.html(html.get());
    }
}