package org.jnode.httpd.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jnode.dto.*;
import jnode.orm.ORMManager;
import jnode.logger.Logger;
import jnode.install.DefaultVersion;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.*;

/**
 * Service for exporting and importing jNode settings (all configuration tables except message data)
 */
public class SettingsExportImportService {
    private static final Logger logger = Logger.getLogger(SettingsExportImportService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Tables to include in export (configuration/settings only)
    private static final Class<?>[] EXPORT_ENTITIES = {
        // Base entities with no foreign key dependencies
        Echoarea.class,
        Filearea.class,
        
        // Links must come before entities that reference them
        Link.class,
        
        // Entities that depend on Link
        LinkOption.class,
        Subscription.class,    // depends on Link and Echoarea
        FileSubscription.class, // depends on Link and Filearea
        
        // Other entities
        Route.class,
        Rewrite.class,
        NetmailAcceptRule.class,
        Robot.class,
        Jscript.class,
        ScriptHelper.class,
        Schedule.class,
        Version.class
    };
    
    /**
     * Export all settings to JSON format
     * @return JSON string containing all settings
     */
    public String exportSettings() throws SQLException, IOException {
        logger.l4("Starting settings export");
        
        ObjectNode root = objectMapper.createObjectNode();
        root.put("jnode_version", DefaultVersion.getSelf().toString());
        root.put("export_timestamp", System.currentTimeMillis());
        root.put("export_date", new Date().toString());
        
        ObjectNode tables = objectMapper.createObjectNode();
        
        for (Class<?> entityClass : EXPORT_ENTITIES) {
            try {
                String tableName = getTableName(entityClass);
                List<?> entities = ORMManager.get(entityClass).getAll();
                
                ArrayNode tableData = objectMapper.createArrayNode();
                for (Object entity : entities) {
                    ObjectNode entityNode;
                    if (entity instanceof LinkOption) {
                        // Handle LinkOption specially to avoid circular reference
                        entityNode = serializeLinkOption((LinkOption) entity);
                    } else if (entity instanceof Subscription) {
                        // Handle Subscription specially to avoid circular reference
                        entityNode = serializeSubscription((Subscription) entity);
                    } else if (entity instanceof FileSubscription) {
                        // Handle FileSubscription specially to avoid circular reference
                        entityNode = serializeFileSubscription((FileSubscription) entity);
                    } else if (entity instanceof Route) {
                        // Handle Route specially to avoid circular reference
                        entityNode = serializeRoute((Route) entity);
                    } else {
                        entityNode = objectMapper.valueToTree(entity);
                    }
                    tableData.add(entityNode);
                }
                
                tables.set(tableName, tableData);
                logger.l4("Exported " + entities.size() + " records from " + tableName);
            } catch (Exception e) {
                logger.l2("Error exporting " + entityClass.getSimpleName() + ": " + e.getMessage());
                throw new SQLException("Failed to export " + entityClass.getSimpleName(), e);
            }
        }
        
        root.set("tables", tables);
        
        StringWriter writer = new StringWriter();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(writer, root);
        
        logger.l4("Settings export completed successfully");
        return writer.toString();
    }
    
    /**
     * Import settings from JSON format
     * @param jsonData JSON string containing settings
     * @return Import result summary
     */
    public ImportResult importSettings(String jsonData) throws SQLException, IOException {
        logger.l4("Starting settings import");
        
        ImportResult result = new ImportResult();
        
        try {
            JsonNode root = objectMapper.readTree(jsonData);
            
            // Validate format
            if (!root.has("tables")) {
                throw new IllegalArgumentException("Invalid export format: missing 'tables' section");
            }
            
            JsonNode tables = root.get("tables");
            
            // Import in order to respect foreign key constraints
            for (Class<?> entityClass : EXPORT_ENTITIES) {
                String tableName = getTableName(entityClass);
                
                JsonNode tableData = null;
                String actualTableName = tableName;
                
                if (tables.has(tableName)) {
                    tableData = tables.get(tableName);
                } else {
                    // Try legacy table name (class name lowercase) for backward compatibility
                    String legacyTableName = entityClass.getSimpleName().toLowerCase();
                    if (tables.has(legacyTableName)) {
                        tableData = tables.get(legacyTableName);
                        actualTableName = legacyTableName;
                        logger.l4("Using legacy table name '" + legacyTableName + "' for " + entityClass.getSimpleName());
                    }
                }
                
                if (tableData != null) {
                    int importedCount = importTable(entityClass, tableData);
                    result.addTableResult(actualTableName, importedCount);
                    logger.l4("Imported " + importedCount + " records from " + actualTableName + " to " + tableName);
                    
                    // Extra logging for Link imports to debug foreign key issues
                    if (entityClass == Link.class && importedCount > 0) {
                        try {
                            List<Link> links = ORMManager.get(Link.class).getAll();
                            logger.l4("After Link import, database contains " + links.size() + " links");
                            for (Link link : links) {
                                logger.l4("  Link ID: " + link.getId() + ", Address: " + link.getLinkAddress());
                            }
                        } catch (Exception e) {
                            logger.l2("Error checking imported links: " + e.getMessage());
                        }
                    }
                } else {
                    logger.l4("No data for table " + tableName + " (or legacy " + entityClass.getSimpleName().toLowerCase() + ") in import file");
                }
            }
            
            result.setSuccess(true);
            logger.l4("Settings import completed successfully");
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            logger.l2("Settings import failed: " + e.getMessage());
            throw new SQLException("Settings import failed", e);
        }
        
        return result;
    }
    
    /**
     * Clear all settings tables before import
     */
    public void clearAllSettings() throws SQLException {
        logger.l4("Clearing all settings tables");
        
        // Clear in reverse order to respect foreign key constraints
        Class<?>[] reverseOrder = new Class<?>[EXPORT_ENTITIES.length];
        for (int i = 0; i < EXPORT_ENTITIES.length; i++) {
            reverseOrder[i] = EXPORT_ENTITIES[EXPORT_ENTITIES.length - 1 - i];
        }
        
        for (Class<?> entityClass : reverseOrder) {
            try {
                // Delete all records from the table using raw SQL
                String tableName = getTableName(entityClass);
                ORMManager.get(entityClass).executeRaw("DELETE FROM " + tableName);
                logger.l4("Cleared table " + tableName);
            } catch (Exception e) {
                logger.l2("Error clearing " + entityClass.getSimpleName() + ": " + e.getMessage());
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private int importTable(Class<?> entityClass, JsonNode tableData) throws Exception {
        if (!tableData.isArray()) {
            return 0;
        }
        
        int count = 0;
        for (JsonNode entityNode : tableData) {
            Object entity;
            if (entityClass == LinkOption.class) {
                // Handle LinkOption specially to restore foreign key reference
                entity = deserializeLinkOption(entityNode);
            } else if (entityClass == Subscription.class) {
                // Handle Subscription specially to restore foreign key references
                entity = deserializeSubscription(entityNode);
            } else if (entityClass == FileSubscription.class) {
                // Handle FileSubscription specially to restore foreign key references
                entity = deserializeFileSubscription(entityNode);
            } else if (entityClass == Route.class) {
                // Handle Route specially to restore foreign key references
                entity = deserializeRoute(entityNode);
            } else {
                entity = objectMapper.treeToValue(entityNode, entityClass);
            }
            
            // Skip null entities (e.g., LinkOptions with missing Link references)
            if (entity != null) {
                // Use upsert logic to handle existing records
                upsertEntity(entityClass, entity);
                count++;
            }
        }
        
        return count;
    }
    
    /**
     * Upsert entity (insert if new, update if exists)
     */
    @SuppressWarnings("unchecked")
    private void upsertEntity(Class<?> entityClass, Object entity) throws Exception {
        jnode.dao.GenericDAO<Object> dao = (jnode.dao.GenericDAO<Object>) ORMManager.get(entityClass);
        
        // Handle special cases for entities without single ID field
        if (entityClass == Subscription.class) {
            upsertSubscription(dao, (Subscription) entity);
            return;
        } else if (entityClass == FileSubscription.class) {
            upsertFileSubscription(dao, (FileSubscription) entity);
            return;
        }
        
        // Standard entities with ID field
        Object entityId = getEntityId(entity);
        
        if (entityId != null) {
            // Check if entity with this ID already exists
            Object existingEntity = dao.getById((Long) entityId);
            
            if (existingEntity != null) {
                // Entity exists, update it
                logger.l4("Updating existing " + entityClass.getSimpleName() + " with ID: " + entityId);
                dao.update(entity);
            } else {
                // Entity doesn't exist, create new one
                logger.l4("Creating new " + entityClass.getSimpleName() + " with ID: " + entityId);
                
                // For entities with generatedId = true, we need to use raw SQL to preserve original IDs
                if (hasGeneratedId(entityClass)) {
                    insertWithPreservedId(dao, entity, entityClass);
                } else {
                    dao.save(entity);
                }
            }
        } else {
            // No ID specified, create new entity (database will generate ID)
            logger.l4("Creating new " + entityClass.getSimpleName() + " (auto-generated ID)");
            dao.save(entity);
        }
    }
    
    /**
     * Upsert Subscription (composite key: link_id + echoarea_id)
     */
    private void upsertSubscription(jnode.dao.GenericDAO<Object> dao, Subscription subscription) throws Exception {
        // Check if subscription already exists based on link and echoarea
        List<?> existing = dao.getAnd("link_id", "=", subscription.getLink().getId(), 
                                      "echoarea_id", "=", subscription.getArea().getId());
        
        if (!existing.isEmpty()) {
            // Subscription exists, no need to update (it's just a relationship record)
            logger.l4("Subscription already exists: Link " + subscription.getLink().getId() + 
                     " -> Echoarea " + subscription.getArea().getId());
        } else {
            // Create new subscription
            logger.l4("Creating new Subscription: Link " + subscription.getLink().getId() + 
                     " -> Echoarea " + subscription.getArea().getId());
            dao.save(subscription);
        }
    }
    
    /**
     * Upsert FileSubscription (composite key: link_id + filearea_id)
     */
    private void upsertFileSubscription(jnode.dao.GenericDAO<Object> dao, FileSubscription fileSubscription) throws Exception {
        // Check if file subscription already exists based on link and filearea
        List<?> existing = dao.getAnd("link_id", "=", fileSubscription.getLink().getId(), 
                                      "filearea_id", "=", fileSubscription.getArea().getId());
        
        if (!existing.isEmpty()) {
            // FileSubscription exists, no need to update (it's just a relationship record)
            logger.l4("FileSubscription already exists: Link " + fileSubscription.getLink().getId() + 
                     " -> Filearea " + fileSubscription.getArea().getId());
        } else {
            // Create new file subscription
            logger.l4("Creating new FileSubscription: Link " + fileSubscription.getLink().getId() + 
                     " -> Filearea " + fileSubscription.getArea().getId());
            dao.save(fileSubscription);
        }
    }
    
    /**
     * Get the ID field value from an entity using reflection
     */
    private Object getEntityId(Object entity) {
        try {
            // Try common ID field names and methods
            if (entity != null) {
                Class<?> clazz = entity.getClass();
                
                // Try getId() method first
                try {
                    java.lang.reflect.Method getIdMethod = clazz.getMethod("getId");
                    return getIdMethod.invoke(entity);
                } catch (NoSuchMethodException e) {
                    // Try direct field access for entities without getId method
                    try {
                        java.lang.reflect.Field idField = clazz.getDeclaredField("id");
                        idField.setAccessible(true);
                        return idField.get(entity);
                    } catch (NoSuchFieldException ex) {
                        // Some entities might not have an ID field (like Subscription, FileSubscription)
                        // For these, we'll return null and always create new records
                        return null;
                    }
                }
            }
        } catch (Exception e) {
            logger.l2("Error getting entity ID for " + entity.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Serialize LinkOption without circular reference
     */
    private ObjectNode serializeLinkOption(LinkOption linkOption) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", linkOption.getId());
        node.put("link_id", linkOption.getLink().getId()); // Store just the ID
        node.put("option", linkOption.getOption());
        node.put("value", linkOption.getValue());
        return node;
    }
    
    /**
     * Deserialize LinkOption and restore Link foreign key reference
     */
    private LinkOption deserializeLinkOption(JsonNode node) throws Exception {
        LinkOption linkOption = new LinkOption();
        
        if (node.has("id")) {
            linkOption.setId(node.get("id").asLong());
        }
        
        // Restore Link foreign key reference
        if (node.has("link_id")) {
            Long linkId = node.get("link_id").asLong();
            Link link = ORMManager.get(Link.class).getById(linkId);
            if (link == null) {
                logger.l2("Skipping LinkOption: Link with ID " + linkId + " not found during import");
                return null; // Skip this LinkOption
            }
            linkOption.setLink(link);
        }
        
        if (node.has("option")) {
            linkOption.setOption(node.get("option").asText());
        }
        
        if (node.has("value")) {
            linkOption.setValue(node.get("value").asText());
        }
        
        return linkOption;
    }
    
    /**
     * Serialize Subscription without circular reference
     */
    private ObjectNode serializeSubscription(Subscription subscription) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("link_id", subscription.getLink().getId());
        node.put("echoarea_id", subscription.getArea().getId());
        return node;
    }
    
    /**
     * Deserialize Subscription and restore foreign key references
     */
    private Subscription deserializeSubscription(JsonNode node) throws Exception {
        Subscription subscription = new Subscription();
        
        // Restore Link foreign key reference
        if (node.has("link_id")) {
            Long linkId = node.get("link_id").asLong();
            Link link = ORMManager.get(Link.class).getById(linkId);
            if (link == null) {
                logger.l2("Skipping Subscription: Link with ID " + linkId + " not found during import");
                return null; // Skip this Subscription
            }
            subscription.setLink(link);
        }
        
        // Restore Echoarea foreign key reference
        if (node.has("echoarea_id")) {
            Long echoareaId = node.get("echoarea_id").asLong();
            Echoarea echoarea = ORMManager.get(Echoarea.class).getById(echoareaId);
            if (echoarea == null) {
                logger.l2("Skipping Subscription: Echoarea with ID " + echoareaId + " not found during import");
                return null; // Skip this Subscription
            }
            subscription.setArea(echoarea);
        }
        
        return subscription;
    }
    
    /**
     * Serialize FileSubscription without circular reference
     */
    private ObjectNode serializeFileSubscription(FileSubscription fileSubscription) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("link_id", fileSubscription.getLink().getId());
        node.put("filearea_id", fileSubscription.getArea().getId());
        return node;
    }
    
    /**
     * Deserialize FileSubscription and restore foreign key references
     */
    private FileSubscription deserializeFileSubscription(JsonNode node) throws Exception {
        FileSubscription fileSubscription = new FileSubscription();
        
        // Restore Link foreign key reference
        if (node.has("link_id")) {
            Long linkId = node.get("link_id").asLong();
            Link link = ORMManager.get(Link.class).getById(linkId);
            if (link == null) {
                logger.l2("Skipping FileSubscription: Link with ID " + linkId + " not found during import");
                return null; // Skip this FileSubscription
            }
            fileSubscription.setLink(link);
        }
        
        // Restore Filearea foreign key reference
        if (node.has("filearea_id")) {
            Long fileareaId = node.get("filearea_id").asLong();
            Filearea filearea = ORMManager.get(Filearea.class).getById(fileareaId);
            if (filearea == null) {
                logger.l2("Skipping FileSubscription: Filearea with ID " + fileareaId + " not found during import");
                return null; // Skip this FileSubscription
            }
            fileSubscription.setArea(filearea);
        }
        
        return fileSubscription;
    }
    
    /**
     * Serialize Route without circular reference
     */
    private ObjectNode serializeRoute(Route route) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", route.getId());
        node.put("nice", route.getNice());
        node.put("from_name", route.getFromName());
        node.put("to_name", route.getToName());
        node.put("from_address", route.getFromAddr());
        node.put("to_address", route.getToAddr());
        node.put("subject", route.getSubject());
        // Handle nullable foreign key reference
        if (route.getRouteVia() != null) {
            node.put("route_via_id", route.getRouteVia().getId());
        } else {
            node.putNull("route_via_id");
        }
        return node;
    }
    
    /**
     * Deserialize Route and restore foreign key references
     */
    private Route deserializeRoute(JsonNode node) throws Exception {
        Route route = new Route();
        
        if (node.has("id")) {
            route.setId(node.get("id").asLong());
        }
        
        if (node.has("nice")) {
            route.setNice(node.get("nice").asLong());
        }
        
        if (node.has("from_name")) {
            route.setFromName(node.get("from_name").asText());
        }
        
        if (node.has("to_name")) {
            route.setToName(node.get("to_name").asText());
        }
        
        if (node.has("from_address")) {
            route.setFromAddr(node.get("from_address").asText());
        }
        
        if (node.has("to_address")) {
            route.setToAddr(node.get("to_address").asText());
        }
        
        if (node.has("subject")) {
            route.setSubject(node.get("subject").asText());
        }
        
        // Restore Link foreign key reference (nullable)
        if (node.has("route_via_id") && !node.get("route_via_id").isNull()) {
            Long routeViaId = node.get("route_via_id").asLong();
            Link routeVia = ORMManager.get(Link.class).getById(routeViaId);
            if (routeVia == null) {
                throw new SQLException("Link with ID " + routeViaId + " not found while importing Route");
            }
            route.setRouteVia(routeVia);
        }
        
        return route;
    }
    
    private String getTableName(Class<?> entityClass) {
        // Get the actual table name from @DatabaseTable annotation
        try {
            if (entityClass.isAnnotationPresent(com.j256.ormlite.table.DatabaseTable.class)) {
                com.j256.ormlite.table.DatabaseTable annotation = 
                    entityClass.getAnnotation(com.j256.ormlite.table.DatabaseTable.class);
                if (annotation.tableName() != null && !annotation.tableName().isEmpty()) {
                    return annotation.tableName();
                }
            }
        } catch (Exception e) {
            logger.l2("Could not get table name from annotation for " + entityClass.getSimpleName() + ": " + e.getMessage());
        }
        
        // Fallback to simple conversion if annotation is not found
        String className = entityClass.getSimpleName();
        return className.toLowerCase();
    }
    
    /**
     * Check if entity class has auto-generated ID field
     */
    private boolean hasGeneratedId(Class<?> entityClass) {
        try {
            for (java.lang.reflect.Field field : entityClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(com.j256.ormlite.field.DatabaseField.class)) {
                    com.j256.ormlite.field.DatabaseField annotation = 
                        field.getAnnotation(com.j256.ormlite.field.DatabaseField.class);
                    if (annotation.generatedId()) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.l2("Error checking for generatedId in " + entityClass.getSimpleName() + ": " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Insert entity with preserved ID using raw SQL (bypasses auto-generation)
     */
    private void insertWithPreservedId(jnode.dao.GenericDAO<Object> dao, Object entity, Class<?> entityClass) throws Exception {
        try {
            // Build INSERT statement dynamically using reflection
            String tableName = getTableName(entityClass);
            
            // Get all fields with @DatabaseField annotation
            List<java.lang.reflect.Field> dbFields = new ArrayList<>();
            List<String> columnNames = new ArrayList<>();
            List<String> paramValues = new ArrayList<>();
            
            for (java.lang.reflect.Field field : entityClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(com.j256.ormlite.field.DatabaseField.class)) {
                    com.j256.ormlite.field.DatabaseField annotation = 
                        field.getAnnotation(com.j256.ormlite.field.DatabaseField.class);
                    
                    // Get column name
                    String columnName = annotation.columnName();
                    if (columnName.isEmpty()) {
                        columnName = field.getName(); // fallback to field name
                    }
                    
                    dbFields.add(field);
                    columnNames.add(columnName);
                    
                    // Get field value
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    
                    if (value == null) {
                        // Use default value from annotation if available
                        String defaultValue = annotation.defaultValue();
                        if (!defaultValue.equals(com.j256.ormlite.field.DatabaseField.DEFAULT_STRING)) {
                            paramValues.add(defaultValue);
                        } else {
                            // For null values, we'll need to handle them specially in SQL
                            paramValues.add(""); // Empty string as fallback
                        }
                    } else {
                        // Convert value to string for SQL
                        if (value instanceof Boolean) {
                            // Check if this is a boolean field type for PostgreSQL compatibility
                            if (annotation.dataType() == com.j256.ormlite.field.DataType.BOOLEAN) {
                                paramValues.add(((Boolean) value) ? "true" : "false");
                            } else {
                                paramValues.add(((Boolean) value) ? "1" : "0");
                            }
                        } else if (value instanceof Date) {
                            // Convert Date to timestamp
                            paramValues.add(String.valueOf(((Date) value).getTime()));
                        } else if (annotation.foreign()) {
                            // For foreign key fields, extract the ID from the related entity
                            try {
                                java.lang.reflect.Method getIdMethod = value.getClass().getMethod("getId");
                                Object id = getIdMethod.invoke(value);
                                if (id != null) {
                                    paramValues.add(id.toString());
                                } else {
                                    paramValues.add(""); // Will be handled as NULL or default
                                }
                            } catch (Exception e) {
                                logger.l2("Warning: Could not extract ID from foreign entity " + value.getClass().getSimpleName() + ": " + e.getMessage());
                                paramValues.add(""); // Fallback
                            }
                        } else {
                            paramValues.add(value.toString());
                        }
                    }
                }
            }
            
            // Build SQL statement with embedded values (since executeRaw doesn't support parameters)
            String columns = String.join(", ", columnNames);
            
            // Build values list with proper SQL escaping
            List<String> sqlValues = new ArrayList<>();
            for (int i = 0; i < paramValues.size(); i++) {
                String value = paramValues.get(i);
                java.lang.reflect.Field field = dbFields.get(i);
                com.j256.ormlite.field.DatabaseField annotation = 
                    field.getAnnotation(com.j256.ormlite.field.DatabaseField.class);
                
                if (value == null) {
                    sqlValues.add("NULL");
                } else if (value.isEmpty() && !annotation.canBeNull()) {
                    // For NOT NULL fields with empty string values, use empty string literal
                    sqlValues.add("''");
                } else if (value.isEmpty()) {
                    sqlValues.add("NULL");
                } else if (value.matches("\\d+")) {
                    // Numeric value - no quotes needed
                    sqlValues.add(value);
                } else {
                    // String value - needs quotes and escaping
                    String escaped = value.replace("'", "''"); // Escape single quotes
                    sqlValues.add("'" + escaped + "'");
                }
            }
            
            String values = String.join(", ", sqlValues);
            String sql = "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + values + ")";
            
            logger.l4("Executing preserved ID insert: " + sql);
            
            // Execute the raw SQL
            dao.executeRaw(sql);
            
        } catch (Exception e) {
            logger.l2("Error in insertWithPreservedId for " + entityClass.getSimpleName() + ": " + e.getMessage());
            logger.l2("Falling back to regular save - ID may not be preserved");
            dao.save(entity);
        }
    }
    
    /**
     * Result of an import operation
     */
    public static class ImportResult {
        private boolean success;
        private String errorMessage;
        private Map<String, Integer> tableResults = new HashMap<>();
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        public Map<String, Integer> getTableResults() { return tableResults; }
        public void addTableResult(String tableName, int count) { tableResults.put(tableName, count); }
        
        public int getTotalRecords() {
            return tableResults.values().stream().mapToInt(Integer::intValue).sum();
        }
    }
}