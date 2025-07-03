package org.jnode.httpd.routes.post.secure;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;
import org.jnode.httpd.service.SettingsExportImportService;
import jnode.logger.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Settings export handler - generates JSON export file
 */
public class SettingsExportRoute implements Handler {
    private static final Logger logger = Logger.getLogger(SettingsExportRoute.class);
    private final SettingsExportImportService service = new SettingsExportImportService();
    
    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        try {
            logger.l4("Processing settings export request");
            
            // Generate export
            String jsonData = service.exportSettings();
            
            // Generate filename with timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "jnode_settings_" + timestamp + ".json";
            
            // Set headers for file download
            ctx.header("Content-Type", "application/json");
            ctx.header("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            ctx.header("Cache-Control", "no-cache, no-store, must-revalidate");
            ctx.header("Pragma", "no-cache");
            ctx.header("Expires", "0");
            
            ctx.result(jsonData);
            
            logger.l4("Settings export completed successfully: " + filename);
            
        } catch (Exception e) {
            logger.l2("Settings export failed: " + e.getMessage());
            ctx.status(500);
            ctx.result("Export failed: " + e.getMessage());
        }
    }
}