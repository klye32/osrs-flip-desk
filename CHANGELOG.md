# Changelog

All notable changes to OSRS Flip Desk are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html) for the `version` field in `runelite-plugin.properties`.

## [Unreleased]

### Fixed
- Mouse wheel scrolling in Flips / Active / History (nested PluginPanel scroll panes were eating wheel events)
- Remove / Clear active confirmation dialogs opening clipped off-screen (now parented to the main RuneLite window)

## [1.1.0] — 2026-08-08

### Added
- Recommended GE flips from public wiki price data with competitive buy/sell targets
- Flip strictness presets (More flips / Balanced / Safer fills)
- Minimum flip profit filter (Any / 10k+ / 50k+ / 200k+)
- Sort by total profit or fill confidence
- Active and History tracking for fills that match recommendations
- Observed 4-hour buy-limit tracking; skips/caps items when remaining limit is low
- Optional tray notifications for tracked buy and sell fills, plus a settings test toggle
- Local per-item fill learning stored in RuneLite config
- Sidebar panel: GP on hand, refresh, profit summary

### Changed
- Simplified configuration around strictness and ticket size (removed many advanced pricing knobs)
- Manual GP on hand only (inventory auto-sync removed)

### Notes
- Does not automate GE clicks, typing, or offer submission
- Market data is requested from `https://prices.runescape.wiki/api/v1/osrs` only

[Unreleased]: https://github.com/klye32/osrs-flip-desk/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/klye32/osrs-flip-desk/releases/tag/v1.1.0
