# DonutMail

A free, open-source mailbox for **Paper**, **Folia** and **Purpur** servers on Minecraft **1.20.1 through 26.3**.

Send items to any player, online or offline. Trade safely with cash on delivery. Reach every player with one campaign, reward logins with a daily streak calendar, hand out reward codes, and deliver rewards from the console or your own plugins, across several servers with MySQL.

**Documentation:** <https://nightbeam.dev/docs/donut-mail> (sources in [`docs/`](docs/)).

## Features

- **Mailbox**: Inbox, Parcels, Sent and Rewards tabs, new-mail markers, item preview, Claim All and paging. Every slot, material and word is configurable (`gui.yml`, `messages.yml`, MiniMessage).
- **Parcels**: drop items into a menu, pick any player (names tab-complete), add a note, send. Recipients are told at once, or with a summary when they join. Unclaimed parcels come back. Senders can take a parcel back until it is claimed.
- **Cash on delivery**: set a price; the recipient previews the exact items and pays to accept, or declines and the items go back. The money reaches the sender as a payment in their mailbox.
- **Campaigns**: `/mail announce` creates one mail for everyone, including players who join later, inside an optional time window. Stored once, claimed once per player. Items, money, commands and vouchers. Optional Discord post.
- **Daily rewards**: a streak calendar (7 days by default) delivered to the Rewards tab on the first join of the day.
- **Reward codes**: `/mail redeem <code>` with total uses, uses per player and expiry.
- **Deliveries**: `/mail give <player|online|all> <hand|item|voucher|money>` from staff and the console, plus vote rewards through NuVotifier.
- **Control**: block players, refuse all parcels, cooldown, daily limit, per-recipient and mailbox limits, item blacklist, optional postage.
- **Notifications**: join summary with a clickable "open mailbox", chat, title, action bar and sound, also for mail sent on another server.
- **Staff tools**: `/mail admin view|info|purge|streak`, mailbox blocks, and an audit log of every send, claim, payment and return.
- **Integrations**: PlaceholderAPI, a developer API with Bukkit events, Vault or VaultUnlocked.
- **Safe by design**: every claim, payment, decline, return and expiry is one conditional update that exactly one action can win, on one server or many. A claim interrupted by a crash is finished or undone on the next join, from a receipt in the player's own save data. Items are never deleted silently.

## Requirements

- Paper, Folia, Purpur or another Paper fork, **1.20.1–26.3** (plain Spigot/Bukkit lacks Paper's API; the plugin says so and disables itself)
- Java **17+** for 1.20.1–1.20.4, **21+** for 1.20.5–1.21.11, **25+** for 26.x (Minecraft's own requirements)
- Optional: Vault or VaultUnlocked with an economy plugin (money features), PlaceholderAPI, NuVotifier, MySQL/MariaDB for several servers

## Installation

1. Put `DonutMail-<version>.jar` in `plugins/`.
2. Restart the server.
3. Edit `plugins/DonutMail/config.yml`, `messages.yml`, `gui.yml` and `daily.yml`, then `/mail reload`.

Upgrading from 1.x: replace the jar and restart; mail (YAML, SQLite or MySQL), vouchers, permissions and settings are kept. See the [2.0.0 notes](DonutMail-2.0.0-PatchNotes.md). Data from PostMaster is still migrated.

## Commands

| Command | Description |
|---|---|
| `/mail` or `/mailbox` | Open the mailbox |
| `/mail send <player> [cod <price>]` | Send a parcel (optionally cash on delivery) |
| `/mail claimall` | Claim everything that fits |
| `/mail redeem <code>` | Redeem a reward code |
| `/mail block <player>` / `unblock` / `blocklist` / `toggle` | Who can send you parcels |
| `/mail give <player\|online\|all> <hand\|item <material> [n]\|voucher <id>\|money <n>> [message]` | Deliver (staff, console) |
| `/mail announce [options] <message>` | Campaign for every player (staff) |
| `/mail campaign list\|end\|delete\|addcommand` | Manage campaigns (staff) |
| `/mail code create\|delete\|list\|addcommand` | Reward codes (staff) |
| `/mail admin view\|info\|purge\|streak\|setblock\|removeblock` | Staff tools |
| `/mail send <targets> <message>`, `/mail voucher send\|sendall` | 1.x staff commands, unchanged |
| `/mail reload` | Reload configuration |

Aliases: `/donutmail`, `/postmaster`. Full list: [docs/commands.md](docs/commands.md).

## Permissions

| Permission | Default | Description |
|---|---|---|
| `donutmail.use` | everyone | Mailbox, claiming, block list, refuse toggle |
| `donutmail.senditem` | everyone | Send parcels |
| `donutmail.cod` | everyone | Cash-on-delivery parcels |
| `donutmail.redeem` | everyone | Reward codes |
| `donutmail.daily` | everyone | Daily rewards |
| `donutmail.bypass.limits` | op | Ignore parcel limits |
| `donutmail.postage.exempt` | op | No postage |
| `donutmail.admin` | op | Staff commands |

The PostMaster-era `postmaster.*` nodes still work.

## PlaceholderAPI

`%donutmail_unread%`, `%donutmail_waiting%`, `%donutmail_parcels%`, `%donutmail_cod%`, `%donutmail_rewards%`, `%donutmail_inbox%`, `%donutmail_campaigns%`, `%donutmail_daily_streak%`, `%donutmail_daily_ready%`, `%donutmail_daily_next%`, `%donutmail_daily_next_day%`, `%donutmail_daily_time%`. See [docs/placeholders.md](docs/placeholders.md).

## Developer API

```java
DonutMailAPI.deliver(Delivery.to(playerId)
        .sender("Crates")
        .message("<gold>Your crate reward")
        .item(new ItemStack(Material.DIAMOND, 3))
        .money(250)
        .rewardsTab()
        .build());
```

Also `sendParcel(...)` (with cash on delivery), `createCampaign(...)`, `endCampaign(...)`, `unread(...)`, `waiting(...)`, `openMailbox(...)`, and the 1.x `sendMail`/`sendVoucher` methods. Events: `MailSendEvent` (cancellable), `MailClaimEvent`, `ParcelPaidEvent`, `ParcelReturnedEvent`, `DailyRewardEvent`, `CodeRedeemEvent`. Add `softdepend: [DonutMail]` to your plugin.yml. See [docs/developer-api.md](docs/developer-api.md).

## Several servers

Set `storage.type: MYSQL` on every server with the same database. Mail sent on one server reaches players on the others within `network.poll-interval-seconds` (3 by default), and claims and payments stay single-winner across servers. See [docs/multi-server.md](docs/multi-server.md).

## Building and testing

```bash
mvn package            # target/DonutMail-<version>.jar, runs the unit tests
```

`mvn test` runs the unit tests and the storage contract on YAML and SQLite, including the races (add
`-Ddonutmail.test.mysql.url=jdbc:mysql://localhost:3306/<db>` with `-Ddonutmail.test.mysql.user`/`.password` for MySQL or MariaDB).
The live matrix on real Paper and Folia servers with network clients is in [`tools/smoke`](tools/smoke/README.md).

Release tagging (Modrinth / CurseForge) uses every loader and Minecraft version in
[`release/supported-minecraft.json`](release/supported-minecraft.json).

## Contributing / Security / License

See [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](.github/SECURITY.md), and [LICENSE](LICENSE) (Apache-2.0).
