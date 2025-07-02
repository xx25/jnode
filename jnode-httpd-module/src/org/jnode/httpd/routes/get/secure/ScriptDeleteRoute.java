package org.jnode.httpd.routes.get.secure;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;
import jnode.orm.ORMManager;
import jnode.dto.Jscript;

public class ScriptDeleteRoute implements Handler {
    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String idStr = ctx.queryParam("id");
        
        if (idStr != null) {
            try {
                long id = Long.parseLong(idStr);
                Jscript script = ORMManager.get(Jscript.class).getById(id);
                if (script != null) {
                    ORMManager.get(Jscript.class).delete(script);
                }
            } catch (Exception e) {
                // Log error
            }
        }
        
        ctx.redirect("/secure/scripts.html");
    }
}