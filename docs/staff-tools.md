# Staff tools

## Look into a mailbox

`/mail admin view <player>` lists a player's mail: everything waiting first, then their recent history with what happened to each (claimed, returned, expired, removed). Right-click to see a mail's contents. **Shift-right-click** a waiting mail to remove it: a player's parcel goes back to its sender, anything else is deleted. Removals are written to the audit log with your name.

## Health

`/mail admin info` shows the version, the server software, the storage and server id, the economy, PlaceholderAPI and NuVotifier hooks, and counts: all mail, mail waiting, cash-on-delivery parcels waiting, mail locked by a claim in progress, campaigns, codes and players. Network polling is listed when several servers share MySQL.

## Clean up

`/mail admin purge <days>` deletes finished mail (claimed, returned, expired, removed) older than that. Mail still waiting is never purged. `mail.history-retention-days` (60 by default) does this automatically.

## Streaks

`/mail admin streak <player> <days>` sets a player's daily streak as if their last reward came yesterday. Use it after an outage so nobody loses their streak. `0` starts them over.

## Mailbox blocks

Look at a block and run `/mail admin setblock` to make it open the mailbox; `/mail admin removeblock` undoes it.

## Audit log

`plugins/DonutMail/logs/audit-YYYY-MM-DD.log` (UTC), one line per action:

```
2026-11-07T13:01:12.481Z survival-1 SEND mail=92389b1c-... from=Alice to=Bob stacks=3 cod=250.0 postage=0.0 note=""
2026-11-07T13:01:20.017Z survival-1 PAY_START mail=92389b1c-... buyer=Bob price=250.0
2026-11-07T13:01:20.044Z survival-1 PAID mail=92389b1c-... buyer=Bob seller=Alice price=250.0 tax=0.0 receipt=4f1d...
2026-11-07T13:01:20.101Z survival-1 CLAIM mail=92389b1c-... kind=PARCEL player=Bob stacks=3 left=0 ...
```

Actions: `SEND`, `DELIVER`, `CLAIM`, `CLAIM_RECOVERED`, `CLAIM_UNDONE`, `PAY_START`, `PAID`, `PAY_REFUNDED`, `PAY_INTERRUPTED`, `RETURN`, `EXPIRE`, `REMOVE`, `CAMPAIGN`, `CAMPAIGN_CLAIM`, `CAMPAIGN_END`, `CAMPAIGN_DELETE`, `CODE_CREATE`, `CODE_DELETE`, `REDEEM`, `DAILY`, `STREAK_SET`, `VOTE`, `PURGE`, `COMPOSE_RETURN`. Turn it off with `audit.enabled: false`.
