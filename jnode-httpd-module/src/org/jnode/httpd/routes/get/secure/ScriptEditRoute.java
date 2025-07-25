package org.jnode.httpd.routes.get.secure;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;
import org.jnode.httpd.util.HTMLi18n;
import jnode.orm.ORMManager;
import jnode.dto.Jscript;
import jnode.dto.ScriptHelper;

import java.util.List;

public class ScriptEditRoute implements Handler {
    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        HTMLi18n html = HTMLi18n.create(ctx, true);
        
        String idParam = ctx.queryParam("id");
        long id = idParam != null ? Long.parseLong(idParam) : 0;
        Jscript script = null;
        String title;
        
        if (id > 0) {
            script = ORMManager.get(Jscript.class).getById(id);
            title = html.t("scripts.edit_title");
        } else {
            title = html.t("scripts.new_title");
        }
        
        html.append("<h2>").append(title).append("</h2>");
        
        // Show available helpers
        List<ScriptHelper> helpers = ORMManager.get(ScriptHelper.class).getAll();
        if (!helpers.isEmpty()) {
            html.append("<div style='border: 1px solid #999; padding: 10px; margin-bottom: 20px;'>");
            html.append("<h3>").append(html.t("scripts.available_helpers")).append("</h3>");
            html.append("<ul>");
            for (ScriptHelper helper : helpers) {
                html.append("<li><b>").append(helper.getId()).append("</b> - ");
                html.append(helper.getClassName()).append("</li>");
            }
            html.append("</ul>");
            html.append("</div>");
        }
        
        // Script form
        html.append("<form method='post' action='/secure/script_save.html'>");
        if (id > 0) {
            html.append("<input type='hidden' name='id' value='").append(String.valueOf(id)).append("'>");
        }
        html.append("<table>");
        html.append("<tr>");
        html.append("<td valign='top'>").append(html.t("scripts.content")).append(":</td>");
        html.append("<td><textarea name='content' rows='20' cols='80' required>");
        if (script != null) {
            html.append(script.getContent());
        }
        html.append("</textarea></td>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<td colspan='2'>");
        html.append("<input type='submit' value='").append(html.t("scripts.save")).append("'> ");
        html.append("<input type='button' value='").append(html.t("scripts.cancel"));
        html.append("' onclick='location.href=\"/secure/scripts.html\"'>");
        html.append("</td>");
        html.append("</tr>");
        html.append("</table>");
        html.append("</form>");
        
        // Example scripts
        html.append("<div style='margin-top: 20px; border: 1px solid #999; padding: 10px;'>");
        html.append("<h3>").append(html.t("scripts.examples")).append("</h3>");
        
        // Report helper example
        html.append("<h4>ReportHelper - ").append(html.t("scripts.example_report_single_col")).append("</h4>");
        html.append("<pre style='white-space: pre-wrap; word-wrap: break-word; max-width: 100%; overflow-x: auto; background-color: #f5f5f5; padding: 10px;'>");
        html.append("reporthelper.report(\n");
        html.append("    \"828.robots\",          // ").append(html.t("scripts.param_echoarea")).append("\n");
        html.append("    \"").append(html.t("scripts.example_report_subject")).append("\",  // ").append(html.t("scripts.param_subject")).append("\n");
        html.append("    \"SELECT e.name FROM echoareas e WHERE (SELECT COUNT(*) FROM subscriptions WHERE echoarea_id=e.id)=1 ORDER BY 1\",  // ").append(html.t("scripts.param_sql")).append("\n");
        html.append("    \"ECHOAREA\",            // ").append(html.t("scripts.param_headers")).append("\n");
        html.append("    \"50\",                  // ").append(html.t("scripts.param_col_width")).append("\n");
        html.append("    \"S\"                   // ").append(html.t("scripts.param_format")).append("\n");
        html.append(");");
        html.append("</pre>");
        
        // Multi-column example
        html.append("<h4>ReportHelper - ").append(html.t("scripts.example_report_multi_col")).append("</h4>");
        html.append("<pre style='white-space: pre-wrap; word-wrap: break-word; max-width: 100%; overflow-x: auto; background-color: #f5f5f5; padding: 10px;'>");
        html.append("reporthelper.report(\n");
        html.append("    \"828.robots\",\n");
        html.append("    \"").append(html.t("scripts.example_report_multi_subject")).append("\",\n");
        html.append("    \"SELECT e.name, e.description, COUNT(s.id) FROM echoareas e LEFT JOIN subscriptions s ON e.id=s.echoarea_id GROUP BY e.id\",\n");
        html.append("    \"ECHOAREA,DESCRIPTION,SUBSCRIBERS\",  // ").append(html.t("scripts.param_headers")).append("\n");
        html.append("    \"30,40,15\",            // ").append(html.t("scripts.param_col_width")).append("\n");
        html.append("    \"S,S,S\"               // ").append(html.t("scripts.param_format")).append("\n");
        html.append(");");
        html.append("</pre>");
        
        // Shell helper example
        html.append("<h4>ShellHelper</h4>");
        html.append("<pre style='white-space: pre-wrap; word-wrap: break-word; max-width: 100%; overflow-x: auto; background-color: #f5f5f5; padding: 10px;'>");
        html.append("shellhelper.execCommand(\n");
        html.append("    \"828.robots\",          // ").append(html.t("scripts.param_echoarea")).append("\n");
        html.append("    \"").append(html.t("scripts.example_shell_subject")).append("\",     // ").append(html.t("scripts.param_subject")).append("\n");
        html.append("    \"df\",                  // ").append(html.t("scripts.param_command")).append("\n");
        html.append("    \"-h\",                 // ").append(html.t("scripts.param_args")).append("\n");
        html.append("    \"/opt/jnode\"          // ").append(html.t("scripts.param_workdir")).append("\n");
        html.append(");");
        html.append("</pre>");
        
        html.append("</div>");
        
        html.footer();
        ctx.html(html.get());
    }
}