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

package org.jnode.httpd.i18n;

import io.javalin.http.Context;
import java.util.Locale;

/**
 * Manages locale selection and persistence for web sessions
 */
public class LocaleManager {
    private static final String LOCALE_COOKIE_NAME = "jnode_locale";
    private static final String LOCALE_SESSION_KEY = "locale";
    private static final int COOKIE_MAX_AGE = 365 * 24 * 60 * 60; // 1 year
    
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    
    /**
     * Get current locale from request context
     * Priority: URL parameter > Session > Cookie > Browser preference > Default
     */
    public static Locale getCurrentLocale(Context ctx) {
        TranslationService ts = TranslationService.getInstance();
        
        // 1. Check URL parameter
        String langParam = ctx.queryParam("lang");
        if (langParam != null) {
            Locale locale = ts.parseLocale(langParam);
            setLocale(ctx, locale);
            return locale;
        }
        
        // 2. Check session
        Object sessionLocale = ctx.sessionAttribute(LOCALE_SESSION_KEY);
        if (sessionLocale instanceof Locale) {
            return (Locale) sessionLocale;
        }
        
        // 3. Check cookie
        String cookieValue = ctx.cookie(LOCALE_COOKIE_NAME);
        if (cookieValue != null) {
            Locale locale = ts.parseLocale(cookieValue);
            ctx.sessionAttribute(LOCALE_SESSION_KEY, locale);
            return locale;
        }
        
        // 4. Check browser preference
        String acceptLanguage = ctx.header("Accept-Language");
        if (acceptLanguage != null) {
            Locale browserLocale = parseAcceptLanguage(acceptLanguage);
            if (ts.isLocaleSupported(browserLocale)) {
                setLocale(ctx, browserLocale);
                return browserLocale;
            }
        }
        
        // 5. Return default
        return DEFAULT_LOCALE;
    }
    
    /**
     * Set locale for the current session and persist in cookie
     */
    public static void setLocale(Context ctx, Locale locale) {
        ctx.sessionAttribute(LOCALE_SESSION_KEY, locale);
        ctx.cookie(LOCALE_COOKIE_NAME, locale.getLanguage(), COOKIE_MAX_AGE);
    }
    
    /**
     * Parse Accept-Language header to find best matching locale
     */
    private static Locale parseAcceptLanguage(String acceptLanguage) {
        // Simple parsing - just take the first language
        // Format: "en-US,en;q=0.9,ru;q=0.8"
        String[] parts = acceptLanguage.split(",");
        if (parts.length > 0) {
            String lang = parts[0].split(";")[0].trim();
            if (lang.contains("-")) {
                lang = lang.split("-")[0];
            }
            return new Locale(lang);
        }
        return DEFAULT_LOCALE;
    }
    
    /**
     * Get translation for key using current locale
     */
    public static String translate(Context ctx, String key) {
        Locale locale = getCurrentLocale(ctx);
        return TranslationService.getInstance().getMessage(key, locale);
    }
    
    /**
     * Get translation with parameters
     */
    public static String translate(Context ctx, String key, Object... params) {
        Locale locale = getCurrentLocale(ctx);
        return TranslationService.getInstance().getMessage(key, locale, params);
    }
}