# Reward codes

Codes for events, streams, newsletters and apologies. Players type `/mail redeem <code>` (or click **Redeem a Code** on the Rewards tab) and the reward arrives in their Rewards tab.

## Creating a code

```
/mail code create <code> [options] [message]
```

| Option | Meaning | Default |
| --- | --- | --- |
| `uses:<n>` | Total redemptions | unlimited |
| `per-player:<n>` | Redemptions per player | 1 |
| `expires:<when>` | `7d`, `2026-12-31` or `2026-12-31T23:59` | never |
| `money:<amount>` | Money paid through Vault when claimed | |
| `voucher:<id>` | A voucher | |
| `hand` | A copy of the item in your hand (in game) | |

Codes use letters, digits, `-` and `_` (3 to 32 characters) and are not case-sensitive.

```
/mail code create SPRING uses:2 per-player:1 money:50 Spring reward
/mail code addcommand SPRING lp user %player% parent add supporter
```

## Limits that hold

Each redemption counts the use (total and per player) and saves the reward in one transaction. In the tests, 10 players redeeming a code with 3 uses at the same moment got exactly 3 rewards, and one player redeeming a one-per-player code from 6 threads at once got exactly 1 (on YAML, SQLite and MariaDB). `codes.attempt-cooldown-seconds` (3 by default) slows down guessing.

## Managing

| Command | What it does |
| --- | --- |
| `/mail code list` | Codes with uses, limits and expiry |
| `/mail code delete <code>` | Delete a code |
| `/mail code addcommand <code> <command>` | Add a reward command |

Codes are shared between servers on one MySQL database.
