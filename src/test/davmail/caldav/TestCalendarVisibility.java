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
import davmail.exchange.ExchangeSession;
import davmail.exchange.ews.EwsExchangeSession;
import davmail.http.HttpClientAdapter;
import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Exercises the production CalDAV routing/serialization with an Exchange-free session. */
public class TestCalendarVisibility extends TestCase {
    private static final String HOME = "/users/me@example.org/calendar/";
    private static final String PROPS = "<d:propfind xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:caldav\">"
            + "<d:prop><d:resourcetype/><d:displayname/><d:getcontenttype/>"
            + "<c:supported-calendar-component-set/></d:prop></d:propfind>";
    private static final String[] KEYS = {"davmail.calendarsInclude", "davmail.calendarsExclude",
            "davmail.hideDefaultCalendar", "davmail.folderAliases"};
    private final Map<String, String> saved = new HashMap<>();

    @Override
    protected void setUp() {
        for (String key : KEYS) {
            saved.put(key, Settings.getRawProperty(key));
            Settings.removeProperty(key);
        }
        Settings.setProperty("davmail.folderAliases", "ram_a=fake-a;ram_b=fake-b");
    }

    @Override
    protected void tearDown() {
        for (String key : KEYS) {
            if (saved.get(key) == null) {
                Settings.removeProperty(key);
            } else {
                Settings.setProperty(key, saved.get(key));
            }
        }
    }

    public void testAbsentEmptyAndReloadedInclude() {
        assertTrue(CalendarVisibility.fromSettings().isVisible("@default"));
        Settings.setProperty("davmail.calendarsInclude", "");
        CalendarVisibility none = CalendarVisibility.fromSettings();
        assertFalse(none.isVisible("@default"));
        assertFalse(none.isVisible("ram_a"));
        assertFalse(none.isVisible("ordinary"));
        Settings.removeProperty("davmail.calendarsInclude");
        Settings.setProperty("davmail.calendarsExclude", "");
        assertTrue(CalendarVisibility.fromSettings().isVisible("ram_a"));
    }

    public void testCombinedListsAndCompatibility() {
        Settings.setProperty("davmail.calendarsInclude", " @default, ram_a,ram_b,ram_a ");
        Settings.setProperty("davmail.calendarsExclude", "ram_b");
        CalendarVisibility policy = CalendarVisibility.fromSettings();
        assertTrue(policy.isVisible("@default"));
        assertTrue(policy.isVisible("ram_a"));
        assertFalse(policy.isVisible("ram_b"));
        assertFalse(policy.isVisible("ordinary"));
        Settings.setProperty("davmail.hideDefaultCalendar", "true");
        assertFalse(CalendarVisibility.fromSettings().isVisible("@default"));
        Settings.removeProperty("davmail.calendarsInclude");
        assertTrue(CalendarVisibility.fromSettings().isVisible("ram_a"));
    }

    public void testExactNamesAndScope() {
        Settings.setProperty("davmail.calendarsInclude", "ram_a,Work");
        CalendarVisibility policy = CalendarVisibility.fromSettings();
        assertTrue(policy.isVisible("ram_a"));
        assertFalse(policy.isVisible("RAM_A"));
        assertTrue(policy.isVisible("work"));
        assertNull(policy.calendarName("/users/other@example.org/calendar/ram_a/", "me@example.org"));
        assertNull(policy.calendarName("/users/me@example.org/calendarOther/", "me@example.org"));
        assertNull(policy.calendarName("/users/me@example.org/tasks/", "me@example.org"));
        assertEquals("@default", policy.calendarName(HOME, "me@example.org"));
        assertEquals("ram_a", policy.calendarName(HOME + "ram_a/nested/event.ics", "me@example.org"));
        assertEquals("Work Calendar", policy.calendarName(HOME + "Work Calendar/", "me@example.org"));
    }

    public void testInvalidSelectorsFailBeforeServerBind() {
        for (String value : Arrays.asList("ram_*", "ram_?", "@personal", "ram_a,,ram_b", "ram_a/sub")) {
            Settings.setProperty("davmail.calendarsInclude", value);
            try {
                new CaldavServer(11080);
                fail("Invalid selector accepted: " + value);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("calendar selector"));
            }
        }
    }

    public void testDiscoveryRetainsHomeAndOmitsExcludedChildren() throws Exception {
        Settings.setProperty("davmail.calendarsInclude", "ram_a,ram_b");
        Settings.setProperty("davmail.calendarsExclude", "ram_b");
        TestConnection connection = new TestConnection();
        String xml = connection.request("PROPFIND", HOME, PROPS, null);
        assertTrue(xml.startsWith("HTTP/1.1 207"));
        assertTrue(xml.contains("<D:href>" + HOME + "</D:href>"));
        assertTrue(xml.contains("<D:href>" + HOME + "ram_a/</D:href>"));
        assertFalse(xml.contains("ram_b"));
        assertEquals(1, count(xml, "<C:calendar/>"));
        assertEquals(0, connection.fake.eventReads);
        assertTrue(new TestConnection().request("GET", HOME + "ram_a/", null, null).contains("BEGIN:VCALENDAR"));
    }

    public void testDefaultHiddenInEveryDiscoveryEntryPoint() throws Exception {
        Settings.setProperty("davmail.calendarsExclude", "@default");
        for (String path : Arrays.asList("/", "/users/me@example.org/")) {
            String xml = new TestConnection().request("PROPFIND", path, PROPS, null);
            assertTrue(xml.startsWith("HTTP/1.1 207"));
            assertFalse(xml.contains("<C:calendar"));
        }
        String xml = new TestConnection().request("PROPFIND", HOME, PROPS, null);
        assertEquals(2, count(xml, "<C:calendar/>"));
    }

    public void testHiddenCalendarAndItemsRejectReadsAndWrites() throws Exception {
        Settings.setProperty("davmail.calendarsExclude", "@default,ram_b");
        for (String path : Arrays.asList(HOME, HOME.substring(0, HOME.length() - 1),
                HOME + "event.ics", HOME + "ram_b/", HOME + "ram_b", HOME + "ram_b/event.ics")) {
            // REPORT and MKCALENDAR address collections. On an item URL, DavMail's
            // parser treats them as a new child collection rather than the item's parent.
            List<String> methods = path.endsWith("event.ics")
                    ? Arrays.asList("GET", "HEAD", "PUT", "DELETE")
                    : Arrays.asList("GET", "HEAD", "PUT", "DELETE", "REPORT", "MKCALENDAR");
            for (String method : methods) {
                TestConnection connection = new TestConnection();
                String body = method.equals("REPORT") ? "<c:calendar-query xmlns:c=\"urn:ietf:params:xml:ns:caldav\"/>"
                        : method.equals("MKCALENDAR") ? "<c:mkcalendar xmlns:c=\"urn:ietf:params:xml:ns:caldav\"/>" : null;
                assertTrue(method + " " + path, connection.request(method, path, body, null).startsWith("HTTP/1.1 404"));
                assertEquals(0, connection.fake.folderReads);
                assertEquals(0, connection.fake.eventReads);
            }
        }
        assertTrue(new TestConnection().request("PROPFIND", HOME + "ram_b/", PROPS, null).startsWith("HTTP/1.1 404"));
    }

    public void testIncludedDefaultCalendarStillAllowsItemRequests() throws Exception {
        Settings.setProperty("davmail.calendarsInclude", "@default");
        try {
            new TestConnection().request("GET", HOME + "event.ics", null, null);
            fail("Expected the fake item backend to be called");
        } catch (IOException expected) {
            assertEquals("item backend", expected.getMessage());
        }
    }

    public void testMovesCannotWriteIntoHiddenCalendars() throws Exception {
        Settings.setProperty("davmail.calendarsExclude", "@default,ram_b");
        for (String destination : Arrays.asList(HOME + "event.ics", HOME + "ram_b/event.ics")) {
            assertTrue(new TestConnection().request("MOVE", HOME + "ram_a/event.ics", null,
                    "https://localhost" + destination).startsWith("HTTP/1.1 404"));
        }
    }

    private static int count(String text, String value) {
        return (text.length() - text.replace(value, "").length()) / value.length();
    }

    private static class MemorySocket extends Socket {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        @Override public InetAddress getInetAddress() { return InetAddress.getLoopbackAddress(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public OutputStream getOutputStream() { return bytes; }
    }

    private static class TestConnection extends CaldavConnection {
        final FakeSession fake;
        final MemorySocket memory;
        TestConnection() throws IOException { this(new MemorySocket()); }
        TestConnection(MemorySocket socket) throws IOException {
            super(socket);
            memory = socket;
            fake = new FakeSession();
            session = fake;
        }
        String request(String method, String path, String body, String destination) throws IOException {
            Map<String, String> headers = new HashMap<>();
            headers.put("depth", "1");
            if (destination != null) { headers.put("destination", destination); }
            handleRequest(method, path, headers, body);
            os.flush();
            return memory.bytes.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static class FakeSession extends EwsExchangeSession {
        int folderReads;
        int eventReads;
        FakeSession() throws IOException { super((HttpClientAdapter) null, "me@example.org"); }
        @Override public void buildSessionInfo(URI uri) { email = "me@example.org"; }
        @Override public boolean isSharedFolder(String path) { return false; }
        @Override public ExchangeSession.Folder getFolder(String path) {
            folderReads++;
            return folder(path);
        }
        private ExchangeSession.Folder folder(String path) {
            ExchangeSession.Folder folder = new ExchangeSession.Folder();
            folder.folderPath = path;
            folder.displayName = path;
            folder.folderClass = path.endsWith("contacts") ? "IPF.Contact" : "IPF.Appointment";
            folder.ctag = "test-ctag";
            folder.etag = "test-etag";
            return folder;
        }
        @Override public List<ExchangeSession.Folder> getSubCalendarFolders(String path, boolean recursive) {
            return Arrays.asList(folder("calendar/ram_a"), folder("calendar/ram_b"));
        }
        @Override public ExchangeSession.Item getItem(String path, String name) throws IOException {
            throw new IOException("item backend");
        }
        @Override public List<ExchangeSession.Event> getAllEvents(String path) {
            eventReads++;
            return Collections.emptyList();
        }
    }
}
