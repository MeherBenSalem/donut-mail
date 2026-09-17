# DonutMail

Mailbox plugin for Paper and Folia: send mail, items, and vouchers to online or offline players.

## Requirements

- Java **25**
- Paper **26.1.2+** (including **26.2** and **26.3**)
- Folia supported through **26.2** (Folia 26.3 not published yet)

## Install

1. Drop `donut-mail-1.1.0.jar` into `plugins/`.
2. Start the server once to generate `config.yml` and vouchers.
3. Use `/mail` or `/mailbox`.

Upgrading from **PostMaster**: keep the old `plugins/PostMaster` folder on first boot. DonutMail copies that data into `plugins/DonutMail`, renames the SQLite DB/table when needed, and still accepts `postmaster.*` permissions for this release.

## Commands

| Command | Description |
|---------|-------------|
| `/mail` | Open mailbox |
| `/mailbox` | Open mailbox GUI |
| `/mail send <target> <message>` | Send text mail (admin) |
| `/mail senditem <player>` | Compose item mail |
| `/mail voucher send <id> <target>` | Send voucher |
| `/mail reload` | Reload config/vouchers |

Aliases: `/donutmail`, `/postmaster` (legacy).

## Permissions

| Node | Default |
|------|---------|
| `donutmail.use` | true |
| `donutmail.senditem` | true |
| `donutmail.admin` | op |

Legacy `postmaster.*` nodes still work.

## Storage

YAML (default), SQLite, or MySQL — see `config.yml`.

## API

```java
DonutMailAPI.sendMail(uuid, "Server", "Hello", items, null);
DonutMailAPI.sendVoucher(uuid, "welcome");
```

`PostMasterAPI` remains as a deprecated shim for one release.

## Build

```bash
mvn -B clean package
```

Artifact: `target/donut-mail-1.1.0.jar`

## Publish

Modrinth project id: `a4rA3S9T`  
CurseForge project id: `1480575`

## Contributing / Security / License

See [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](.github/SECURITY.md), and [LICENSE](LICENSE) (Apache-2.0).
