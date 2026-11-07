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

(filled in after the final run)
