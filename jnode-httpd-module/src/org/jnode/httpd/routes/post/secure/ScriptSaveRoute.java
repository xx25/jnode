package org.jnode.httpd.routes.post.secure;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;
import jnode.orm.ORMManager;
import jnode.dto.Jscript;

public class ScriptSaveRoute implements Handler {
    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String idStr = ctx.formParam("id");
        String content = ctx.formParam("content");
        
        if (content == null || content.trim().isEmpty()) {
            ctx.redirect("/secure/scripts.html?error=empty_content");
            return;
        }
        
        Jscript script;
        if (idStr != null && !idStr.equals("0")) {
            // Update existing
            script = ORMManager.get(Jscript.class).getById(Long.parseLong(idStr));
            if (script != null) {
                script.setContent(content);
                ORMManager.get(Jscript.class).update(script);
            }
        } else {
            // Create new
            script = new Jscript();
            script.setContent(content);
            ORMManager.get(Jscript.class).save(script);
        }
        
        ctx.redirect("/secure/scripts.html");
    }
}