# jNode Web Interface Internationalization (i18n) System

## Overview

The jNode FTN (FidoNet Technology Network) web interface supports multiple languages through a comprehensive internationalization system that allows users to switch between languages dynamically and maintains their language preference across sessions.

## Supported Languages

- **English** (en) - Default language
- **Russian** (ru) - Русский  
- **German** (de) - Deutsch
- **Spanish** (es) - Español
- **Portuguese** (pt) - Português
- **Italian** (it) - Italiano
- **French** (fr) - Français
- **Polish** (pl) - Polski

## Architecture Components

### 1. Translation Service (`TranslationService.java`)

**Location:** `src/org/jnode/httpd/i18n/TranslationService.java`

The core translation engine that:
- Manages ResourceBundle loading with **UTF-8 encoding support**
- Handles locale detection and validation
- Provides translation lookup with fallback mechanisms
- Implements singleton pattern for performance
- Supports parameterized translations using `MessageFormat`

**Key Features:**
- **UTF-8 ResourceBundle Support**: Custom `UTF8Control` class enables proper loading of UTF-8 encoded properties files
- **Locale Caching**: Improves performance by caching loaded resource bundles
- **Graceful Fallback**: Falls back to English if translation keys are missing

### 2. Locale Management (`LocaleManager.java`)

**Location:** `src/org/jnode/httpd/i18n/LocaleManager.java`

Handles locale detection and persistence:
- **Priority Order**: URL parameter → Session → Cookie → Browser preference → Default
- **Cookie Persistence**: Stores language choice for 1 year
- **Session Management**: Maintains locale during user session
- **Browser Detection**: Parses `Accept-Language` headers

### 3. Locale Filter (`LocaleFilter.java`)

**Location:** `src/org/jnode/httpd/filters/LocaleFilter.java`

Applied to all HTTP requests (`app.before("*", new LocaleFilter())`):
- Detects current locale for each request
- Creates `TranslationHelper` for easy template access
- Stores locale and translation utilities in request context
- Provides supported locales list for language selectors

### 4. Enhanced HTML Builder (`HTMLi18n.java`)

**Location:** `src/org/jnode/httpd/util/HTMLi18n.java`

Extends the basic HTML builder with internationalization support:
- **Template Processing**: Handles `{{translation.key}}` replacement in templates
- **Automatic Menu Translation**: Replaces English menus with translated versions using reflection
- **UTF-8 Content Handling**: Ensures proper character encoding for all languages
- **JavaScript Integration**: Provides client-side translation support

### 5. Translation Files

**Location:** `src/main/resources/i18n/`

Properties files for each language:
- `messages.properties` - English (default)
- `messages_ru.properties` - Russian  
- `messages_de.properties` - German
- `messages_es.properties` - Spanish
- etc.

**File Structure:**
```properties
# Menu items
menu.about=About Node
menu.request_point=Request Point
menu.request_link=Request Link
menu.management=Settings

# Russian equivalent
menu.about=О системе
menu.request_point=Запросить поинтовый адрес
menu.request_link=Запросить линк
menu.management=Настройки
```

## Language Switching Implementation

### 1. Client-Side Language Selector

**Location:** `resources/www/js/i18n.js`

JavaScript component that:
- Provides dropdown language selector
- Handles language switching via `changeLanguage()` function
- Updates cookies for persistence
- Reloads page with new language parameter
- **Fixed URL Generation**: Properly generates `?lang=en` instead of `&lang=en`

### 2. URL-Based Language Selection

Users can specify language via URL parameters:
- `http://example.com/?lang=ru` - Switch to Russian
- `http://example.com/?lang=de` - Switch to German

### 3. Cookie Persistence

Language preferences are stored in `jnode_locale` cookie:
- **Duration**: 1 year
- **Path**: `/` (site-wide)
- **Auto-detection**: Loaded on subsequent visits

## Template System

### 1. Translation Key Format

Templates use double curly brace syntax:
```html
<h1>{{about.title}}</h1>
<p>{{about.description}}</p>
```

### 2. Menu Templates

Two versions of each menu template:
- `menu.html` - English version  
- `menu_i18n.html` - Template with translation keys

**Example i18n menu:**
```html
<table class="menu">
    <tr>
        <th><a href="/index.html">{{menu.about}}</a></th>
        <th><a href="/requestpoint.html">{{menu.request_point}}</a></th>
        <th><a href="/requestlink.html">{{menu.request_link}}</a></th>
        <th><a href="/secure/index.html">{{menu.management}}</a></th>
    </tr>
</table>
```

### 3. Automatic Template Processing

The `HTMLi18n` class automatically:
1. Detects user's locale from request context
2. Loads appropriate i18n menu templates
3. Processes `{{key}}` placeholders with translations
4. Replaces English content with translated content using reflection

## Route Handler Integration

### Modern Routes (i18n-enabled)

Routes using `HTMLi18n.create()` automatically support multiple languages:

```java
@Override
public void handle(Context ctx) throws Exception {
    HTMLi18n html = HTMLi18n.create(ctx, false);
    html.append("<h1>").append(html.t("about.title")).append("</h1>");
    html.footer();
    ctx.html(html.get());
}
```

**Updated Routes:**
- `SelfRoute` (main page)
- `HealthRoute` (management)
- `LinksRoute`, `EchoareasRoute`, `UsersRoute`
- `BecomePointRoute`, `BecomeLinkRoute`

### Legacy Routes

Routes still using `HTML.start()` display English-only content and need migration to `HTMLi18n.create()` for full i18n support.

## Character Encoding Handling

### 1. UTF-8 Properties Files

Translation files are stored in UTF-8 format to support Cyrillic, accented characters, and other non-ASCII text.

### 2. Custom ResourceBundle Loading

Java's default `ResourceBundle.getBundle()` expects ISO-8859-1 encoding. The system uses a custom `UTF8Control` class:

```java
private static class UTF8Control extends ResourceBundle.Control {
    @Override
    public ResourceBundle newBundle(String baseName, Locale locale, String format,
                                  ClassLoader loader, boolean reload)
            throws IllegalAccessException, InstantiationException, IOException {
        
        InputStream stream = loader.getResourceAsStream(resourceName);
        if (stream != null) {
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return new PropertyResourceBundle(reader);
            }
        }
        return null;
    }
}
```

### 3. HTTP Response Encoding

The `CharsetFilter` ensures all HTTP responses use UTF-8 encoding:

```java
@Override
public void handle(Context ctx) throws Exception {
    String contentType = ctx.res().getContentType();
    if (contentType == null || contentType.startsWith("text/")) {
        ctx.contentType("text/html; charset=utf-8");
    }
}
```

## Build Configuration

### Maven UTF-8 Support

**Location:** `pom.xml`

```xml
<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
</properties>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-resources-plugin</artifactId>
    <version>3.3.1</version>
    <configuration>
        <encoding>UTF-8</encoding>
    </configuration>
</plugin>
```

This ensures that properties files are copied during build with UTF-8 encoding preserved.

## Usage Examples

### For Developers

**Adding new translation keys:**
1. Add key to all `messages_*.properties` files
2. Use in templates: `{{new.key}}`
3. Access in Java: `html.t("new.key")`

**Creating i18n-enabled routes:**
```java
public class NewRoute implements Handler {
    @Override
    public void handle(Context ctx) throws Exception {
        HTMLi18n html = HTMLi18n.create(ctx, false);
        html.append("<h1>").append(html.t("page.title")).append("</h1>");
        html.footer();
        ctx.html(html.get());
    }
}
```

### For Users

**Switching languages:**
1. Use the language dropdown in the bottom-right corner
2. Or append `?lang=ru` to any URL
3. Language preference persists across sessions
