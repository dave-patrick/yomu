# Dedicated website reader

Read opens `/reader.html?id=<item-id>` instead of the old modal. The same browser
access key authenticates the article fetch. Text is displayed as selectable
paragraphs using text nodes; executable markup and unsafe source/return links
are not interpreted. Source access and an end-of-article Library link are visible.

Reading settings provide three font families, seven sizes from 16–32 px, three
spacing choices, three widths, and the existing System/Light/Dark theme. Typography
is saved per browser and can be reset. Shared defaults and audio are separate work.

Library URLs preserve the selected view and search. Return context in session
storage restores scroll and focus in the same tab, including browser Back with a
cached page. Opening and scrolling issue no library writes; they do not mark read
or overwrite Android listening progress. Phone-dependent extraction remains.

Validation: seven web unit tests pass. Isolated Chrome checks with synthetic data
cover route navigation, reload/persisted typography/theme, library search/scroll
restoration, 390 px mobile layout at 32 px text, Escape on reading settings, missing
articles, no console errors, and no mutation requests. Desktop/mobile screenshots
were inspected. Existing backend tests are also run before deployment. Physical
screen-reader testing and cross-device resume are not claimed by these checks.
