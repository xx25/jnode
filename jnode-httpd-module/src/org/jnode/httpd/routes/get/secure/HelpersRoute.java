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
        
        // Help description
        html.append("<p>").append(html.t("helpers.description")).append("</p>");
        
        // Add new helper form
        html.append("<div style='border: 1px solid #999; padding: 10px; margin-bottom: 20px;'>");
        html.append("<h3>").append(html.t("helpers.add_new")).append("</h3>");
        html.append("<form method='post' action='/secure/helper_add.html'>");
        html.append("<table class='info'>");
        html.append("<tr>");
        html.append("<th class='center' colspan='2'>").append(html.t("helpers.add_new")).append("</th>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<th>").append(html.t("helpers.name")).append("</th>");
        html.append("<td><input type='text' name='helper' required></td>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<th>").append(html.t("helpers.classname")).append("</th>");
        html.append("<td><input type='text' name='classname' size='50' required></td>");
        html.append("</tr>");
        html.append("</table>");
        html.append("<input type='submit' value='").append(html.t("helpers.add")).append("' class='button'>");
        html.append("</form>");
        html.append("</div>");
        
        // Helpers table
        html.append("<table class='links'>");
        html.append("<tr>");
        html.append("<th colspan='3' class='center'>").append(html.t("helpers.title")).append("</th>");
        html.append("</tr>");
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
            html.append("' class='css-link-1' onclick='return confirm(\"").append(html.t("helpers.confirm_delete"));
            html.append("\");'>").append(html.t("helpers.delete")).append("</a>");
            html.append("</td>");
            html.append("</tr>");
        }
        
        html.append("</table>");
        html.footer();
        ctx.html(html.get());
    }
}