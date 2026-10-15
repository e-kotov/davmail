/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2009  Mickael Guessant
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package davmail.caldav;

import davmail.Settings;
import org.apache.log4j.Logger;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Exact-name visibility filter for the authenticated user's calendar home. */
final class CalendarVisibility {
    static final String DEFAULT = "@default";
    private static final Logger LOGGER = Logger.getLogger(CalendarVisibility.class);
    private final Set<String> include;
    private final Set<String> exclude;
    private final Set<String> aliases;
    private boolean warnedUnknownNames;

    private CalendarVisibility(String included, String excluded, boolean hideDefault, Set<String> aliases) {
        this.aliases = aliases;
        include = included == null ? null : parse(included);
        exclude = parse(excluded);
        if (hideDefault) {
            exclude.add(DEFAULT);
        }
        if (include != null) {
            for (String name : include) {
                if (matches(exclude, name)) {
                    LOGGER.warn("Calendar '" + name + "' is included and excluded; exclusion wins.");
                }
            }
        }
    }

    static CalendarVisibility fromSettings() {
        Set<String> aliases = new LinkedHashSet<>();
        String configuredAliases = Settings.getProperty("davmail.folderAliases", "");
        for (String entry : configuredAliases.split(";")) {
            int separator = entry.indexOf('=');
            if (separator > 0) {
                String name = entry.substring(0, separator).trim();
                validateName(name, false);
                aliases.add(name);
            }
        }
        return new CalendarVisibility(Settings.getRawProperty("davmail.calendarsInclude"),
                Settings.getRawProperty("davmail.calendarsExclude"),
                Settings.getBooleanProperty("davmail.hideDefaultCalendar", false), aliases);
    }

    private static Set<String> parse(String value) {
        Set<String> names = new LinkedHashSet<>();
        if (value != null && !value.trim().isEmpty()) {
            for (String part : value.split(",", -1)) {
                String name = part.trim();
                validateName(name, true);
                names.add(name);
            }
        }
        return names;
    }

    private static void validateName(String name, boolean selector) {
        if (selector && DEFAULT.equals(name)) {
            return;
        }
        if (name.isEmpty() || name.startsWith("@") || name.contains("/")
                || name.contains(",") || name.contains("*") || name.contains("?")) {
            throw new IllegalArgumentException("Invalid calendar selector '" + name
                    + "': use an exact top-level calendar name or @default; wildcards are not supported.");
        }
    }

    boolean isVisible(String name) {
        return (include == null || matches(include, name)) && !matches(exclude, name);
    }

    private boolean matches(Set<String> selectors, String name) {
        if (DEFAULT.equals(name) || aliases.contains(name)) {
            return selectors.contains(name);
        }
        for (String selector : selectors) {
            // Alias identifiers keep the case-sensitive semantics of EWS alias lookup.
            if (!aliases.contains(selector) && !DEFAULT.equals(selector) && selector.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** Paths are already decoded by CaldavConnection; descendants inherit their top-level calendar. */
    String calendarName(String path, String email) {
        String root = "/users/" + email + "/calendar";
        if (path == null || !path.regionMatches(true, 0, root, 0, root.length())) {
            return null;
        }
        String rest = path.substring(root.length());
        if (rest.isEmpty() || rest.equals("/")) {
            return DEFAULT;
        }
        if (!rest.startsWith("/")) {
            return null;
        }
        rest = rest.substring(1);
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    synchronized void warnUnknownNames(Collection<String> discovered) {
        if (warnedUnknownNames) {
            return;
        }
        warnedUnknownNames = true;
        Set<String> selected = new LinkedHashSet<>(exclude);
        if (include != null) {
            selected.addAll(include);
        }
        Set<String> available = new LinkedHashSet<>(aliases);
        available.addAll(discovered);
        available.add(DEFAULT);
        for (String name : selected) {
            boolean found = false;
            for (String candidate : available) {
                if (candidate != null && matches(java.util.Collections.singleton(name), candidate)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                LOGGER.warn("Configured calendar '" + name + "' was not discovered; check its name or Exchange availability.");
            }
        }
    }
}
