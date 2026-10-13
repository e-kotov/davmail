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

## Releases

Tags are `X.Y.Z-ek.N`, where `X.Y.Z` is the upstream version in `build.xml` at the merged commit
and `N` starts at 1 and never repeats for that version. Pushing a tag runs `.github/workflows/release-ek.yml`, which
builds with Ant on Java 21 and publishes `davmail-X.Y.Z-ek.N.zip` (the upstream
platform-independent layout: `davmail`, `davmail.jar`, `lib/`) and `SHA256SUMS`.

To update: test the patch on the new upstream commit, merge that commit into `ek` (no rebase, so
older release tags stay on the branch), then tag the next `X.Y.Z-ek.N`.

History: `7.0.0-ek.1` was based on the upstream `7.0.0` tag. From `7.0.0-ek.2`, `ek` follows
upstream `master`; `7.0.0-ek.2` is on `d452b6d8`, which adds the macOS 27 Calendar fix for
mguessan/davmail#562 (`principal-URL` on the principal).
Upstream's Docker image workflows are removed on this branch.
