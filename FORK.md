# e-kotov/davmail

Personal fork of [mguessan/davmail](https://github.com/mguessan/davmail) (GPL-2.0).
Branch `ek` is upstream `master` at a tested commit, merged in, plus the patches below. `master`
mirrors upstream. Each release's notes name the upstream commit it is built on.

## Patches

- **EWS folder aliases.** `davmail.folderAliases=name=EwsFolderId;name2=EwsFolderId2` exposes
  folders that are shared with you but whose parent folders are not visible to you, so DavMail
  cannot reach them by walking the folder tree. Each alias is served as `calendar/<name>` in your
  own mailbox (for example `/users/you@example.org/calendar/<name>/`), is listed under the
  calendar home set so CalDAV clients discover it, and is treated as a shared folder: hard
  delete, no meeting mail sent from your mailbox, private events excluded.
  EWS folder ids can be read with any EWS client, for example `exchangelib`.
- **Android TLS client.** Android's key managers reject `KeyStoreBuilderParameters`, so
  `DavGatewaySSLSocketFactory` falls back to no client certificates there instead of failing
  every HTTPS connection. Desktop JVMs take the unchanged path. Used by
  [e-kotov/davmail-android](https://github.com/e-kotov/davmail-android).

- **Calendar visibility.** Set either or both exact-name lists:

  ```properties
  # Show everything except the main calendar:
  davmail.calendarsExclude=@default
  ```

  ```properties
  # Show only these aliases, then remove birthdays:
  davmail.calendarsInclude=ram_leave,ram_birthdays,ram_teaching
  davmail.calendarsExclude=ram_birthdays
  ```

  An absent include setting starts with all calendars; an explicitly empty
  `davmail.calendarsInclude=` starts with none. An absent or empty exclude list removes
  nothing. Exclusion wins on overlaps, which produce a warning. With neither setting,
  stock discovery is unchanged. Do not uncomment an empty include example unless you
  mean to hide everything.

  `@default` selects the main calendar. Other selectors are exact top-level subcalendar
  or `folderAliases` names; descendants inherit that top-level selection. Alias names are
  case-sensitive, as in EWS alias lookup; ordinary subfolder names are case-insensitive.
  Wildcards, paths and other reserved `@` names are rejected before the listener binds.
  Unknown names produce a warning after discovery, since folders may be temporarily
  unavailable. A configured alias must not use reserved names or selector delimiters.

  Hidden child calendars are omitted from discovery and reject direct operations, including
  reads, writes and MOVE destinations. The home of a hidden main calendar remains a plain
  collection so clients can still discover visible children; own events are omitted.
  The filter covers only the authenticated user's `/users/<email>/calendar/` namespace.
  Contacts, scheduling inbox/outbox, separate tasks collections, public folders and other
  mailbox URLs are unchanged. It grants no Exchange permissions and deletes no data.
  `davmail.hideDefaultCalendar=true` remains compatible: it adds `@default` to exclusions.
  Refresh calendar discovery in clients after changing the settings; cached collections
  may need to be deselected or removed.

## Releases

Tags are `X.Y.Z-ek.N`, where `X.Y.Z` is the upstream version in `build.xml` at the merged commit
and `N` starts at 1 and never repeats for that version. Pushing a tag runs `.github/workflows/release-ek.yml`, which
builds and runs the focused calendar-policy tests with Ant on Java 21, then publishes `davmail-X.Y.Z-ek.N.zip` (the upstream
platform-independent layout: `davmail`, `davmail.jar`, `lib/`) and `SHA256SUMS`.

To update: test the patch on the new upstream commit, merge that commit into `ek` (no rebase, so
older release tags stay on the branch), then tag the next `X.Y.Z-ek.N`.

History: `7.0.0-ek.1` was based on the upstream `7.0.0` tag. From `7.0.0-ek.2`, `ek` follows
upstream `master`; `7.0.0-ek.2` is on `d452b6d8`, which adds the macOS 27 Calendar fix for
mguessan/davmail#562 (`principal-URL` on the principal).
`7.0.0-ek.3` adds the Android TLS client patch on the same upstream commit.
Upstream's Docker image workflows are removed on this branch.
