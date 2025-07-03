package org.jnode.httpd.routes.post.secure;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.UploadedFile;
import org.jetbrains.annotations.NotNull;
import org.jnode.httpd.service.SettingsExportImportService;
import org.jnode.httpd.util.HTMLi18n;
import jnode.logger.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Settings import handler - processes uploaded JSON settings file
 */
public class SettingsImportRoute implements Handler {
    private static final Logger logger = Logger.getLogger(SettingsImportRoute.class);
    private final SettingsExportImportService service = new SettingsExportImportService();
    
    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        HTMLi18n html = HTMLi18n.create(ctx, true);
        
        try {
            logger.l4("Processing settings import request");
            
            // Get uploaded file
            UploadedFile uploadedFile = ctx.uploadedFile("settings_file");
            if (uploadedFile == null) {
                throw new IllegalArgumentException("No file uploaded");
            }
            
            // Read file content
            String jsonData = new String(uploadedFile.content().readAllBytes(), StandardCharsets.UTF_8);
            
            // Check if we should clear existing settings
            boolean clearExisting = ctx.formParam("clear_existing") != null;
            
            html.append("<h2>Settings Import Result</h2>");
            
            SettingsExportImportService.ImportResult result;
            
            if (clearExisting) {
                html.append("<p>Clearing existing settings...</p>");
                service.clearAllSettings();
                html.append("<p style='color: green;'>✓ Existing settings cleared</p>");
            }
            
            html.append("<p>Importing settings from: " + uploadedFile.filename() + "</p>");
            
            // Perform import
            result = service.importSettings(jsonData);
            
            if (result.isSuccess()) {
                html.append("<div style='padding: 10px; border: 1px solid #4CAF50; background-color: #DFF2BF; color: #4F8A10;'>");
                html.append("<h3>✓ Import Successful</h3>");
                html.append("<p>Settings imported successfully!</p>");
                html.append("<p><strong>Total records imported:</strong> " + result.getTotalRecords() + "</p>");
                
                html.append("<table class='links' style='margin-top: 10px;'>");
                html.append("<tr><th>Table</th><th>Records</th></tr>");
                
                result.getTableResults().forEach((tableName, count) -> {
                    html.append("<tr><td>" + tableName + "</td><td>" + count + "</td></tr>");
                });
                
                html.append("</table>");
                html.append("</div>");
                
                logger.l4("Settings import completed successfully: " + result.getTotalRecords() + " records");
                
            } else {
                html.append("<div style='padding: 10px; border: 1px solid #FF6B6B; background-color: #FFE6E6; color: #D8000C;'>");
                html.append("<h3>✗ Import Failed</h3>");
                html.append("<p>Error: " + result.getErrorMessage() + "</p>");
                html.append("</div>");
                
                logger.l2("Settings import failed: " + result.getErrorMessage());
            }
            
        } catch (Exception e) {
            logger.l2("Settings import error: " + e.getMessage());
            
            html.append("<div style='padding: 10px; border: 1px solid #FF6B6B; background-color: #FFE6E6; color: #D8000C;'>");
            html.append("<h3>✗ Import Error</h3>");
            html.append("<p>Error: " + e.getMessage() + "</p>");
            html.append("</div>");
        }
        
        html.append("<p style='margin-top: 20px;'>");
        html.append("<a href='/secure/settings.html' class='css-link-1'>← Back to Settings</a>");
        html.append("</p>");
        
        html.footer();
        ctx.html(html.get());
    }
}