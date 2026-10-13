# e-kotov/davmail

Personal fork of [mguessan/davmail](https://github.com/mguessan/davmail) (GPL-2.0).
Branch `ek` is an upstream release tag plus the patches below; `master` mirrors upstream.

## Patches

- **EWS folder aliases.** `davmail.folderAliases=name=EwsFolderId;name2=EwsFolderId2` exposes
  folders that are shared with you but whose parent folders are not visible to you, so DavMail
  cannot reach them by walking the folder tree. Each alias is served as `calendar/<name>` in your
  own mailbox (for example `/users/you@example.org/calendar/<name>/`), is listed under the
  calendar home set so CalDAV clients discover it, and is treated as a shared folder: hard
  delete, no meeting mail sent from your mailbox, private events excluded.
  EWS folder ids can be read with any EWS client, for example `exchangelib`.

## Releases

Tags are `X.Y.Z-ek.N`, where `X.Y.Z` is the upstream release the `ek` branch is based on and `N`
starts at 1 and never repeats. Pushing a tag runs `.github/workflows/release-ek.yml`, which
builds with Ant on Java 21 and publishes `davmail-X.Y.Z-ek.N.zip` (the upstream
platform-independent layout: `davmail`, `davmail.jar`, `lib/`) and `SHA256SUMS`.

To update: rebase `ek` onto the new upstream tag, then tag `X.Y.Z-ek.1`.
Upstream's Docker image workflows are removed on this branch.
