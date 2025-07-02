package org.jnode.httpd.routes.get.secure;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;
import jnode.orm.ORMManager;
import jnode.dto.Schedule;

public class ScheduleDeleteRoute implements Handler {
    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String idStr = ctx.queryParam("id");
        
        if (idStr != null) {
            try {
                long id = Long.parseLong(idStr);
                Schedule schedule = ORMManager.get(Schedule.class).getById(id);
                if (schedule != null) {
                    ORMManager.get(Schedule.class).delete(schedule);
                }
            } catch (Exception e) {
                // Log error
            }
        }
        
        ctx.redirect("/secure/schedules.html");
    }
}