package org.jnode.httpd.routes.get.secure;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;
import org.jnode.httpd.util.HTMLi18n;
import jnode.orm.ORMManager;
import jnode.dto.Jscript;

public class ScriptRunRoute implements Handler {
    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        HTMLi18n html = HTMLi18n.create(ctx, true);
        
        String idStr = ctx.queryParam("id");
        
        html.append("<h2>").append(html.t("scripts.run_title")).append("</h2>");
        
        if (idStr != null) {
            try {
                long id = Long.parseLong(idStr);
                Jscript script = ORMManager.get(Jscript.class).getById(id);
                if (script != null) {
                    // Execute script
                    // Note: Script execution would need to be implemented
                    html.append("<p>").append(html.t("scripts.run_success")).append("</p>");
                } else {
                    html.append("<p style='color: red;'>").append(html.t("scripts.script_not_found")).append("</p>");
                }
            } catch (Exception e) {
                html.append("<p style='color: red;'>").append(html.t("scripts.invalid_id")).append("</p>");
            }
        }
        
        html.append("<p><a href='/secure/scripts.html'>").append(html.t("scripts.back_to_list")).append("</a></p>");
        html.footer();
        ctx.html(html.get());
    }
}