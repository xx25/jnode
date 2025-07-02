package org.jnode.httpd.routes.post.secure;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;
import jnode.orm.ORMManager;
import jnode.dto.Schedule;
import jnode.dto.Schedule.Type;
import jnode.dto.Jscript;

public class ScheduleAddRoute implements Handler {
    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String jscriptIdStr = ctx.formParam("jscript_id");
        String typeStr = ctx.formParam("type");
        String detailsStr = ctx.formParam("details");
        
        if (jscriptIdStr == null || typeStr == null || detailsStr == null) {
            ctx.redirect("/secure/schedules.html?error=missing_fields");
            return;
        }
        
        try {
            Schedule schedule = new Schedule();
            Jscript jscript = ORMManager.get(Jscript.class).getById(Long.parseLong(jscriptIdStr));
            schedule.setJscript(jscript);
            schedule.setType(Type.valueOf(typeStr));
            schedule.setDetails(Integer.parseInt(detailsStr));
            
            ORMManager.get(Schedule.class).save(schedule);
            ctx.redirect("/secure/schedules.html");
        } catch (Exception e) {
            ctx.redirect("/secure/schedules.html?error=save_failed");
        }
    }
}