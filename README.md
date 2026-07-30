# LoveContracts

Contract systems for **Paper 1.21**: a daily NPC contract board in the style of Witcher 3,
plus player-to-player hiring contracts backed by LoveCore's escrow.

## 1. Daily NPC contract board (`/contracts`)

Players accept contracts via GUI, signs or Citizens NPCs, complete conditions (kill, mine, craft, fish…), receive rewards / penalties. Full race-condition protection, SQLite + HikariCP, MiniMessage, PlaceholderAPI.

### Features (Phase 1)

- Weighted daily rotation (Easy 60% / Medium 30% / Hard 10%)
- REPEATING & ONE_TIME contracts
- Conditions: `KillEntity`, `MineMaterial`, `CatchFish`, `CraftItem`
- 54-slot GUI with tabs
- Transaction-safe accept / complete / fail
- Double-completion & double-fail protection
- Sign integration `[LoveContracts]`
- PlaceholderAPI (`%lovecontracts_daily_completed%` …)
- Item / XP / reputation rewards & penalties
- Admin commands: reload, rotate, force-complete/fail, diag

> **Known issue:** `ContractManager`, `ContractRegistry`, and `LoveContractsAdminCommand` are
> referenced by `LoveContracts.java` but missing from the tree, so this module currently does
> not compile. Pre-existing, tracked separately from the player-contract work below.

### Commands

| Command | Description |
|---------|-------------|
| `/contracts` | Open contract board |
| `/contracts stats` | Stats (WIP) |
| `/lovecontracts reload` | Reload configs |
| `/lovecontracts rotate` | Force daily rotation |
| `/lovecontracts complete <player> <id>` | Force complete |
| `/lovecontracts fail <player> <id>` | Force fail |
| `/lovecontracts diag` | Diagnostics |

## 2. Player-to-player contracts (`/pcontract`)

Players hire each other instead of only pulling jobs from the NPC board: post a job (deliver
items, kill targets, or a free-form custom task) with a gold + item reward, someone else
accepts it, reward and escrow settle automatically on completion, cancellation, or expiry.

### Features

- **Create**: `/pcontract create <deliver|kill|custom> ...` with free-form flags (`withitem`,
  `clanonly`, `rep:N`) instead of a rigid argument list.
- **Escrow**: gold is withdrawn from the creator via LoveCore's `LoveEconomy` ledger
  (`AccountId`-based — settles for offline players too) on creation, paid out to the executor
  on completion, refunded to the creator on cancel/expire.
- **Objectives**: `DELIVER_ITEM` (verified via `/pcontract turnin`), `KILL_ENTITY` (tracked
  live via `EntityDeathEvent`), `CUSTOM` (executor submits, creator reviews accept/reject).
- **Item rewards** need a live inventory to deliver — they queue and land on next login if the
  executor is offline at settlement time.
- **Soft integrations**: reputation gates and clan-only visibility go through LoveCore's
  `ReputationOracle`/`ProfileOracle` — works with LoveCore absent, just without those checks
  (zero-gold contracts work with no LoveCore at all).
- **Events**: fires `PlayerContractCreated/Accepted/Completed/EndedEvent` so other plugins
  (LoveBehavior for reputation, LoveLeaderboards for stats) can react independently.
- Every limit (escrow bounds, reputation gates, deadlines, creation tax, per-player caps)
  lives under `player-contracts:` in `config.yml`.

### Commands

| Command | Description |
|---------|-------------|
| `/pcontract board` | Browse open contracts and accept one |
| `/pcontract my` | Your contracts, as creator or executor |
| `/pcontract create deliver <material> <amount> <gold> <hours> [flags] <description>` | Post a delivery contract |
| `/pcontract create kill <entity> <amount> <gold> <hours> [flags] <description>` | Post a kill contract |
| `/pcontract create custom <gold> <hours> [flags] <description>` | Post a free-form contract |
| `/pcontract accept <id>` | Accept an open contract |
| `/pcontract turnin <id>` | Turn in items for a delivery contract |
| `/pcontract submit <id>` | Submit a custom contract for review |
| `/pcontract review <id> accept\|reject` | Creator reviews a submitted custom contract |
| `/pcontract cancel <id>` | Cancel (creator) or abandon (executor) |
| `/pcontract info <id>` | Contract details |

## Build

```bash
mvn clean package
```

Requires **Java 21**.

## Soft depends

- PlaceholderAPI
- Citizens (planned trait)
- LoveCore (required for gold escrow on `/pcontract`; optional deeper integration for `/contracts`)

## License

All rights reserved / private use.
