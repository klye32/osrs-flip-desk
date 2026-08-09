# OSRS Flip Desk

RuneLite sidebar plugin that suggests **affordable Grand Exchange flips** from public wiki market data, then tracks your fills into Active / History.

It does **not** automate gameplay. It does not click the GE, type prices, or submit offers for you.

## Features

- Competitive buy / sell targets from recent completed trades (not raw last-low as a live ask)
- Position sizing from GP on hand, remaining 4-hour buy limit, and recent two-sided volume
- Minimum flip profit filter, strictness presets, and sort by total profit or fill confidence
- Auto-tracks recommended buys into **Active**, matches sells FIFO, and records realized profit
- Optional tray notifications on tracked buy / sell fills
- Per-item fill learning stored only in local RuneLite config

## Install

Once accepted on the Plugin Hub: open RuneLite → Plugin Hub → search **OSRS Flip Desk** → install.

For local development: Java 11, then `./gradlew run` (or `gradlew.bat run` on Windows).

## Settings

- **GP on hand** — cash you want recommendations sized for (also editable in the side panel)
- **Flip strictness** — More flips / Balanced / Safer fills
- **Minimum flip profit** — hide small tickets (10k+ / 50k+ / 200k+)
- **Sort flips by** — total profit or fill confidence
- **Buy / sell fill notifications** — RuneLite tray alerts for tracked fills

## Privacy

Market requests go to the public OSRS wiki prices API:

`https://prices.runescape.wiki/api/v1/osrs`

The plugin does **not** send your account name, GP amount, GE offers, or learned fill data to that service. Tracking data stays in local RuneLite configuration.

## Important limitation

There is no public GE order book. Prices are estimates from recent trades and your observed fills, not guaranteed executable quotes.

## License

BSD 2-Clause. See [LICENSE](LICENSE).

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for release notes.
