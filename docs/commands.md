# Commands

`/mail` and `/mailbox` open the mailbox. `/donutmail` and `/postmaster` are aliases of `/mail`.

## Players

| Command | What it does | Permission |
| --- | --- | --- |
| `/mail` | Open the mailbox (Inbox tab) | `donutmail.use` |
| `/mail inbox` / `parcels` / `sent` / `rewards` | Open a tab directly | `donutmail.use` |
| `/mail send <player>` | Write a parcel to a player, online or offline | `donutmail.senditem` |
| `/mail send <player> cod <price>` | A parcel with cash on delivery (`1k`, `2.5m` work) | `donutmail.senditem` + `donutmail.cod` |
| `/mail cod <player> <price>` | The same, shorter | `donutmail.senditem` + `donutmail.cod` |
| `/mail <player>` | Shortcut for `/mail send <player>` | `donutmail.senditem` |
| `/mail claimall` | Claim everything that fits in your inventory | `donutmail.use` |
| `/mail redeem <code>` | Redeem a reward code | `donutmail.redeem` |
| `/mail block <player>` / `unblock <player>` | Refuse or accept parcels from one player | `donutmail.use` |
| `/mail blocklist` | Who you blocked | `donutmail.use` |
| `/mail toggle` | Refuse or accept all parcels | `donutmail.use` |
| `/mail help` | Command list | |

Player names tab-complete for online players and for every player DonutMail has seen, so offline players are easy to reach.

## Staff

All need `donutmail.admin`. They work from the console too, except the ones marked "in game".

| Command | What it does |
| --- | --- |
| `/mail give <target> hand [message]` | Send a copy of the item in your hand (in game) |
| `/mail give <target> item <material> [amount] [message]` | Send items |
| `/mail give <target> voucher <id> [message]` | Send a voucher |
| `/mail give <target> money <amount> [message]` | Send money, paid through Vault when claimed |
| `/mail send <targets> <message>` | Text mail |
| `/mail voucher send <id> <targets>` / `sendall <id>` | Send a voucher (1.x commands, unchanged) |
| `/mail announce [options] <message>` | A campaign: one mail for everyone, claimed once each. See [Campaigns](campaigns.md) |
| `/mail campaign list` / `end <name>` / `delete <name>` / `addcommand <name> <command>` | Manage campaigns |
| `/mail code create <code> [options] [message]` | A reward code. See [Reward codes](codes.md) |
| `/mail code list` / `delete <code>` / `addcommand <code> <command>` | Manage codes |
| `/mail admin view <player>` | See a player's mail; right-click to inspect, shift-right-click to remove (in game) |
| `/mail admin info` | Version, server, storage, hooks and counts |
| `/mail admin purge <days>` | Delete finished mail older than that |
| `/mail admin streak <player> <days>` | Set a daily streak, for example after an outage (0 starts over) |
| `/mail admin setblock` / `removeblock` | Make the block you look at a mailbox, or not (in game) |
| `/mail reload` | Reload config.yml, messages.yml, gui.yml, daily.yml and vouchers |

**Targets** for `give` and `send`: a player name or UUID, `name;name;name`, `online` (everyone online now) or `all` (every player known before now, stored once as a campaign instead of one mail per player).
