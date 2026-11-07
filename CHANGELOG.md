# DonutMail — Changelog

## [2.0.0] — The Post Office Update

### Added
- Mailbox with Inbox, Parcels, Sent and Rewards tabs, new-mail markers, item preview, Claim All and paging; every menu is configurable in `gui.yml`, every message in `messages.yml` (MiniMessage, `&` codes, hex).
- Player parcels: compose menu, any player online or offline (names tab-complete), notes, optional postage, take back unclaimed parcels from the Sent tab, return to sender on expiry.
- Cash on delivery: the recipient previews the items and pays to accept (the money reaches the sender as a payment mail) or declines (the items go back). Optional tax.
- Server campaigns (`/mail announce`): one stored mail for every player including later joiners, optional start/end window and permission, claimed once per player; items, money, commands and vouchers; optional Discord webhook.
- Daily rewards: a streak calendar in `daily.yml` (7 days by default) delivered to the Rewards tab on the first join of the day; `/mail admin streak`.
- Reward codes: `/mail redeem <code>`, `/mail code create|delete|list|addcommand` with total uses, uses per player and expiry.
- `/mail give <player|online|all> <hand|item|voucher|money>` for staff and the console; vote rewards through NuVotifier; mailbox blocks (`/mail admin setblock`).
- Block list (`/mail block|unblock|blocklist`), refuse all parcels (`/mail toggle`), cooldown, daily limit, per-recipient limit, mailbox capacity and an item blacklist.
- Join summary with a clickable link, live notices (chat, title, action bar, sound), also for mail sent on another server.
- Staff: `/mail admin view|info|purge|streak`, and an audit log of every send, claim, payment, return, expiry, removal and redemption.
- PlaceholderAPI expansion (`%donutmail_unread%`, `_parcels%`, `_cod%`, `_daily_streak%`, `_daily_next%` and more).
- API: `DonutMailAPI.deliver(Delivery)`, `sendParcel`, `createCampaign`, `endCampaign`, `unread`, `waiting`, `openMailbox`; events `MailSendEvent` (cancellable), `MailClaimEvent`, `ParcelPaidEvent`, `ParcelReturnedEvent`, `DailyRewardEvent`, `CodeRedeemEvent`.
- Several servers on one MySQL/MariaDB database: mail, campaigns and codes are shared, other servers are told through polling.

### Changed
- One jar for Paper and Folia 1.20.1 through 26.3, Java 17 or newer (built against the Paper 1.20.1 API).
- Every claim, payment, decline, return and expiry is one conditional update in storage; claims interrupted by a crash are finished or undone on the next join from a receipt in the player's save data.
- Claims hand over the stacks that fit and keep the rest (1.x refused the whole mail when it did not fit).
- New installs use SQLite; existing servers keep their storage. SQLite uses the driver that ships with Paper (every OS), MySQL is shaded and relocated; the jar is about 1.9 MB instead of 4.1 MB (1.1.0).
- Items in YAML storage are written in Paper's versioned binary format (1.x objects are converted on load).
- The release jar is named `DonutMail-<version>.jar`.

### Fixed
- Shutting down no longer rewrites the whole mail table (1.x deleted and re-inserted every row on each stop, which could overwrite other servers' mail on a shared MySQL database).
- Items left in the send menu are never dropped on the ground; what does not fit goes to the player's own mailbox.
- SQLite storage did not start on Paper 26.3 in 1.1.0 ("Failed to get driver instance"); the driver is now loaded explicitly.

### Upgrade
- Replace the jar and restart. 1.x mail (YAML, SQLite, MySQL), vouchers and permissions are kept; the texts 1.x kept in `config.yml` move to `messages.yml`. PostMaster data is still migrated.

---

## [1.1.0] — 2026-09-17

### Changed
- Rebranded from **PostMaster** to **DonutMail** (packages, permissions, storage names, Maven artifact `donut-mail`)
- Target Paper API **26.3** (`26.3.build.8-alpha`); `api-version: '26.1'`
- Item attachments serialize via `ItemStack.serializeAsBytes()` with legacy Bukkit-stream decode fallback
- Licensed under **Apache License 2.0**

### Added
- Automatic migration from `plugins/PostMaster` data, `postmaster.db`, and `postmaster_mail` table
- Deprecated `PostMasterAPI` shim delegating to `DonutMailAPI`
- Legacy `postmaster.*` permission acceptance and `/postmaster` command alias

### Notes
- Requires **Java 25**. Supports Paper 26.1.2–26.3. Folia claimed through 26.2 (Folia 26.3 not published yet).

---

## [1.0.7] — 2026-09-04

### Changed
- Target Paper/Folia API **26.2**
- Route async work through Folia's async scheduler (`SchedulerAdapter`)

### Added
- Unit tests for storage type and list codec

---

## [1.0.5] — 2026-04-04

### Fixed
- **Jar size reduced from ~17.5 MB → 7.1 MB** via targeted shade filters:
  - `sqlite-jdbc` native binaries for Windows, macOS, FreeBSD, Android, 32-bit Linux (x86/armv7), and ppc64 are no longer bundled — only Linux x86_64 and aarch64 (both glibc and musl) are kept, covering all production server environments including Alpine/Docker.
  - MySQL X DevAPI / X Protocol classes (`com.mysql.cj.x`, `com.mysql.cj.xdevapi`) excluded — these are unused for standard JDBC connections.
- **HikariCP relocated** to `io.nightbeam.postmaster.libs.hikari` to prevent classpath conflicts when other plugins also shade HikariCP.
- **JDBC driver SPI** entries (`META-INF/services/java.sql.Driver`) are now correctly merged via `ServicesResourceTransformer` so both the SQLite and MySQL drivers register at startup.

---

## [1.0.4] — 2026-04-03

### Improved
- Voucher command placeholder replacement is now **case-insensitive** (e.g. `%Player%` works as well as `%player%`).
- Added `%player_name%` and `%name%` as accepted aliases for `%player%` in voucher command definitions.

---

## [1.0.3]

### Fixed
- Permission node corrections.

---

## [1.0.1]

### Fixed
- Minor bug fixes.
