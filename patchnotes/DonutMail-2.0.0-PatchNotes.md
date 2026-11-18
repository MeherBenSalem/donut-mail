# DonutMail v2.0.0 — The Post Office Update

## New

- **A new mailbox.** Inbox, Parcels, Sent and Rewards tabs, new-mail markers, an item preview before you claim, Claim All and paging. Every slot, material and word can be changed in `gui.yml` and `messages.yml` (MiniMessage, `&` codes and `&#RRGGBB` hex). Menu text is no longer italic.
- **Parcels between players.** `/mail send <player>` (or the Send a Parcel button) opens a menu: drop the items in, pick any player online or offline (names tab-complete), add a note, send. The recipient is told at once, on any server of a network, or with a summary when they join. Senders follow their parcels on the Sent tab and can take one back until it is claimed. Parcels nobody claims return to the sender after 7 days.
- **Cash on delivery.** Set a price when sending (`/mail send <player> cod 250` or the gold nugget in the menu). The recipient previews the exact items, then pays to accept or declines and the items go back. The money reaches the sender as a payment in their mailbox. Optional tax.
- **Campaigns.** `/mail announce` sends one mail to every player, including players who join later, with an optional start and end time and permission. It is stored once and each player claims it once. Items, money, console commands and vouchers; an optional Discord post. `/mail campaign list|end|delete|addcommand`.
- **Daily rewards.** A streak calendar in `daily.yml` (7 days by default: items, money, commands, vouchers) delivered to the Rewards tab when a player joins on a new day, and shown as a calendar. Missing a day restarts the streak (configurable). `/mail admin streak <player> <days>` restores a streak.
- **Reward codes.** `/mail redeem <code>`; staff create codes with total uses, uses per player and an expiry: `/mail code create SPRING uses:2 per-player:1 money:50`.
- **Deliveries for staff and the console.** `/mail give <player|online|all> <hand|item <material> [amount]|voucher <id>|money <amount>> [message]`. `all` is stored once as a campaign for every known player. Vote rewards through NuVotifier.
- **Control over your mailbox.** `/mail block|unblock|blocklist` and `/mail toggle` to refuse all parcels. Server limits: a cooldown, parcels per day, parcels per recipient, mailbox capacity and an item blacklist. Optional postage.
- **Notifications.** A join summary with a clickable "open mailbox", and live chat, title, action bar and sound notices when mail arrives, also from another server.
- **Staff tools.** `/mail admin view <player>` (inspect and remove), `/mail admin info`, `/mail admin purge <days>`, mailbox blocks (`/mail admin setblock`: right-click a block at spawn to open the mailbox), and an audit log with a line for every send, claim, payment, return, expiry, removal and redemption in `plugins/DonutMail/logs/`.
- **PlaceholderAPI.** `%donutmail_unread%`, `_waiting%`, `_parcels%`, `_cod%`, `_rewards%`, `_inbox%`, `_campaigns%`, `_daily_streak%`, `_daily_ready%`, `_daily_next%`, `_daily_next_day%`, `_daily_time%`.
- **Developer API and events.** `DonutMailAPI.deliver(Delivery)` (items, money, commands, vouchers, to the Inbox or the Rewards tab), `sendParcel(...)` with cash on delivery, `createCampaign(...)`, `endCampaign(...)`, `unread(...)`, `waiting(...)`, `openMailbox(...)`. Events: `MailSendEvent` (cancellable, with a message for the sender), `MailClaimEvent`, `ParcelPaidEvent`, `ParcelReturnedEvent`, `DailyRewardEvent`, `CodeRedeemEvent`. The 1.x methods work unchanged.
- **Several servers.** With one MySQL or MariaDB database, mail sent on one server reaches players on the others, and campaigns, codes and daily rewards are shared.

## Safety

- Every claim, payment, decline, return, expiry, recall and staff removal is one conditional update in storage, so exactly one action wins: two clicks, two players or two servers can never both take the same parcel or pay for it twice. Changes that also create a mail (a receipt, a returned parcel, a campaign claim, a daily or code reward) are saved in one transaction.
- A claim interrupted by a crash is finished or undone the next time the player joins, decided by a receipt written into the player's own save data in the same tick as the items. Nothing is lost or doubled either way.
- Items are never deleted silently: expired, declined, recalled and staff-removed parcels go back to the sender; items that don't fit stay in the mailbox; items left in the send menu go back to the player or their mailbox. Nothing is dropped on the ground unless you allow it.
- Folia: inventories change on the player's own thread and nowhere else; commands and economy calls run on the global thread.

## Fixes

- Stopping the server no longer rewrites the whole mail table. 1.x deleted and re-inserted every row on each stop, which could overwrite mail saved meanwhile by another server on a shared MySQL database.
- A mail whose items did not all fit could not be claimed at all; now the stacks that fit are handed over and the rest stays.
- Items in the send menu are no longer dropped on the ground when the inventory is full.
- SQLite storage did not start on Paper 26.3 in 1.1.0 ("Failed to get driver instance"). The database driver is now loaded explicitly, for SQLite and MySQL.

## Supported versions

- Paper and Folia **1.20.1 through 26.3** from one jar (Purpur and other Paper forks too). 1.20.1 is the lowest version: DonutMail is built against the Paper 1.20.1 API (Java 17), which already has everything it needs (region schedulers for Folia, Adventure text, versioned item storage), it matches the rest of the Donut plugins, and it is tested live on Paper and Folia 1.20.1. Older versions are not tested and not supported.
- Java **17** or newer for the plugin. Minecraft itself needs Java 21 from 1.20.5 and Java 25 for 26.x.
- Spigot and plain Bukkit are not supported (no Paper API); the plugin says so and disables itself.

## Upgrade and data

- Replace the jar and restart. Mail stored by 1.x is read as is, on the same storage (YAML, SQLite or MySQL), and is listed and claimable. Vouchers, permissions (`donutmail.*` and the PostMaster-era `postmaster.*`) and settings are kept. Back up `plugins/DonutMail` before updating, as always.
- The texts 1.x kept in `config.yml` move to `messages.yml`; `mail.page-size` is gone (the layout is in `gui.yml`). New settings are added to your files automatically.
- New installs use SQLite. SQLite now uses the driver that ships with Paper, so it works on every operating system; the jar is about 1.9 MB (1.1.0: 4.1 MB).
- Items are stored in Paper's versioned binary format, which survives Minecraft upgrades. Items 1.x stored as YAML objects are converted the moment they are loaded.
- PostMaster data is still migrated when DonutMail starts with an empty data folder.

## Verification

**91 automated tests** (`mvn test`). One storage contract suite runs on YAML (24 tests), SQLite (23) and MariaDB 10.11 (24): every state change, the 1.1.0 tables and files, and races in which 6 to 10 attempts at the same claim, payment, decline, expiry, take-back, campaign claim, daily delivery or code redemption start at the same moment (on MariaDB from two connection pools, like two servers). Exactly one wins, every time. With the state check removed from two of the updates, the race tests fail (5 failures on SQLite, 5 on MariaDB), so they catch a broken update. Also: amounts and durations, MiniMessage escaping, the daily streak, config merging and the Discord payload.

**Live tests** with [mineflayer](https://github.com/PrismarineJS/mineflayer) bots over the network protocol, on server jars from PaperMC's download API (SHA-256 checked), with PlaceholderAPI 2.12.3, all on the release jar:

| Server (build) | Java | Bot client | Economy | Storage | Checks | DonutMail errors |
| --- | --- | --- | --- | --- | --- | --- |
| Paper 1.20.1 (196) | Temurin 17.0.21 | 1.20.1 | Vault 1.7.3 + EssentialsX 2.22.0 | YAML | 41/41 | 0 |
| Folia 1.20.1 (17) | Temurin 17.0.21 | 1.20.1 | VaultUnlocked 2.20.3 + test economy | YAML | 41/41 | 0 |
| Paper 1.21.11 (132) | OpenJDK 21.0.12 | 1.21.11 | Vault 1.7.3 + EssentialsX 2.22.0 | SQLite | 45/45 | 0 |
| Folia 1.21.11 (14) | OpenJDK 21.0.12 | 1.21.11 | VaultUnlocked 2.20.3 + test economy | SQLite | 41/41 | 0 |
| Paper 26.3 (244) | Temurin 25.0.5 | 26.1 through ViaVersion + ViaBackwards 5.12.2 | Vault 1.7.3 + EssentialsX 2.22.0 | MariaDB | 41/41 | 0 |
| Folia 26.3 (6) | Temurin 25.0.5 | 26.1 through ViaVersion + ViaBackwards 5.12.2 | VaultUnlocked 2.20.3 + test economy | SQLite | 41/41 | 0 |
| 2 × Paper 1.21.11 (132) | OpenJDK 21.0.12 | 1.21.11 | VaultUnlocked 2.20.3 + test economy | one MariaDB database | 6/6 | 0 |
| Paper 26.3 (244), 1.1.0 → 2.0.0 | Temurin 25.0.5 | 26.1 through ViaVersion + ViaBackwards | VaultUnlocked 2.20.3 | YAML | 6/6 | 0 |

Every single-server run:

- daily reward day 1 on joining, claimed from the Rewards tab (16 iron);
- a parcel sent through the menu and claimed by an online player; a parcel to an offline player, the clickable join summary, and the claim after joining;
- cash on delivery: preview and pay 250 (buyer 1000 → 750, items delivered, sender told, 250 collected from the payment mail); decline (items back to the sender, buyer not charged); a double click on Pay charged once; pay and take-back clicked by two players at the same moment (one winner: across the six runs the take-back won 3 times and the payment 3 times, never both); recipient, note and price set through the menu's chat prompts (75);
- a full inventory with 2 free slots and 5 swords: 2 delivered, 3 kept, the rest claimed later; Claim All;
- a campaign broadcast and claimed once (+100); a code with 2 uses and 1 per player (OK, already redeemed, OK, used up) by command and through the Rewards tab; console `/mail give` to a player, to everyone online and to `all`;
- block list and refuse toggle (items stay with the sender); cooldown and the per-recipient limit (3);
- an unanswered cash-on-delivery parcel with expiry set to about 104 seconds came back to the sender 107 to 113 seconds after sending;
- 40 letters across two pages, `/mail send Al<tab>` completing Alice, PlaceholderAPI values, `/mail admin info`, a lectern as mailbox block, staff view and removal, `/mail reload`, the API events;
- a restart: mail unchanged, items in a parcel menu open during the stop kept in the owner's mailbox, a claimed campaign not offered again while a new player can claim it, and a streak set to 2 continuing with day 3 (32 bottles).

On Paper 1.21.11 also: a NuVotifier 2.7.3 vote (protocol v1) became a reward; a campaign was posted to a webhook endpoint; and the server was stopped dead right after a delivery, before the claim was saved, twice: without the player's data saved the claim was undone and could be claimed again (2 blocks once), with the player's data saved the claim was finished on the next join (2 blocks, nothing left to claim).

Two servers on one MariaDB database: a parcel from one server reached a player on the other with a live notice after 2.0 seconds (2,004 ms; polling every second) and postage of $6 (5 + 1 per stack); pay on one server against take-back on the other, 3 rounds in each of two runs: one winner every round (payment 3 of 3 in one run, take-back 3 of 3 in the other); a campaign claimed once per player across both; one daily reward per player per day.

Upgrade: DonutMail 1.1.0 on Paper 26.3 with YAML storage got an item mail through its own menu, a text mail and a voucher; with 2.0.0 swapped in, all three were listed and claimed (3 diamonds), `config.yml` kept YAML and lost the 1.x text keys, and `messages.yml`, `gui.yml` and `daily.yml` were created.

## Not tested

- Mailbox blocks on 26.3: through ViaBackwards the bots' right-click never reaches the server, so there the click was fired as a Bukkit event by the test plugin. A real client click was tested on 1.20.1 and 1.21.11.
- Bots on 26.x use the 26.1 protocol through ViaVersion and ViaBackwards (the bot library has no newer protocol).
- Versions between the ones above (Paper 1.20.2–1.21.10, 26.1.x, 26.2), Purpur, MySQL itself (MariaDB 10.11 was used), Windows and macOS.
- Live upgrades from 1.1.0 SQLite and MySQL data (1.1.0's own SQLite storage does not start on Paper 26.3); their tables are upgraded in the unit tests. PostMaster folder migration (unchanged from 1.1.0).
- A crash in the middle of a cash-on-delivery payment, `claims.drop-overflow`, the daily limit, mailbox capacity, campaign start and end times and `/mail admin purge` were not triggered live (the storage queries behind the limits, campaign times and purge are unit-tested). Titles and action bars were sent, but the checks read chat. The webhook was checked against a local endpoint, not Discord.
