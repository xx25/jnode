package org.jnode.httpd.routes.get.secure;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;
import org.jnode.httpd.util.HTMLi18n;

/**
 * Settings import/export page
 */
public class SettingsRoute implements Handler {
    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        HTMLi18n html = HTMLi18n.create(ctx, true);
        
        html.append("<h2>Settings Import/Export</h2>");
        
        // Description
        html.append("<p>Export and import jNode configuration settings. This includes all configuration tables ");
        html.append("(links, areas, routing, scripts, etc.) but excludes message content and temporary data.</p>");
        
        // Export section
        html.append("<div style='margin-bottom: 20px; padding: 10px; border: 1px solid #ccc; background-color: #f9f9f9;'>");
        html.append("<h3>Export Settings</h3>");
        html.append("<p>Download all current settings as a JSON file for backup or transfer to another system.</p>");
        html.append("<form method='post' action='/secure/settings_export.html' style='margin-top: 10px;'>");
        html.append("<input type='submit' value='Export Settings' class='button' />");
        html.append("</form>");
        html.append("</div>");
        
        // Import section
        html.append("<div style='margin-bottom: 20px; padding: 10px; border: 1px solid #ccc; background-color: #f9f9f9;'>");
        html.append("<h3>Import Settings</h3>");
        html.append("<p><strong>WARNING:</strong> Importing settings will replace ALL current configuration. ");
        html.append("Make sure to export current settings first as backup.</p>");
        
        html.append("<form method='post' action='/secure/settings_import.html' enctype='multipart/form-data' style='margin-top: 10px;'>");
        html.append("<table class='links' style='width: 100%;'>");
        html.append("<tr><td><label for='settings_file'>Settings File (JSON):</label></td>");
        html.append("<td><input type='file' id='settings_file' name='settings_file' accept='.json' required /></td></tr>");
        
        html.append("<tr><td><label for='clear_existing'>Clear Existing Settings:</label></td>");
        html.append("<td><input type='checkbox' id='clear_existing' name='clear_existing' checked /> ");
        html.append("(Recommended - remove all current settings before import)</td></tr>");
        
        html.append("<tr><td colspan='2' style='text-align: center; padding-top: 10px;'>");
        html.append("<input type='submit' value='Import Settings' class='button' ");
        html.append("onclick='return confirm(\"Are you sure you want to import settings? This will replace your current configuration!\");' />");
        html.append("</td></tr>");
        html.append("</table>");
        html.append("</form>");
        html.append("</div>");
        
        // Status/Help section
        html.append("<div style='margin-bottom: 20px; padding: 10px; border: 1px solid #ccc; background-color: #fff8dc;'>");
        html.append("<h3>What's Included</h3>");
        html.append("<ul>");
        html.append("<li><strong>Included:</strong> Links, Echo Areas, File Areas, Routing Rules, Rewrite Rules, ");
        html.append("Netmail Accept Rules, Scripts, Scheduled Tasks, Subscriptions</li>");
        html.append("<li><strong>Excluded:</strong> Message content, Waiting queues, User accounts, Temporary data</li>");
        html.append("</ul>");
        html.append("<p><strong>Tip:</strong> Use export/import to backup configurations or migrate settings between systems.</p>");
        html.append("</div>");
        
        html.footer();
        ctx.html(html.get());
    }
}