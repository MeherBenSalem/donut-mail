# Daily rewards

A login streak calendar delivered through the mailbox. When a player joins on a new day, the reward for their streak day goes to the **Rewards** tab, and they get a chat message with a link to claim it. The calendar on the Rewards tab shows which days are collected, today's reward and what is coming.

## The calendar

`plugins/DonutMail/daily.yml`:

```yaml
enabled: true
timezone: ''          # empty = the server's time zone, or e.g. Europe/Berlin
reset-on-miss: true   # missing a day starts again at day 1; false just pauses the streak
after-last-day: LOOP  # LOOP starts again at day 1, LAST keeps giving the last day's reward

days:
  1:
    icon: IRON_INGOT
    display: ['<white>16x Iron Ingot']
    items: ['IRON_INGOT:16']
  2:
    icon: GOLD_INGOT
    display: ['<white>16x Gold Ingot', '<green>$250']
    items: ['GOLD_INGOT:16']
    money: 250
  7:
    icon: NETHERITE_SCRAP
    display: ['<white>2x Netherite Scrap', '<white>1x Diamond Block', '<green>$2,500']
    items: ['NETHERITE_SCRAP:2', 'DIAMOND_BLOCK:1']
    money: 2500
    commands: ['crate key give %player% vote 1']
    voucher: vote_reward
```

Each day can have `items` (`material[:amount]`), `money` (Vault), `commands` (run by the console when claimed; `%player%` and `%uuid%` are replaced) and a `voucher`. `display` is what the calendar shows. The bundled file has seven days; add as many as you like. Run `/mail reload` after editing.

## Rules

- One delivery per player per day, also across servers sharing a MySQL database: the player's day and streak move forward in the same transaction that saves the reward.
- Players need `donutmail.daily` (everyone by default).
- Daily rewards expire like other server mail (`mail.expire-days`, 30 by default).
- `/mail admin streak <player> <days>` sets a streak as if the last reward came yesterday, so the next join continues from it (useful after an outage). `0` starts over.

## Placeholders

`%donutmail_daily_streak%`, `%donutmail_daily_ready%`, `%donutmail_daily_next%`, `%donutmail_daily_next_day%` and `%donutmail_daily_time%`. See [Placeholders](placeholders.md).
