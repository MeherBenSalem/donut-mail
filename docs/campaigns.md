# Campaigns

A campaign is one mail for every player: a server announcement, an event reward, a "thanks for 1,000 members" gift. It is stored once, every player can claim it once, and players who join later see it too, inside an optional time window.

## Creating one

```
/mail announce [options] <message>
```

| Option | Meaning |
| --- | --- |
| `hand` | Include a copy of the item in your hand (in game) |
| `money:<amount>` | Money paid through Vault when claimed (`500`, `2.5k`) |
| `voucher:<id>` | A voucher from `plugins/DonutMail/vouchers/` |
| `start:<when>` | When it appears: `2d` (from now), `2026-12-24` or `2026-12-24T18:00`. Default: now |
| `end:<when>` | When it disappears for players who did not claim it. Default: never |
| `name:<id>` | A short name for the commands below. Default: generated |
| `permission:<node>` | Players need this permission to see it |
| `sender:<name>` | Who it is from. Default: your name, or "Server" from the console |

Options go before the message; everything after them is the message (MiniMessage and `&` codes work). Times use the time zone in daily.yml (`timezone`).

```
/mail announce name:winter hand money:2500 end:2026-12-31 <aqua>Happy holidays!</aqua> Thanks for playing this year.
```

Add console commands to run when a player claims it:

```
/mail campaign addcommand winter lp user %player% permission set cosmetics.snowman
```

`%player%` and `%uuid%` are replaced.

## What players see

Online players get a chat message with a link to open the mailbox (turn it off with `campaigns.announce: false`); everyone else sees it in their Inbox when they join. Unclaimed campaigns count as new mail in the join summary and in `%donutmail_unread%`.

Clicking it claims it: DonutMail records the claim and creates the player's own copy in one transaction, then claims that copy like any other mail. If their inventory is full, the rest stays in their Inbox. A second click, a second server or a reconnect can't claim it again.

## Managing

| Command | What it does |
| --- | --- |
| `/mail campaign list` | Every campaign, whether it is live, scheduled or over, and how many players claimed it |
| `/mail campaign end <name>` | Stop it now; players who did not claim it no longer see it |
| `/mail campaign delete <name>` | Remove it and its claim records |
| `/mail campaign addcommand <name> <command>` | Add a reward command |

## Give to everyone

`/mail give all ...` and `/mail send all <message>` also create a campaign, limited to players known to the server before it was sent, so it works like a delivery to every existing player without storing one mail each. Players who join later don't get it; use `/mail announce` for that.

## Discord

Post new campaigns to a Discord channel:

```yaml
discord:
  enabled: true
  webhook-url: 'https://discord.com/api/webhooks/...'
  username: 'Server Mail'
  avatar-url: ''
  color: '#F5A623'
```

The post shows the message, the rewards and the end time. Mentions such as `@everyone` in the message are never pinged.
