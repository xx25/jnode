package org.jnode.httpd.routes.get.secure;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;
import org.jnode.httpd.util.HTMLi18n;
import jnode.orm.ORMManager;
import jnode.dto.Schedule;
import jnode.dto.Jscript;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SchedulesRoute implements Handler {
    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        HTMLi18n html = HTMLi18n.create(ctx, true);
        
        html.append("<h2>").append(html.t("schedules.title")).append("</h2>");
        
        // Help description
        html.append("<p>").append("Schedule JavaScript scripts for automated execution at specified intervals.").append("</p>");
        
        // Help table
        html.append("<table class=\"info\">");
        html.append("<tr>");
        html.append("<th>Schedule Type</th><th>Details Value</th><th>Execution Time</th><th>Description</th>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<td>HOURLY</td><td>0-59</td><td>Minute of hour</td><td>Run every hour at specified minute</td>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<td>DAILY</td><td>0-23</td><td>Hour of day</td><td>Run every day at specified hour</td>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<td>WEEKLY</td><td>1-7</td><td>Day of week</td><td>Run every week on specified day (1=Monday)</td>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<td>MONTHLY</td><td>1-31</td><td>Day of month</td><td>Run every month on specified day</td>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<td>ANNUALLY</td><td>1-365</td><td>Day of year</td><td>Run every year on specified day</td>");
        html.append("</tr>");
        html.append("</table>");
        
        // Get all scripts for dropdown
        List<Jscript> scripts = ORMManager.get(Jscript.class).getAll();
        Map<Long, String> scriptMap = scripts.stream()
            .collect(Collectors.toMap(Jscript::getId, s -> 
                s.getContent().length() > 50 ? 
                s.getContent().substring(0, 50) + "..." : 
                s.getContent()));
        
        // Add new schedule form
        html.append("<div style='border: 1px solid #999; padding: 10px; margin-bottom: 20px;'>");
        html.append("<h3>").append(html.t("schedules.add_new")).append("</h3>");
        html.append("<form method='post' action='/secure/schedule_add.html'>");
        html.append("<table class='info'>");
        html.append("<tr>");
        html.append("<th class='center' colspan='2'>").append(html.t("schedules.add_new")).append("</th>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<th>").append(html.t("schedules.script")).append("</th>");
        html.append("<td><select name='jscript_id' required>");
        for (Jscript script : scripts) {
            html.append("<option value='").append(String.valueOf(script.getId())).append("'>");
            html.append("ID: ").append(String.valueOf(script.getId())).append(" - ");
            html.append(scriptMap.get(script.getId())).append("</option>");
        }
        html.append("</select></td>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<th>").append(html.t("schedules.type")).append("</th>");
        html.append("<td><select name='type' required onchange='updateDetailsField(this.value)'>");
        html.append("<option value='HOURLY'>").append(html.t("schedules.hourly")).append("</option>");
        html.append("<option value='DAILY'>").append(html.t("schedules.daily")).append("</option>");
        html.append("<option value='WEEKLY'>").append(html.t("schedules.weekly")).append("</option>");
        html.append("<option value='MONTHLY'>").append(html.t("schedules.monthly")).append("</option>");
        html.append("<option value='ANNUALLY'>").append(html.t("schedules.annually")).append("</option>");
        html.append("</select></td>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<th>").append(html.t("schedules.details")).append("</th>");
        html.append("<td><input type='number' name='details' id='details' min='0' required>");
        html.append(" <span id='details_help'>").append(html.t("schedules.details_hourly")).append("</span></td>");
        html.append("</tr>");
        html.append("</table>");
        html.append("<input type='submit' value='");
        html.append(html.t("schedules.add")).append("' class='button'>");
        html.append("</form>");
        html.append("</div>");
        
        // JavaScript for dynamic help text
        html.append("<script>");
        html.append("function updateDetailsField(type) {");
        html.append("  var help = document.getElementById('details_help');");
        html.append("  var input = document.getElementById('details');");
        html.append("  switch(type) {");
        html.append("    case 'HOURLY': help.innerHTML = '").append(html.t("schedules.details_hourly")).append("'; input.max = 59; break;");
        html.append("    case 'DAILY': help.innerHTML = '").append(html.t("schedules.details_daily")).append("'; input.max = 23; break;");
        html.append("    case 'WEEKLY': help.innerHTML = '").append(html.t("schedules.details_weekly")).append("'; input.min = 1; input.max = 7; break;");
        html.append("    case 'MONTHLY': help.innerHTML = '").append(html.t("schedules.details_monthly")).append("'; input.min = 1; input.max = 31; break;");
        html.append("    case 'ANNUALLY': help.innerHTML = '").append(html.t("schedules.details_annually")).append("'; input.min = 1; input.max = 365; break;");
        html.append("  }");
        html.append("}");
        html.append("</script>");
        
        // Schedules table
        html.append("<table class='links'>");
        html.append("<tr>");
        html.append("<th colspan='5' class='center'>").append(html.t("schedules.title")).append("</th>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<th>").append(html.t("schedules.id")).append("</th>");
        html.append("<th>").append(html.t("schedules.script")).append("</th>");
        html.append("<th>").append(html.t("schedules.type")).append("</th>");
        html.append("<th>").append(html.t("schedules.details")).append("</th>");
        html.append("<th>").append(html.t("schedules.actions")).append("</th>");
        html.append("</tr>");
        
        List<Schedule> schedules = ORMManager.get(Schedule.class).getAll();
        for (Schedule schedule : schedules) {
            html.append("<tr>");
            html.append("<td>").append(String.valueOf(schedule.getId())).append("</td>");
            html.append("<td>");
            Long scriptId = schedule.getJscript() != null ? schedule.getJscript().getId() : null;
            if (scriptId != null && scriptMap.containsKey(scriptId)) {
                html.append("ID: ").append(String.valueOf(scriptId)).append(" - ");
                html.append(scriptMap.get(scriptId));
            } else {
                html.append(html.t("schedules.script_not_found"));
            }
            html.append("</td>");
            html.append("<td>").append(html.t("schedules." + schedule.getType().name().toLowerCase())).append("</td>");
            html.append("<td>").append(String.valueOf(schedule.getDetails())).append("</td>");
            html.append("<td>");
            html.append("<a href='/secure/schedule_delete.html?id=").append(String.valueOf(schedule.getId()));
            html.append("' class='css-link-1' onclick='return confirm(\"").append(html.t("schedules.confirm_delete"));
            html.append("\");'>").append(html.t("schedules.delete")).append("</a>");
            html.append("</td>");
            html.append("</tr>");
        }
        
        html.append("</table>");
        html.footer();
        ctx.html(html.get());
    }
}