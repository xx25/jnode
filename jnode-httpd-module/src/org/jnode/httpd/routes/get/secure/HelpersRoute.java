package org.jnode.httpd.routes.get.secure;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;
import org.jnode.httpd.util.HTMLi18n;
import jnode.orm.ORMManager;
import jnode.dto.ScriptHelper;

import java.util.List;

public class HelpersRoute implements Handler {
    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        HTMLi18n html = HTMLi18n.create(ctx, true);
        
        html.append("<h2>").append(html.t("helpers.title")).append("</h2>");
        
        // Add new helper form
        html.append("<div style='border: 1px solid #999; padding: 10px; margin-bottom: 20px;'>");
        html.append("<h3>").append(html.t("helpers.add_new")).append("</h3>");
        html.append("<form method='post' action='/secure/helper_add.html'>");
        html.append("<table>");
        html.append("<tr>");
        html.append("<td>").append(html.t("helpers.name")).append(":</td>");
        html.append("<td><input type='text' name='helper' required>");
        html.append("<br><small>").append(html.t("scripts.helper_name_desc")).append("</small></td>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<td>").append(html.t("helpers.classname")).append(":</td>");
        html.append("<td><input type='text' name='classname' size='50' required>");
        html.append("<br><small>").append(html.t("scripts.helper_class_desc")).append("</small></td>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<td colspan='2'>");
        html.append("<p><strong>").append(html.t("scripts.helper_usage_note")).append("</strong></p>");
        html.append("<input type='submit' value='").append(html.t("helpers.add")).append("'>");
        html.append("</td>");
        html.append("</tr>");
        html.append("</table>");
        html.append("</form>");
        html.append("</div>");
        
        // Helpers table
        html.append("<table class='info'>");
        html.append("<tr>");
        html.append("<th>").append(html.t("helpers.name")).append("</th>");
        html.append("<th>").append(html.t("helpers.classname")).append("</th>");
        html.append("<th>").append(html.t("helpers.actions")).append("</th>");
        html.append("</tr>");
        
        List<ScriptHelper> helpers = ORMManager.get(ScriptHelper.class).getAll();
        for (ScriptHelper helper : helpers) {
            html.append("<tr>");
            html.append("<td>").append(helper.getId()).append("</td>");
            html.append("<td>").append(helper.getClassName()).append("</td>");
            html.append("<td>");
            html.append("<a href='/secure/helper_delete.html?id=").append(helper.getId());
            html.append("' onclick='return confirm(\"").append(html.t("helpers.confirm_delete"));
            html.append("\");'>").append(html.t("helpers.delete")).append("</a>");
            html.append("</td>");
            html.append("</tr>");
        }
        
        html.append("</table>");
        html.footer();
        ctx.html(html.get());
    }
}