# Newsletter forwarding milestone

Implemented 2026-10-07. Website Settings links to Newsletters. The first usable flow is uploading an .eml sample, previewing safe text, choosing an existing playlist, and explicitly enabling a sender/List-ID rule. New matching deliveries become ready items in To read and that playlist without phone extraction, playback, or Up next changes.

## Delivered

- Plain-text/HTML email bodies, attached .eml forwards, and advisory identity detection for common English inline forwards. Original sender is shown for owner review; forwarded headers are not authentication.
- Safe text preview: scripts/forms/tracking images are not rendered or fetched. HTTP(S) links remain readable text. Attachments and automatic website crawling are excluded.
- One destination per newsletter, exact sender fallback, normalized List-ID matching, rule pause/enable, and destination edits affecting future deliveries only. Existing playlists can be renamed without breaking their ID mapping. Create new playlists from the library first.
- Unmatched/ambiguous messages need a routing decision. Similar content with a different message identity requires review. Failed jobs offer retry; unwanted pending deliveries can be dismissed.
- SQLite-backed queue and bounded same-process background worker on the current persistent disk. A provider event is acknowledged only after durable acceptance. Each import/savepoint and its playlist membership are atomic; interruption rolls back before retry.
- Provider-event and original Message-ID ledgers survive replay and library deletion. Deleted imported issues are not automatically recreated. Pending content expires after seven days; imported preview bodies are removed after seven days while issue metadata and identity remain. Normal Yomu content remains in the library.
- Limits: 2 MB incoming request, 500 KB UTF-8 extracted body, 100 unresolved deliveries, up to 20 jobs per pass, three transient attempts, latest 100 deliveries with attention states first.
- A Postmark receiving adapter at `/hooks/newsletters/postmark`, disabled unless a separate webhook secret is configured. Postmark uses HTTPS Basic authentication, not a signed-payload mechanism. This is a provider-specific adjustment to the initial signed-webhook design; ordinary Yomu browser keys do not authorize the hook.
- Android alpha21 identifies newsletter bodies in article tools and does not offer a nonexistent original-file download. No Room migration or provider keys are required for this adaptation; newsletter items use existing ready text and sync records.

## External receiving setup still required

The owner selected AgentMail and created an inbox. Live webhook connection and real receipt remain pending; no paid provider plan has been enabled. .eml upload and routing work independently. Live email arrival is unverified until a real provider account is connected and a real newsletter is forwarded.

AgentMail is now the selected provider; see [AgentMail setup](AGENTMAIL-SETUP.md). The original Postmark adapter remains an alternative. Official setup: https://postmarkapp.com/developer/user-guide/inbound/configure-an-inbound-server

Configure the existing Render service with:

- `YOMU_NEWSLETTER_ADDRESS`: the actual inbound stream address.
- `YOMU_NEWSLETTER_WEBHOOK_SECRET`: a unique random secret of at least 32 characters; generate and store it privately, never in Git or chat.

Use Basic authentication username `yomu` and that separate secret on the provider callback to `https://yomu-fmze.onrender.com/hooks/newsletters/postmark`. The provider's setup accepts credentials in its callback URL; configure that privately in the account UI. Never paste the credential-bearing URL into chat, documentation, or source. No Postmark sending/server API token is needed by Yomu for receiving through this adapter.

Forward an issue, verify its detected original newsletter identity in Preview, select a playlist, and enable the rule. Forward a second issue to verify automatic routing and sync to the phone. Mail-provider forwarding verification emails must be reviewed manually; Yomu does not follow confirmation links automatically.

Provider delivery/authentication, recipient-address rotation, connection deletion, rule removal, custom subject filters, OAuth mailbox capture, bounded historical backfill, and physical phone listening/sync acceptance remain later work. Pausing a rule leaves incoming issues reviewable; it does not stop the external provider receiving them.

## Validation

- 21 backend tests passed: nine existing sync tests plus 12 newsletter tests, including HTML safety, replay/tombstones, ambiguous duplicates, rule conflicts, deleted destinations, paused rules, transaction rollback, restart recovery, Basic authentication, attached/inline forwards, and retention.
- Isolated real FastAPI/SQLite backend with headless Chrome passed sample upload, preview, rule save, second-issue automatic routing, ready library records, membership, no playback/queue mutation, no console errors, and narrow-screen overflow checks. Light/dark desktop/mobile captures were inspected in bounded passes.
- Android alpha21 built successfully, was installed in place over local Wi-Fi, and version/launch were confirmed. Real forwarded-mail and physical newsletter playback acceptance remain open.
- Fixture servers/data were isolated from the production library. No synthetic issues were written to the live service.

## AgentMail follow-up

Signed AgentMail intake has been implemented with Svix verification, a single allowed inbox, fixed-host bounded API recovery for omitted bodies/headers, and existing durable ingestion/routing. Spam, blocked, and unauthenticated event types are ignored. Failed provider retrieval returns 503 for provider retry rather than acknowledging a lost issue. See [setup and acceptance](AGENTMAIL-SETUP.md).
