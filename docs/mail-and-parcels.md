# Mail and parcels

`/mail` opens the mailbox. It has four tabs:

| Tab | What is in it |
| --- | --- |
| **Inbox** | Messages and deliveries from staff, the console and other plugins, payments for your cash-on-delivery parcels, parcels coming back to you, and server campaigns |
| **Parcels** | Items other players sent you, cash-on-delivery parcels at the top |
| **Sent** | Parcels you sent and what happened to them: waiting, waiting for payment, paid, claimed or returned |
| **Rewards** | The daily reward calendar, daily rewards, redeemed codes and vote rewards |

New mail glows and is marked **● New** until you open or claim it. The head at the top shows your counts and daily streak.

## Claiming

- **Left-click** a mail to claim it. Everything that fits goes into your inventory; the rest stays in the mailbox (a partial claim) and you are told how many stacks are left.
- **Right-click** to preview the contents before claiming.
- **Claim All** (the hopper) claims everything on the tab that fits, and stops when your inventory is full. Cash-on-delivery parcels are skipped. `/mail claimall` does the same from chat.

Stacks are always handed over whole, so a claim never splits a stack between your inventory and the mailbox. Items are never dropped on the ground unless the server sets `claims.drop-overflow: true`.

## Sending a parcel

`/mail send <player>` (or the **Send a Parcel** button) opens the parcel menu:

1. Put the items in the top five rows.
2. **To** (the head): click to type another player's name. Online and offline players both work; names tab-complete.
3. **Note** (the sign): click to type a short note (up to `parcels.note-max-length` characters, 120 by default).
4. **Cash on Delivery** (the gold nugget): left-click to set a price, right-click to remove it. See [Cash on delivery](cash-on-delivery.md).
5. **Send Parcel**.

The recipient is told right away if they are online (on any server of a network), or with a summary when they next join. Closing the menu without sending gives everything back.

Parcels nobody claims return to the sender after `parcels.expire-days` (7 by default). Until a parcel is claimed or paid, the sender can open it on the **Sent** tab and **Take Back** it.

## Postage

Optional (off by default): a flat fee and/or a fee per stack, paid through Vault when sending. `donutmail.postage.exempt` sends for free.

```yaml
parcels:
  postage:
    enabled: true
    flat: 5
    per-stack: 1
```

## Who can send you parcels

- `/mail block <player>` refuses parcels from one player, `/mail unblock <player>` undoes it, `/mail blocklist` lists them.
- `/mail toggle` (or the comparator on the mailbox) refuses all parcels. Staff deliveries, campaigns and rewards still arrive.

## Anti-spam limits

| Setting (`parcels.`) | Default | Meaning |
| --- | --- | --- |
| `cooldown-seconds` | 10 | Between two parcels from one player |
| `max-per-day` | 30 | Parcels one player can send per day |
| `max-pending-per-recipient` | 5 | Parcels from one sender waiting in one mailbox |
| `mailbox-capacity` | 54 | Parcels a mailbox holds from all senders together |
| `blacklist` | command blocks, barriers, bedrock... | Items that can never be sent |

`donutmail.bypass.limits` (staff) ignores the cooldown and the three limits. Use `0` to turn a limit off.

## A mailbox at spawn

Look at a block (a lectern, a barrel, a sign) and run `/mail admin setblock`: right-clicking that block opens the mailbox. `/mail admin removeblock` undoes it. The list is `mailbox-blocks.locations` in config.yml.
