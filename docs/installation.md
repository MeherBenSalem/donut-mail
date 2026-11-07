# Installation

DonutMail is one jar for every supported version. Install it on the server; players don't need any mods.

## Requirements

| Server | Minecraft | Java | Notes |
| --- | --- | --- | --- |
| Paper, Purpur, other Paper forks | 1.20.1 – 1.20.4 | 17+ | |
| Paper, Purpur, other Paper forks | 1.20.5 – 1.21.11 | 21+ | |
| Paper, Purpur, other Paper forks | 26.1 – 26.3 | 25+ | |
| Folia | 1.20.1 – 26.3 | as above | Region-thread safe |
| Spigot, Bukkit | | | Not supported: they lack Paper's API. The plugin says so and disables itself. |

The Java column is Minecraft's own requirement; DonutMail itself runs on Java 17 and newer.

Optional:

- **Vault** or **VaultUnlocked** with an economy plugin for cash on delivery, postage and money rewards. Without one, everything else works and money features say so.
- [PlaceholderAPI](placeholders.md) for placeholders.
- [NuVotifier](configuration.md#vote-rewards) for vote rewards.
- MySQL or MariaDB to [share mail between servers](multi-server.md).

## Install

1. Put `DonutMail-<version>.jar` in `plugins/`.
2. Restart the server.
3. Edit `plugins/DonutMail/config.yml`, `messages.yml`, `gui.yml` and `daily.yml`, then run `/mail reload`.

A new install stores mail in SQLite (`plugins/DonutMail/data/donutmail.db`); nothing else needs setting up for a single server.

## Updating

Replace the jar and restart. Mail, settings and messages are kept, and new settings are added automatically. Back up `plugins/DonutMail` before updating, as with any update.

**From 1.x:** the same steps apply, on the same storage (YAML, SQLite or MySQL). 1.x mail is listed and claimable in 2.0; texts that 1.x kept in config.yml move to messages.yml. The `donutmail.*` and `postmaster.*` permissions keep working. See the [changelog](../CHANGELOG.md).

**From PostMaster:** keep the `plugins/PostMaster` folder when DonutMail starts for the initial time. DonutMail copies it into `plugins/DonutMail` and renames the old database and table.

## Downloads

- [Modrinth](https://modrinth.com/project/a4rA3S9T)
- CurseForge (project 1480575)
- [GitHub](https://github.com/MeherBenSalem/donut-mail)
