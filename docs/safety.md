# How items stay safe

DonutMail is built so an item or a payment can't be duplicated by double clicks, lag, two players acting at once, two servers acting at once, or a crash.

## One winner per action

Every change to a mail is one conditional update in storage: it checks the mail's current state and changes it in the same step. Claiming, paying, declining, taking a parcel back, expiry and staff removal all go through this. Whoever wins the update does the work; everyone else is told the mail is gone and nothing moves for them. This holds for YAML (one lock around every change), SQLite and MySQL, and across servers that share a MySQL database.

When an action also creates a mail (a receipt for the sender, a parcel going back, a player's copy of a campaign, a daily reward, a code reward), both are saved in one transaction: there is never a receipt without a payment, or a returned parcel without the original closing.

## A claim, step by step

1. The mail is locked for this claim with a random token (`PENDING` → `CLAIMING`).
2. On the player's own thread, the stacks that fit are added to their inventory, and a receipt (the token and which stacks were handed over) is written into the player's save data, in the same tick. Any save of the player has both or neither.
3. The claim is saved: `CLAIMED`, or back to `PENDING` with just the stacks that did not fit.

If the server stops between steps 2 and 3, the next time the player joins that server the receipt decides:

- **receipt present**: the delivery was saved with the player, so the claim is finished and nothing is handed over again;
- **receipt absent**: the player's data was rolled back with the crash, so the mail is unlocked and can be claimed again.

Either way nothing is lost and nothing is doubled. Money, vouchers and commands are handed over once, right after step 3.

## Cash on delivery

Paying locks the parcel, then takes the money, then saves the payment together with the sender's receipt. If the money can't be taken, the lock is released. If saving fails, the money is given back. If the server stops in the moment between taking the money and saving, the parcel goes back to waiting for payment on restart and the audit log records `PAY_INTERRUPTED` with the amount, so staff can check that player's balance.

The receipt carries the money to the sender as mail, so it is stored safely until the sender collects it.

## Items are never deleted silently

- Expired, declined, recalled and staff-removed parcels go back to the sender as a new mail. Returned parcels and receipts don't expire.
- Items that don't fit stay in the mailbox (partial claim). They are never dropped at the player's feet unless you set `claims.drop-overflow: true`.
- Items left in the parcel menu go back to the player's inventory; anything that doesn't fit goes to their own mailbox. If the server stops while the menu is open, the items go to their mailbox.
- Server mail with items can expire (`mail.expire-days`); every expiry is written to the audit log with the number of stacks.

## Minecraft upgrades

Items are stored in Paper's binary item format, which records the Minecraft data version and runs Mojang's data fixers when read, so stored mail survives Minecraft upgrades. An item this server version can't read stays in the mailbox and is shown as unreadable instead of breaking the menu.

## Text from players

Player names and parcel notes are always inserted as plain text, so they can't add colors, click events or fake messages.

## Audit log

`plugins/DonutMail/logs/audit-YYYY-MM-DD.log` (UTC) has one line for every send, claim, payment, return, expiry, removal, campaign, redemption and daily reward, with the server id.
