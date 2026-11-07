# Configuration

DonutMail has five files in `plugins/DonutMail/`. New settings are added to them when the server starts; your values are kept. `/mail reload` applies changes, except storage and network settings, which need a restart.

| File | What it holds |
| --- | --- |
| `config.yml` | Storage, limits, cash on delivery, notifications, votes, Discord, mailbox blocks, sounds |
| `messages.yml` | Every chat, title and action bar text |
| `gui.yml` | Every menu: titles, slots, materials, names and lore |
| `daily.yml` | The daily reward calendar ([Daily rewards](daily-rewards.md)) |
| `vouchers/*.yml` | Vouchers: reward bundles with a name, icon, lore and console commands |

## Text

`messages.yml` and `gui.yml` accept MiniMessage (`<gold>`, `<#ffaa00>`, `<gradient:#f9d423:#ff4e50>`, `<click:run_command:'/mail'>`, `<hover:show_text:'...'>`), legacy `&` codes and `&#RRGGBB` hex, mixed freely. Inside `<click>` and `<hover>` use MiniMessage colors, because a legacy code resets formatting and would end the click. Menu text is not italic unless you make it so; lore is gray unless you color it. Player names and notes are always inserted as plain text.

An empty message is not sent, so any message can be turned off with `''`.

## Menus

`gui.yml` sets each menu's title and, for every button, `slot`, `material`, `name`, `lore` and `model` (custom model data for resource packs, 0 = none). Mail icons per kind are under `entries`, and the lines they show under `lore`. The default layout:

- **Mailbox**: tabs at the top (Inbox, Parcels, your head, Sent, Rewards), mail in rows 2 to 5, and Previous, Claim All, Send a Parcel, settings or Redeem a Code, Next at the bottom. The Rewards tab shows the 7-day calendar in row 2.
- **Parcel menu**: items in the top five rows; Cancel, recipient, note, Send, cash on delivery and help at the bottom.
- **Preview**: the items in the top five rows; Back, info and Claim (or Pay, info and Decline) at the bottom.

Set `filler.material: AIR` to leave empty slots empty.

## config.yml

```yaml
storage:
  type: SQLITE            # YAML, SQLITE or MYSQL
  mysql: { host: localhost, port: 3306, database: donutmail, username: root, password: change_me, pool-size: 10 }

network:
  server-id: ''           # empty = generated once (data/server-id.txt)
  poll-interval-seconds: 3

mail:
  expire-days: 30         # server mail; 0 = never; decimals allowed
  expiry-scan-seconds: 60
  history-retention-days: 60

parcels:
  enabled: true
  expire-days: 7
  return-to-sender: true  # false deletes expired parcels (logged)
  allow-recall: true
  allow-self: false
  cooldown-seconds: 10
  max-per-day: 30
  max-pending-per-recipient: 5
  mailbox-capacity: 54
  note-max-length: 120
  blacklist: [BEDROCK, BARRIER, COMMAND_BLOCK, ...]
  postage: { enabled: false, flat: 0, per-stack: 0 }

cash-on-delivery: { enabled: true, min-price: 1, max-price: 1000000000, tax-percent: 0 }

claims:
  drop-overflow: false    # true drops what doesn't fit at the player's feet

notifications:
  join-summary: true
  join-delay-seconds: 2
  live: true              # tell online players at once, also from other servers
  chat: true
  title: true
  action-bar: true
  sound: true
  sound-key: 'minecraft:entity.experience_orb.pickup'
```

The rest (`campaigns`, `codes`, `votes`, `discord`, `mailbox-blocks`, `audit`, `sounds`, `placeholderapi`) is described in the file and on the pages for each feature.

### Storage

- **YAML** keeps everything in `data/*.yml` and writes each change before acting on it. Fine for small servers; above a few thousand mails, SQLite is faster.
- **SQLITE** (default for new installs) uses `data/donutmail.db` and the SQLite driver that ships with Paper.
- **MYSQL** works with MySQL 8 and MariaDB, for one server or [several](multi-server.md).

### Vote rewards

With [NuVotifier](https://github.com/NuVotifier/NuVotifier) installed:

```yaml
votes:
  enabled: true
  message: '<green>Thanks for voting on <white>%service%</white>!'
  money: 250
  items: ['DIAMOND:2']
  commands: ['crate key give %player% vote 1']
  voucher: ''
```

Every vote becomes a reward on the player's Rewards tab, also when they are offline.

### Sounds

`sounds.<event>: 'namespace:key:volume:pitch'` for `click`, `claim`, `send`, `paid`, `error` and `page`; `none` mutes one.

## Upgrading from 1.x

Replace the jar and restart. Mail, vouchers and settings are kept. The texts 1.x had in config.yml move to messages.yml, `mail.page-size` is removed (the layout is in gui.yml now), and a 1.x sound name such as `ENTITY_EXPERIENCE_ORB_PICKUP` becomes a key. Existing storage settings are not changed: a 1.x server on YAML stays on YAML.
