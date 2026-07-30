# LoveContracts

Daily contract system for **Paper 1.21** in the style of Witcher 3.

Players accept contracts via GUI (`/contracts`), signs or Citizens NPCs, complete conditions (kill, mine, craft, fish\u2026), receive rewards / penalties. Full race-condition protection, SQLite + HikariCP, MiniMessage, PlaceholderAPI.

## Features (Phase 1)

- Weighted daily rotation (Easy 60% / Medium 30% / Hard 10%)
- REPEATING & ONE_TIME contracts
- Conditions: `KillEntity`, `MineMaterial`, `CatchFish`, `CraftItem`
- 54-slot GUI with tabs
- Transaction-safe accept / complete / fail
- Double-completion & double-fail protection
- Sign integration `[LoveContracts]`
- PlaceholderAPI (`%lovecontracts_daily_completed%` \u2026)
- Vault money rewards/penalties
- Admin commands: reload, rotate, force-complete/fail, diag

## Build

```bash
mvn clean package
```

Requires **Java 21**.

## Commands

| Command | Description |
|---------|-------------|
| `/contracts` | Open contract board |
| `/contracts stats` | Stats (WIP) |
| `/lovecontracts reload` | Reload configs |
| `/lovecontracts rotate` | Force daily rotation |
| `/lovecontracts complete <player> <id>` | Force complete |
| `/lovecontracts fail <player> <id>` | Force fail |
| `/lovecontracts diag` | Diagnostics |

## Soft depends

- PlaceholderAPI
- Vault
- Citizens (planned trait)
- LoveCore (optional deeper integration)

## License

All rights reserved / private use.
