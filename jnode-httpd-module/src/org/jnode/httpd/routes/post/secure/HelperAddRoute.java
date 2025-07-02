package org.jnode.httpd.routes.post.secure;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;
import jnode.orm.ORMManager;
import jnode.dto.ScriptHelper;

public class HelperAddRoute implements Handler {
    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String helper = ctx.formParam("helper");
        String classname = ctx.formParam("classname");
        
        if (helper == null || helper.trim().isEmpty() || 
            classname == null || classname.trim().isEmpty()) {
            ctx.redirect("/secure/helpers.html?error=empty_fields");
            return;
        }
        
        ScriptHelper scriptHelper = new ScriptHelper();
        scriptHelper.setId(helper.trim());
        scriptHelper.setClassName(classname.trim());
        
        try {
            ORMManager.get(ScriptHelper.class).save(scriptHelper);
            ctx.redirect("/secure/helpers.html");
        } catch (Exception e) {
            ctx.redirect("/secure/helpers.html?error=save_failed");
        }
    }
}