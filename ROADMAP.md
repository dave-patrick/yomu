# Yomu roadmap

Updated 2026-10-06. This is the current forward-looking roadmap. The original
Android milestones remain in [Pre-development package](YOMU-PRE-DEVELOPMENT-PACKAGE.md).
Planned items below are not claims of implemented behavior.

## Current baseline

- Native Android reading/listening library and private Render web library are implemented.
- Owner verified initial sync and library changes in both directions.
- Web icon/favicon are deployed. The basic web text viewer has no audio or automatic resume capture.
- Android sync direction/interval settings are implemented; broader one-way/conflict/offline
  device verification remains necessary. Browser extension installation/real-page QA remains pending.

## Web reader and settings expansion

High-level architecture and ownership rules: [Web reader design](docs/WEB-READER-DESIGN.md).

| Phase | Planned scope | Completion gate |
|---|---|---|
| 1 — Web reading and local settings | Dedicated responsive reader; font/size/spacing/width; theme; web Settings; local sync interval/direction/manual controls | Comfortable laptop/mobile reading; keyboard access; preferences survive reload; one-way/manual behavior verified |
| 2 — Cross-device resume and shared defaults | Web progress capture; text-revision-aware anchors; shared reader defaults and device overrides; Android compatibility | Resume works in both directions; offline/concurrent edits and old clients tested; playback is not unexpectedly repositioned |
| 3 — Browser listening | Optional browser-native voices; player, highlighting, seek/speed/sleep timer; Up next | Real target-browser tests establish supported behavior and background limits; reading/listening/history remain distinct |
| 4 — Premium speech parity | Secure server credential setup; first approved cloud provider; incremental synthesis, timing, media controls, usage limits | Explicit paid-use opt-in; no exposed keys; real-provider seek/resume/quota/failure and cost checks pass |
| 5 — Later extensions | Evaluate server extraction, PWA/offline reading, web MP3 export, Raindrop two-way integration | Separate scope/design approval and evidence for storage, provider, and hosting requirements |

Sequence phases 1 and 2 before audio. Keep the phone usable throughout; back up data
and test migrations before deploying shared schema changes. Each phase produces a
reviewable web build and an Android build only when Android behavior changes.

## Existing quality work continues

- Production audio lifecycle, calls/headset controls, and Android Auto device QA.
- Sync conflict/offline/deletion coverage, document extraction/upload checks, and recovery backups.
- Release signing, source/license notices, and asset provenance before a public release.

This roadmap authorizes planning only. Implementation, release dates, and additional
provider/hosting costs remain separate decisions.
