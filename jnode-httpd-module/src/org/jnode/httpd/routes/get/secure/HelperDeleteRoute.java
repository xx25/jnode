package org.jnode.httpd.routes.get.secure;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;
import jnode.orm.ORMManager;
import jnode.dto.ScriptHelper;

public class HelperDeleteRoute implements Handler {
    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String idStr = ctx.queryParam("id");
        
        if (idStr != null) {
            try {
                ScriptHelper helper = ORMManager.get(ScriptHelper.class).getAnd("helper", "=", idStr).get(0);
                if (helper != null) {
                    ORMManager.get(ScriptHelper.class).delete(helper);
                }
            } catch (Exception e) {
                // Log error
            }
        }
        
        ctx.redirect("/secure/helpers.html");
    }
}