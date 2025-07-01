/**
 * Client-side i18n support for jNode web interface
 */
var jnodeI18n = (function() {
    var currentLocale = 'en';
    var translations = {};
    
    /**
     * Initialize i18n with current locale
     */
    function init(locale) {
        currentLocale = locale || 'en';
        // Translations can be loaded dynamically or embedded in page
    }
    
    /**
     * Set translations object
     */
    function setTranslations(trans) {
        translations = trans || {};
    }
    
    /**
     * Get translation for key
     */
    function t(key, params) {
        var text = translations[key] || key;
        
        // Simple parameter replacement
        if (params) {
            for (var param in params) {
                text = text.replace('{' + param + '}', params[param]);
            }
        }
        
        return text;
    }
    
    /**
     * Change language and reload page
     */
    function changeLanguage(lang) {
        // Set cookie for persistence
        document.cookie = 'jnode_locale=' + lang + ';path=/;max-age=' + (365*24*60*60);
        
        // Reload page with new language
        var url = window.location.href;
        
        // Remove existing lang parameter if present
        url = url.replace(/[?&]lang=[^&]*/g, '');
        
        // Determine separator after cleaning URL
        var separator = url.indexOf('?') !== -1 ? '&' : '?';
        
        // Add new lang parameter
        window.location.href = url + separator + 'lang=' + lang;
    }
    
    /**
     * Get current locale
     */
    function getLocale() {
        return currentLocale;
    }
    
    /**
     * Format confirmation messages
     */
    function confirm(key, params) {
        return window.confirm(t(key, params));
    }
    
    /**
     * Update all elements with data-i18n attribute
     */
    function updateElements() {
        var elements = document.querySelectorAll('[data-i18n]');
        for (var i = 0; i < elements.length; i++) {
            var element = elements[i];
            var key = element.getAttribute('data-i18n');
            element.textContent = t(key);
        }
        
        // Update placeholders
        var placeholders = document.querySelectorAll('[data-i18n-placeholder]');
        for (var i = 0; i < placeholders.length; i++) {
            var element = placeholders[i];
            var key = element.getAttribute('data-i18n-placeholder');
            element.placeholder = t(key);
        }
        
        // Update titles
        var titles = document.querySelectorAll('[data-i18n-title]');
        for (var i = 0; i < titles.length; i++) {
            var element = titles[i];
            var key = element.getAttribute('data-i18n-title');
            element.title = t(key);
        }
    }
    
    // Public API
    return {
        init: init,
        setTranslations: setTranslations,
        t: t,
        changeLanguage: changeLanguage,
        getLocale: getLocale,
        confirm: confirm,
        updateElements: updateElements
    };
})();

// Alias for convenience
var _t = jnodeI18n.t;