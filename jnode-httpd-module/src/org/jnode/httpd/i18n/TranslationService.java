/*
 * Licensed to the jNode FTN Platform Development Team (jNode Team)
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

package org.jnode.httpd.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import jnode.logger.Logger;

/**
 * Translation service for jNode web interface.
 * Designed to be extensible for use in other modules.
 */
public class TranslationService {
    private static final Logger logger = Logger.getLogger(TranslationService.class);
    private static final String BUNDLE_NAME = "i18n.messages";
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    
    private static TranslationService instance;
    private final Map<Locale, ResourceBundle> bundleCache = new ConcurrentHashMap<>();
    private final Set<Locale> supportedLocales = new HashSet<>();
    
    private TranslationService() {
        // Initialize supported locales
        supportedLocales.add(Locale.ENGLISH);
        supportedLocales.add(new Locale("ru"));
        supportedLocales.add(Locale.GERMAN);
        supportedLocales.add(new Locale("es"));
        supportedLocales.add(new Locale("pt"));
        supportedLocales.add(Locale.ITALIAN);
        supportedLocales.add(Locale.FRENCH);
        supportedLocales.add(new Locale("pl"));
        
        // Clear any existing ResourceBundle cache to force UTF-8 loading
        ResourceBundle.clearCache();
    }
    
    public static synchronized TranslationService getInstance() {
        if (instance == null) {
            instance = new TranslationService();
        }
        return instance;
    }
    
    /**
     * Get translated message for the given key and locale
     */
    public String getMessage(String key, Locale locale) {
        if (key == null) {
            return "";
        }
        
        ResourceBundle bundle = getBundle(locale);
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            logger.l3("Missing translation for key: " + key + " in locale: " + locale);
            // Try default locale
            try {
                return getBundle(DEFAULT_LOCALE).getString(key);
            } catch (MissingResourceException e2) {
                // Return key as fallback
                return key;
            }
        }
    }
    
    /**
     * Get translated message with parameters
     */
    public String getMessage(String key, Locale locale, Object... params) {
        String pattern = getMessage(key, locale);
        if (params == null || params.length == 0) {
            return pattern;
        }
        return MessageFormat.format(pattern, params);
    }
    
    /**
     * Get translated message using default locale
     */
    public String getMessage(String key) {
        return getMessage(key, DEFAULT_LOCALE);
    }
    
    /**
     * Get ResourceBundle for locale with caching and UTF-8 support
     */
    private ResourceBundle getBundle(Locale locale) {
        return bundleCache.computeIfAbsent(locale, loc -> {
            // English uses the default bundle (messages.properties) to avoid duplication
            if (Locale.ENGLISH.equals(loc)) {
                try {
                    return ResourceBundle.getBundle(BUNDLE_NAME, Locale.ROOT, new UTF8Control());
                } catch (MissingResourceException e) {
                    logger.l1("Failed to load default resource bundle", e);
                    return new ListResourceBundle() {
                        @Override
                        protected Object[][] getContents() {
                            return new Object[0][0];
                        }
                    };
                }
            }
            
            try {
                return ResourceBundle.getBundle(BUNDLE_NAME, loc, new UTF8Control());
            } catch (MissingResourceException e) {
                logger.l3("Resource bundle not found for locale: " + loc + ", using default");
                try {
                    return ResourceBundle.getBundle(BUNDLE_NAME, DEFAULT_LOCALE, new UTF8Control());
                } catch (MissingResourceException e2) {
                    logger.l1("Failed to load default resource bundle", e2);
                    return new ListResourceBundle() {
                        @Override
                        protected Object[][] getContents() {
                            return new Object[0][0];
                        }
                    };
                }
            }
        });
    }
    
    /**
     * ResourceBundle.Control subclass to handle UTF-8 encoded properties files
     */
    private static class UTF8Control extends ResourceBundle.Control {
        private static final Logger logger = Logger.getLogger(UTF8Control.class);
        
        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format,
                                      ClassLoader loader, boolean reload)
                throws IllegalAccessException, InstantiationException, IOException {
            
            // Only handle properties files
            if (!"java.properties".equals(format)) {
                return null;
            }
            
            String bundleName = toBundleName(baseName, locale);
            String resourceName = toResourceName(bundleName, "properties");
            logger.l4("UTF8Control loading: " + resourceName + " for locale: " + locale);
            
            InputStream stream = loader.getResourceAsStream(resourceName);
            if (stream == null) {
                logger.l4("UTF8Control: Resource not found: " + resourceName);
                return null;
            }
            
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                logger.l4("UTF8Control: Successfully loaded UTF-8 bundle: " + resourceName);
                return new PropertyResourceBundle(reader);
            }
        }
    }
    
    /**
     * Get all supported locales
     */
    public Set<Locale> getSupportedLocales() {
        return new HashSet<>(supportedLocales);
    }
    
    /**
     * Check if locale is supported
     */
    public boolean isLocaleSupported(Locale locale) {
        return supportedLocales.contains(locale);
    }
    
    /**
     * Parse locale from string (e.g., "en", "ru", "de")
     */
    public Locale parseLocale(String localeString) {
        if (localeString == null || localeString.isEmpty()) {
            return DEFAULT_LOCALE;
        }
        
        String[] parts = localeString.split("_");
        Locale locale;
        
        if (parts.length == 1) {
            locale = new Locale(parts[0]);
        } else if (parts.length == 2) {
            locale = new Locale(parts[0], parts[1]);
        } else {
            locale = new Locale(parts[0], parts[1], parts[2]);
        }
        
        return isLocaleSupported(locale) ? locale : DEFAULT_LOCALE;
    }
    
    /**
     * Get locale display name in its own language
     */
    public String getLocaleDisplayName(Locale locale) {
        return locale.getDisplayName(locale);
    }
    
    /**
     * Clear cache (useful for development/testing)
     */
    public void clearCache() {
        bundleCache.clear();
    }
}