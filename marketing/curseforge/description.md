**[Discord](https://discord.gg/TJc3CxR4jK)**

<p align="center">
  <img src="https://raw.githubusercontent.com/Fuzss/modresources/main/pages/commons/headers/about.png" alt="About">
</p>

<p align="center">
  <img src="https://i.imgur.com/fQXL1iB.png" alt="Post Master Interface" width="400">
</p>

<p align="center">
  <strong>Post Master</strong> is a lightweight and powerful mailbox plugin for Paper and Folia servers.
  It allows players to send and receive mail whether they are online or offline, manage custom vouchers through
  inventory-based interfaces, and keep player communication reliable with configurable storage, background
  expiration handling, and an API designed for integration with other plugins.
</p>

<p align="center">
  
  <a href="https://www.youtube.com/@nightbeamstudio">
  </a>
  <a href="https://github.com/MeherBenSalem">
  </a>
  <a href="https://www.curseforge.com/members/nightbeamstudio/projects">
  </a>
  <a href="https://modrinth.com/organization/nightbeam">
  </a>
</p>

# Features

| Feature | Description |
| --- | --- |
| Offline & Online Mail | Send mail to players whether they are currently online or offline |
| Custom Vouchers | Define vouchers that players can redeem through the mail system |
| Multiple Storage Options | Supports YAML, SQLite, MySQL, and HikariCP-backed database connections |
| Inventory-Based GUI | Built-in interfaces for viewing mail, composing messages, and managing vouchers |
| API & Bridge | Exposes an API for other plugins and supports interoperability |
| Permissions Support | Fine-grained permissions such as `DonutMail.use` |
| Mail Expiration | Background tasks can handle mail expiration automatically |
| Delivery Notifications | Scheduler integration handles mail notification delivery |
| Paper Support | Designed for modern Paper server environments |
| Folia Friendly | Built with Folia compatibility in mind |
| Modular Architecture | Designed to be extended or integrated with other server systems |
| Configurable Experience | Storage, vouchers, messages, and GUI colors can be customized |

# Mail System

Post Master provides an in-game mailbox system that allows players to stay connected even when they are not online at the same time.

Players can send mail to:

* Online players
* Offline players

Messages remain available through the mailbox system so recipients can access them when they return.

# Mail Interface

Post Master uses inventory-based interfaces to keep mailbox interactions simple and familiar.

The GUI system includes interfaces for:

* Viewing received mail
* Composing messages
* Managing vouchers

This allows players to interact with the core mail system without relying entirely on command-driven workflows.

# Custom Vouchers

Post Master includes support for configurable vouchers that can be delivered and redeemed through the mail system.

Voucher definitions can be stored separately, with example voucher configuration available under:

`vouchers/example-voucher.yml`

This makes the voucher system configurable without requiring changes to the plugin itself.

# Storage

Post Master supports multiple storage options depending on the needs of the server.

## YAML

YAML storage provides a simple file-based option suitable for straightforward installations.

## SQLite

SQLite provides local database storage without requiring an external database server.

## MySQL

MySQL can be used when an external database is preferred.

## HikariCP

HikariCP-backed connections provide database connection pooling for supported database configurations.

# API & Integration

Post Master exposes an API that allows other plugins to integrate with the mailbox system.

The plugin is designed with interoperability in mind, making it possible to connect mail functionality with other server systems and custom plugins.

# Permissions

Post Master supports fine-grained permissions for controlling access to its functionality.

Example:

`DonutMail.use`

Permissions can be assigned through the server's permission-management setup.

# Scheduler & Background Tasks

Background tasks are used to manage important mailbox operations without requiring constant manual intervention.

These tasks can handle:

* Mail expiration
* Notification delivery

This helps keep the mailbox system organized while ensuring players are informed when relevant mail is available.

# Performance

Donut Mail is designed as a lightweight mailbox solution for Paper and Folia servers.

Its modular architecture separates mailbox functionality, storage, vouchers, interfaces, scheduling, and integrations so the plugin can remain focused while still supporting more advanced server setups.

# Configuration

Donut Mail ships with a sample `config.yml`.

Configuration options include:

* Storage settings
* Voucher definitions
* Messages
* GUI colors

Example voucher files can be found under:

`vouchers/example-voucher.yml`

# Installation

1. Place the Donut Mail `.jar` inside your server's `plugins/` folder.
2. Restart or reload the server.
3. Open `config.yml`.
4. Choose your preferred storage option.
5. Customize messages, voucher settings, and GUI options as needed.

# Why Choose Donut Mail?

1. **Offline Mail** — Players can communicate even when they are not online at the same time.
2. **Simple Interface** — Inventory-based GUIs make reading and composing mail straightforward.
3. **Voucher Support** — Create custom vouchers that integrate directly with the mailbox system.
4. **Flexible Storage** — Choose between YAML, SQLite, MySQL, and HikariCP-backed configurations.
5. **Developer Friendly** — An exposed API allows other plugins to integrate with Donut Mail.
6. **Fine-Grained Permissions** — Control access to mailbox functionality through permissions.
7. **Automated Management** — Background tasks handle expiration and notification delivery.
8. **Paper & Folia** — Designed for modern Paper and Folia server environments.
9. **Scalable Design** — Suitable for both smaller survival servers and larger server setups.

# Compatibility

* Paper
* Folia
* YAML storage
* SQLite
* MySQL
* HikariCP-backed database connections
* Small survival servers
* Larger server setups

# Requirements

Post Master requires a supported **Paper or Folia** server.

Configure your preferred storage backend through `config.yml`.

If you encounter a bug or have a feature request, please join the Discord community or report it through the appropriate project page.

---

> **Running a server?** NightBeam's premium plugins are on [BuiltByBit](https://nightbeam.dev/store): NightWatch Pro anti-cheat, Elo ranked leaderboards and more.

---

### More from NightBeam Studio

- **[Remnants: Ashen Legacy](https://www.curseforge.com/minecraft/modpacks/remnants-ashen-legacy)**: the story-driven soulslike RPG modpack built on NightBeam's mods
- **[Premium server plugins](https://nightbeam.dev/store)**: anti-cheat, ranked PvP, SMP setups and more on BuiltByBit
- **[NightBeam Pro](https://nightbeam.dev/memberships)**: the model library and NightBeam Forge credits, from $5 a month
- **[Discord](https://discord.gg/TJc3CxR4jK)**: support, bug reports, suggestions and early looks
- **[Support NightBeam](https://nightbeam.dev/support)**: help keep every mod free

**Follow:** [YouTube](https://www.youtube.com/@nightbeamstudio) · [Instagram](https://www.instagram.com/nightbeamstudio/) · [X](https://x.com/NightBeamLLC) · [Facebook](https://www.facebook.com/profile.php?id=61574823331471)
