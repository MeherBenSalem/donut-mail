# Permissions

| Permission | Default | What it allows |
| --- | --- | --- |
| `donutmail.use` | everyone | Open the mailbox, claim mail, block players, refuse parcels |
| `donutmail.senditem` | everyone | Send parcels |
| `donutmail.cod` | everyone | Send cash-on-delivery parcels |
| `donutmail.redeem` | everyone | Redeem reward codes |
| `donutmail.daily` | everyone | Receive daily rewards |
| `donutmail.bypass.limits` | op | Ignore the parcel cooldown, the daily limit and the mailbox limits |
| `donutmail.postage.exempt` | op | Send parcels without paying postage |
| `donutmail.admin` | op | Every staff command (includes the two bypasses above) |

Campaigns can also require a permission each (`/mail announce permission:<node> ...`) or all together (`campaigns.permission` in config.yml).

## Upgrading

The 1.x nodes `donutmail.use`, `donutmail.senditem` and `donutmail.admin` keep their meaning. The PostMaster-era nodes `postmaster.use`, `postmaster.senditem` and `postmaster.admin` are still accepted.

To stop players from sending parcels while keeping the mailbox, negate `donutmail.senditem`. To keep parcels but turn off trading for money, negate `donutmail.cod`.
