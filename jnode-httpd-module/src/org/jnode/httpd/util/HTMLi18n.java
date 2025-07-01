/*
 * Licensed to the jNode FTN Platform Develpoment Team (jNode Team)
 * under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for 
 * additional information regarding copyright ownership.  
 * The jNode Team licenses this file to you under the 
 * Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jnode.httpd.util;

import io.javalin.http.Context;
import org.jnode.httpd.filters.LocaleFilter;
import org.jnode.httpd.i18n.TranslationService;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced HTML builder with i18n support
 */
public class HTMLi18n {
    private static final Pattern TRANSLATION_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+(?:\\.\\w+)*)\\s*\\}\\}");
    private final Context ctx;
    private final LocaleFilter.TranslationHelper translator;
    private final HTML html;
    
    private HTMLi18n(Context ctx, boolean secure) {
        this.ctx = ctx;
        this.translator = (LocaleFilter.TranslationHelper) ctx.attribute("t");
        
        // Instead of HTML.start(), create custom HTML structure with i18n menus
        this.html = buildI18nHTML(secure);
    }
    
    /**
     * Build HTML structure with i18n menus instead of using HTML.start()
     */
    private HTML buildI18nHTML(boolean secure) {
        // Get a basic HTML instance but override its content
        HTML baseHtml = HTML.start(false); // Start without secure menu
        
        // Get the current content and replace the English menu with i18n menu
        String content = baseHtml.get();
        
        // Replace English menu with i18n menu
        String englishMenu = HTML.getContents("/parts/menu.html");
        String i18nMenu = HTML.getContents("/parts/menu_i18n.html");
        
        if (i18nMenu.length() > 0) {
            String translatedMenu = processTemplate(i18nMenu);
            content = content.replace(englishMenu, translatedMenu);
        }
        
        // Add secure menu if needed
        if (secure) {
            String secureMenu = HTML.getContents("/parts/secure_menu_i18n.html");
            if (secureMenu.length() > 0) {
                String translatedSecureMenu = processTemplate(secureMenu);
                content = content + translatedSecureMenu;
            }
        }
        
        // Create a new HTML instance and set its content
        HTML result = HTML.start(false);
        // Clear and rebuild content - we'll use a simple string replacement approach
        try {
            // Use reflection to replace the internal content
            java.lang.reflect.Field dataField = HTML.class.getDeclaredField("data");
            dataField.setAccessible(true);
            StringBuilder data = (StringBuilder) dataField.get(result);
            data.setLength(0);
            data.append(content);
        } catch (Exception e) {
            // If reflection fails, fall back to normal behavior
            System.out.println("HTMLi18n: Failed to replace menu content, using fallback");
        }
        
        return result;
    }
    
    /**
     * Create new HTMLi18n instance with context
     */
    public static HTMLi18n create(Context ctx, boolean secure) {
        return new HTMLi18n(ctx, secure);
    }
    
    /**
     * Process template and replace translation keys
     */
    private String processTemplate(String template) {
        if (translator == null) {
            return template;
        }
        
        // Replace all {{key}} with translations
        Matcher matcher = TRANSLATION_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String key = matcher.group(1);
            String translation = translator.get(key);
            matcher.appendReplacement(result, Matcher.quoteReplacement(translation));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Add JavaScript translations to page
     */
    public HTMLi18n addJsTranslations(String... keys) {
        if (translator == null) {
            return this;
        }
        
        Map<String, String> translations = new HashMap<>();
        for (String key : keys) {
            translations.put(key, translator.get(key));
        }
        
        // Create JavaScript block with translations
        StringBuilder js = new StringBuilder();
        js.append("<script>\n");
        js.append("if (typeof jnodeI18n !== 'undefined') {\n");
        js.append("  jnodeI18n.init('").append(translator.getLang()).append("');\n");
        js.append("  jnodeI18n.setTranslations({\n");
        
        boolean first = true;
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            if (!first) js.append(",\n");
            js.append("    '").append(entry.getKey()).append("': '")
              .append(entry.getValue().replace("'", "\\'")).append("'");
            first = false;
        }
        
        js.append("\n  });\n");
        js.append("  jnodeI18n.updateElements();\n");
        js.append("}\n");
        js.append("</script>\n");
        
        html.append(js.toString());
        return this;
    }
    
    /**
     * Add language selector to page
     */
    public HTMLi18n addLanguageSelector() {
        if (translator == null) {
            return this;
        }
        
        StringBuilder selector = new StringBuilder();
        selector.append("<style>\n");
        selector.append(".language-selector {\n");
        selector.append("    text-align: center;\n");
        selector.append("    margin-top: 10px;\n");
        selector.append("    padding: 10px;\n");
        selector.append("}\n");
        selector.append(".language-selector select {\n");
        selector.append("    padding: 5px;\n");
        selector.append("    border: 1px solid #ccc;\n");
        selector.append("    border-radius: 3px;\n");
        selector.append("    font-size: 12px;\n");
        selector.append("}\n");
        selector.append("</style>\n");
        selector.append("<div class=\"language-selector\">\n");
        selector.append("  <select onchange=\"jnodeI18n.changeLanguage(this.value)\">\n");
        
        TranslationService ts = TranslationService.getInstance();
        Locale currentLocale = translator.getLocale();
        
        for (Locale locale : ts.getSupportedLocales()) {
            String selected = locale.equals(currentLocale) ? " selected" : "";
            String langKey = "lang." + locale.getLanguage();
            String langName = translator.get(langKey);
            
            selector.append("    <option value=\"").append(locale.getLanguage())
                   .append("\"").append(selected).append(">")
                   .append(langName).append("</option>\n");
        }
        
        selector.append("  </select>\n");
        selector.append("</div>\n");
        
        html.append(selector.toString());
        return this;
    }
    
    /**
     * Get translation directly
     */
    public String t(String key) {
        return translator != null ? translator.get(key) : key;
    }
    
    /**
     * Get translation with parameters
     */
    public String t(String key, Object... params) {
        return translator != null ? translator.get(key, params) : key;
    }
    
    /**
     * Delegate methods to HTML
     */
    public HTMLi18n append(String content) {
        // Process translations in content
        String processed = processTemplate(content);
        html.append(processed);
        return this;
    }
    
    public HTMLi18n header() {
        // Get the header template and process translations
        String headerContent = HTML.getContents("/parts/header.html");
        String processed = processTemplate(headerContent);
        html.append(String.format(processed, HTML.getJNodeAddress()));
        return this;
    }
    
    public HTMLi18n menu() {
        // Use i18n version if available, otherwise use standard
        String menuContent = HTML.getContents("/parts/menu_i18n.html");
        if (menuContent.length() == 0) {
            menuContent = HTML.getContents("/parts/menu.html");
        }
        System.out.println("HTMLi18n.menu() called - content length: " + menuContent.length());
        html.append(processTemplate(menuContent));
        return this;
    }
    
    public HTMLi18n footer() {
        String footerContent = HTML.getContents("/parts/footer.html");
        html.append(processTemplate(footerContent));
        return this;
    }
    
    public HTMLi18n secureMenu() {
        // Use i18n version if available, otherwise use standard
        String secureMenuContent = HTML.getContents("/parts/secure_menu_i18n.html");
        if (secureMenuContent.length() == 0) {
            secureMenuContent = HTML.getContents("/parts/secure_menu.html");
        }
        html.append(processTemplate(secureMenuContent));
        return this;
    }
    
    public HTMLi18n start(boolean secure) {
        // Already started in constructor
        return this;
    }
    
    public String get() {
        return html.get();
    }
}