# Save to Yomu — Chrome extension

Personal unpacked extension, version 0.2.0. Not published to the Chrome Web Store.

## Install

1. Open chrome://extensions and turn on Developer mode.
2. Choose Load unpacked and select this extension folder (not its parent).
3. Pin Save to Yomu from Chrome's Extensions menu.
4. Open its popup, enter your private Yomu access key, and click Connect.
   The website is prefilled with https://yomu-fmze.onrender.com.
5. Approve access to that Yomu server when Chrome asks.

Open a public article page, choose To read only or an existing playlist, and click
Save to Yomu. Playlist saves also keep the item in To read. Create/manage playlists
on the website or phone. Existing saves are deduplicated by URL; fragments are
removed, query parameters preserved. Open your library links to the website.

Appearance offers System, Light, and Dark, saved for this Chrome profile.

## Privacy and limits

Only the active page URL/title are read after you open the extension. No content
scripts, browsing-history access, page text extraction, or provider credentials.
The private access key is in profile-local storage restricted to trusted extension
contexts. Disconnect removes it and revokes the selected server permission.
Only the user-selected HTTPS server gets optional persistent website access.
Your phone still extracts article text and syncs it back. Saving does not start audio.
Keep the popup open until it confirms a save. If closed early, reopen and retry;
server URL deduplication prevents a duplicate article. A saved article can remain
in To read if playlist addition fails; retry the selected playlist to complete it.

## Validation

JavaScript syntax and manifest/assets checked locally. Real Chrome installation,
host permission prompts, key validation, light/dark/system themes, save/duplicate,
playlist membership, and phone sync must be verified in the target browser.
For updates, click Reload on this extension in chrome://extensions.

Automated popup checks: run node extension/tests/popup.test.cjs from the repository root.
Tests cover new/duplicate saves, playlist membership, partial failure, URL normalization, and invalid server addresses.

