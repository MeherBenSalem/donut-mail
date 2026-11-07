# Live test matrix

Boots real Paper and Folia servers and plays DonutMail with [mineflayer](https://github.com/PrismarineJS/mineflayer)
bots over the network protocol, so every check goes through the real plugin, real inventory packets, the real
economy bridge and the real storage.

| Target | Java | Bot protocol | Economy | Storage |
| --- | --- | --- | --- | --- |
| Paper 1.20.1 | 17 | 1.20.1 | Vault + EssentialsX | YAML |
| Paper 1.21.11 | 21 | 1.21.11 | Vault + EssentialsX | SQLite (+ NuVotifier, Discord webhook, crash tests) |
| Folia 1.21.11 | 21 | 1.21.11 | VaultUnlocked + fixture economy | SQLite |
| Paper 26.3 | 25 | 26.1 through ViaVersion + ViaBackwards | Vault + EssentialsX | MySQL/MariaDB |
| Folia 26.3 | 25 | 26.1 through ViaVersion + ViaBackwards | VaultUnlocked + fixture economy | YAML |
| Folia 1.20.1 | 17 | 1.20.1 | VaultUnlocked + fixture economy | SQLite |
| Network: 2 × Paper 1.21.11 | 21 | 1.21.11 | VaultUnlocked + fixture economy | one MySQL/MariaDB database |
| Upgrade: Paper 26.3 | 25 | 26.1 via ViaBackwards | VaultUnlocked | 1.1.0 YAML and SQLite data, then 2.0.0 |

EssentialsX does not load on Folia, so Folia targets use VaultUnlocked with the fixture's in-memory economy.

## What each single-server target does (`coreScenario`)

1. DonutMail enables, registers its API and PlaceholderAPI expansion, and finds the economy.
2. Daily reward day 1 arrives when the bot joins and is claimed from the Rewards tab.
3. Alice sends Bob a parcel through the compose menu; Bob gets a live notice and claims it from the Parcels tab.
4. A parcel to Carol while she is offline; she gets the clickable join summary and claims it.
5. Cash on delivery: Bob previews and pays 250 (balance 1000 → 750, items delivered), Alice is told and collects the
   250 from her receipt. Another parcel is declined and returns to Alice. A double click on Pay charges once.
6. Bob pays while Alice takes the same parcel back, at the same moment: exactly one wins, and balances, items and
   mail agree with the winner.
7. Full inventory (2 free slots, 5 swords): 2 delivered, 3 stay; the rest claimed after making room.
8. `/mail announce` campaign: broadcast, claimed once (+100), not offered again.
9. Reward code with 2 uses and 1 per player: OK, already redeemed, OK, used up.
10. Console `/mail give` to one player, to everyone online, and to `all` (a campaign).
11. Block list and the refuse toggle stop parcels, and the items stay with the sender; cooldown and the
    per-recipient limit (3) refuse sends.
12. An unanswered cash-on-delivery parcel expires (expiry set to about 100 seconds) and returns to the sender.
13. PlaceholderAPI values, `/mail admin info`, a mailbox block (a lectern) opens the mailbox, staff view and
    removal, `/mail reload`, and the API events seen by the fixture.
14. Restart: mail is unchanged, the campaign stays claimed while a new player can claim it, and a streak set to 2
    with `/mail admin streak` continues with the day 3 reward.
15. On Paper 1.21.11: a NuVotifier vote (protocol v1, RSA) becomes a reward; the Discord webhook receives the
    campaign; and two crash tests stop the server dead right after a delivery (with
    `-Ddonutmail.test.crash-after-delivery=nosave|save`), then check the claim is undone (player data rolled back)
    or finished (player data saved) on the next join, never lost or doubled.
16. The console is scanned for DonutMail errors (severe lines, stack frames in `io.nightbeam.donutmail`, linkage errors).

The network target runs two servers on one database: a parcel sent on one server reaches a player on the other,
three rounds of pay-vs-take-back across the two servers each have exactly one winner, a campaign is claimed once
per player across both servers, and the daily reward is delivered once per player per day.

The upgrade target runs DonutMail 1.1.0 (`DONUTMAIL_110_JAR`) on YAML and on SQLite, sends an item mail through
the 1.1.0 menu, a text mail and a voucher, swaps in 2.0.0 and claims the 1.1.0 mail.

## Running it

```bash
mvn -B package
mvn -B -f tools/smoke/fixture/pom.xml package -Ddonutmail.version=2.0.0 -Ddonutmail.jar="$PWD/target/DonutMail-2.0.0.jar"
npm --prefix tools/smoke install
export JAVA17_HOME=... JAVA21_HOME=... JAVA25_HOME=...      # or put JDKs in $MAILLAB_HOME/dl/jdk17 etc.
export DONUTMAIL_110_JAR=/path/to/donut-mail-1.1.0.jar        # for the upgrade target
node tools/smoke/run.mjs                                      # every target
node tools/smoke/run.mjs paper-1.21.11 folia-26.3             # selected targets
```

MySQL targets use `$MAILLAB_MYSQL_HOST`, `_PORT`, `_USER` and `_PASSWORD` (default `127.0.0.1:3307`, `mail`/`mailpass`)
and recreate the databases `donutmail_live` and `donutmail_net`.

Server jars come from the PaperMC Fill API (SHA-256 checked), plugins from Modrinth (SHA-512 checked) or their
official GitHub releases, cached in `$MAILLAB_HOME/dl` (default `~/.cache/donutmail-smoke`). Each server runs with
`-Xms512M -Xmx1536M`. A JSON report per target is written to `$MAILLAB_HOME/reports/`.

`fixture/` is a plugin for these tests: an in-memory Vault economy (when EssentialsX is absent), API event counters and
console helpers (`lab money`, `balance`, `give`, `fill`, `clear`, `count`, `deliver`, `parcel`, `mails`, `place`,
`save`, `events`). Never install it on a real server.
