# Cash on delivery

Cash on delivery is a safe way to trade with someone who is not online at the same time as you. The sender puts the items in a parcel and sets a price. The recipient sees exactly what is inside before paying, and either pays to accept or declines.

## Sending

`/mail send <player> cod <price>` (or `/mail cod <player> <price>`), or set the price with the gold nugget in the parcel menu. Prices accept `1k`, `2.5m` and `1,250`. Limits: `cash-on-delivery.min-price` and `max-price`. Needs `donutmail.cod` and Vault with an economy plugin.

## Receiving

The parcel waits on the **Parcels** tab (first in the list). Clicking it opens a preview with:

- the items, exactly as they are;
- **Pay and Collect**: shows your balance before and after;
- **Decline**: the items go back to the sender and you pay nothing.

After paying, the items are yours: everything that fits is handed over at once, the rest stays in the parcel (it no longer expires). The money goes to the sender as a **payment** mail in their Inbox, and they are told right away if they are online.

## What can't go wrong

- **Paying twice**: the parcel is locked for one payment before any money moves. A double click, a second player or a second server acting at the same moment finds it locked and nothing happens.
- **Paying for something that is gone**: if the sender takes the parcel back at the same moment you pay, only one of you wins; the other is told and nothing is charged or returned twice.
- **Losing the items**: a parcel that is declined, expires, or is removed by staff goes back to the sender, unless they were paid for.
- **Losing the money**: the sender's payment is stored as mail until they collect it, so it is never paid to a player who is offline or on another server's economy by mistake.

See [How items stay safe](safety.md) for the details.

## Tax

`cash-on-delivery.tax-percent` keeps a share of each payment (0 by default). The sender's receipt shows the tax.

## Expiry

An unpaid parcel returns to the sender after `parcels.expire-days` (7 by default), like any parcel.
