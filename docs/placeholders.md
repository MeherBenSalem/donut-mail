# PlaceholderAPI

With [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) installed, DonutMail registers these placeholders. They read from a cache of online players, so a scoreboard or tab list can show them every tick without touching the database.

| Placeholder | Value |
| --- | --- |
| `%donutmail_unread%` | New mail (not opened yet), including server campaigns not claimed yet |
| `%donutmail_waiting%` | Everything waiting in the mailbox |
| `%donutmail_parcels%` | Parcels waiting, including cash on delivery |
| `%donutmail_cod%` | Cash-on-delivery parcels waiting for payment |
| `%donutmail_rewards%` | Mail on the Rewards tab (daily, codes, votes) |
| `%donutmail_inbox%` | Mail on the Inbox tab |
| `%donutmail_campaigns%` | Campaigns not claimed yet |
| `%donutmail_daily_streak%` | Daily streak in days |
| `%donutmail_daily_ready%` | `true` when today's daily reward was delivered |
| `%donutmail_daily_next%` | The next daily reward, as written under `display` in daily.yml |
| `%donutmail_daily_next_day%` | The calendar day of the next reward |
| `%donutmail_daily_time%` | Time until the next reward (`0s` when it is waiting) |

Turn the expansion off with `placeholderapi.enabled: false` in config.yml.

Example for a scoreboard line: `&6Mail: &f%donutmail_waiting%` · `&eStreak: &f%donutmail_daily_streak%`.
