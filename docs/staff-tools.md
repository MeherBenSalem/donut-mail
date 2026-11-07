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
2026-11-07T13:00:17.303285529Z server-f51027bc SEND mail=d7866a02-ee17-47bb-bbfc-54c06607a419 from=Alice fromId=10920508-d5d8-3eed-93d2-92f193afe7d7 to=Bob toId=faa5dca3-c3d4-354b-ae1b-dde9e5a14b3b stacks=1 cod=250.0 postage=0.0 note=""
2026-11-07T13:00:19.217511710Z server-f51027bc PAY_START mail=d7866a02-ee17-47bb-bbfc-54c06607a419 buyer=Bob price=250.0
2026-11-07T13:00:19.258709024Z server-f51027bc PAID mail=d7866a02-ee17-47bb-bbfc-54c06607a419 buyer=Bob seller=Alice price=250.0 tax=0.0 receipt=040539b8-3951-4a6a-84be-2651b3dada32
2026-11-07T13:00:19.303930665Z server-f51027bc CLAIM mail=d7866a02-ee17-47bb-bbfc-54c06607a419 kind=PARCEL player=Bob stacks=1 left=0 dropped=0 money=0.0 voucher=null commands=0
2026-11-07T13:00:29.717938445Z server-f51027bc RETURN mail=c8751e5f-a131-46f4-a704-4ff6ed849813 reason=DECLINED by=Bob from=Bob to=Alice stacks=1 return=941450cf-afcf-4c4d-b8ed-c004d2395d1d
```

(Lines from a live test run: a cash-on-delivery parcel sent, paid and claimed, then a declined one.)

Actions: `SEND`, `DELIVER`, `CLAIM`, `CLAIM_RECOVERED`, `CLAIM_UNDONE`, `PAY_START`, `PAID`, `PAY_REFUNDED`, `PAY_INTERRUPTED`, `RETURN`, `EXPIRE`, `REMOVE`, `CAMPAIGN`, `CAMPAIGN_CLAIM`, `CAMPAIGN_END`, `CAMPAIGN_DELETE`, `CODE_CREATE`, `CODE_DELETE`, `REDEEM`, `DAILY`, `STREAK_SET`, `VOTE`, `PURGE`, `COMPOSE_RETURN`. Turn it off with `audit.enabled: false`.
