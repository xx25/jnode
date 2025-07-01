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

package org.jnode.httpd.filters;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jnode.httpd.i18n.LocaleManager;
import org.jnode.httpd.i18n.TranslationService;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Filter to set up locale and translation context for all requests
 */
public class LocaleFilter implements Handler {
    
    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        // Get current locale for this request
        Locale locale = LocaleManager.getCurrentLocale(ctx);
        
        // Store locale in request attribute for easy access
        ctx.attribute("locale", locale);
        
        // Create translation helper and store in request
        TranslationHelper t = new TranslationHelper(locale);
        ctx.attribute("t", t);
        
        // Also store supported locales for language selector
        ctx.attribute("supportedLocales", TranslationService.getInstance().getSupportedLocales());
    }
    
    /**
     * Helper class to make translations easier in templates
     */
    public static class TranslationHelper {
        private final Locale locale;
        private final TranslationService ts;
        
        public TranslationHelper(Locale locale) {
            this.locale = locale;
            this.ts = TranslationService.getInstance();
        }
        
        /**
         * Get translation for key
         */
        public String get(String key) {
            return ts.getMessage(key, locale);
        }
        
        /**
         * Get translation with parameters
         */
        public String get(String key, Object... params) {
            return ts.getMessage(key, locale, params);
        }
        
        /**
         * Get current locale
         */
        public Locale getLocale() {
            return locale;
        }
        
        /**
         * Get locale language code
         */
        public String getLang() {
            return locale.getLanguage();
        }
        
        /**
         * Get all translations for template rendering
         */
        public Map<String, String> getAll(String... keys) {
            Map<String, String> translations = new HashMap<>();
            for (String key : keys) {
                translations.put(key.replace(".", "_"), get(key));
            }
            return translations;
        }
    }
}