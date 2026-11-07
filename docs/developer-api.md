# Developer API

Deliver rewards from your own plugin (crates, quests, shops, battle passes), send parcels on a player's behalf, create campaigns, and react to claims and payments with Bukkit events. Mail reaches offline players, and players on other servers when the servers share a MySQL database.

## Setup

```yaml
# your plugin.yml
softdepend: [DonutMail]
```

Compile against the DonutMail jar (or the `donut-mail` Maven artifact) with `provided` scope. Check `DonutMailAPI.isAvailable()` before using it.

## Deliveries

```java
import io.nightbeam.donutmail.api.Delivery;
import io.nightbeam.donutmail.api.DonutMailAPI;

DonutMailAPI.deliver(Delivery.to(playerId)
        .sender("Crates")                          // shown as "from"
        .message("<gold>Your legendary crate reward")
        .item(new ItemStack(Material.DIAMOND, 3))  // copied when added
        .money(250)                                // paid through Vault when claimed
        .command("lp user %player% permission set kit.vip")
        .voucher("vote_reward")                    // plugins/DonutMail/vouchers/
        .expiresIn(Duration.ofDays(14))            // default: mail.expire-days; Duration.ZERO = never
        .rewardsTab()                              // list it on the Rewards tab instead of the Inbox
        .build())
    .thenAccept(mailId -> getLogger().info("Delivered " + mailId));
```

The future completes once the mail is stored (off the main thread) and completes exceptionally if it could not be saved, the voucher does not exist, or a `MailSendEvent` listener cancelled it.

## Parcels on a player's behalf

```java
DonutMailAPI.sendParcel(sellerId, "Steve", buyerId, List.of(item), "Your order", 1500.0)
    .thenAccept(result -> {
        if (result.sent()) { /* result.mailId() */ }
        else { /* BLOCKED, REFUSING, CANCELLED, NO_ECONOMY, INVALID or FAILED */ }
    });
```

A price above 0 makes it a cash-on-delivery parcel (needs Vault). The recipient's block list and refuse toggle apply; anti-spam limits and postage do not. Hand over items that are already out of every inventory.

## Campaigns

```java
DonutMailAPI.createCampaign(CampaignRequest.named("summer")
        .sender("Server")
        .message("<gold>Summer event!</gold> Thanks for playing.")
        .item(new ItemStack(Material.ELYTRA))
        .money(1000)
        .ends(Instant.now().plus(Duration.ofDays(7)))
        .permission("group.member")
        .build());

DonutMailAPI.endCampaign("summer");
```

`createCampaign` completes exceptionally when the name is taken.

## Other methods

| Method | Returns |
| --- | --- |
| `isAvailable()` | Whether DonutMail is enabled |
| `unread(UUID)` / `waiting(UUID)` | Counts for an online player (0 when offline) |
| `openMailbox(Player)` | Opens the mailbox |

### 1.x methods

These work as before and keep their signatures:

```java
DonutMailAPI.sendMail(uuid, "Hello", items);
DonutMailAPI.sendMail(uuid, "Server", "Hello", items, "vote_reward");
DonutMailAPI.sendVoucher(uuid, "welcome");
```

`PostMasterAPI` is still there for plugins written for PostMaster (deprecated).

## Events

All in `io.nightbeam.donutmail.api.event`.

| Event | When | Notes |
| --- | --- | --- |
| `MailSendEvent` | Before a parcel or delivery is saved | Cancellable; `setCancelMessage(...)` is shown to the sending player. Kind, receiver, sender, items (copies), money, price. Synchronous for player parcels, otherwise on the calling thread (`isAsynchronous()`) |
| `MailClaimEvent` | After a claim was saved | Asynchronous; player, mail id, kind, stacks delivered (copies), money, `isPartial()` |
| `ParcelPaidEvent` | A cash-on-delivery parcel was paid | Asynchronous; buyer, seller, price, amount to the seller after tax |
| `ParcelReturnedEvent` | A parcel went back to its sender | Asynchronous; reason `DECLINED`, `EXPIRED`, `RECALLED` or `REMOVED`, and the id of the returned mail |
| `DailyRewardEvent` | A daily reward was delivered | Asynchronous; calendar day and streak |
| `CodeRedeemEvent` | A reward code was redeemed | Asynchronous; code and mail id |

Example:

```java
@EventHandler
public void onPaid(ParcelPaidEvent event) {
    stats.recordTrade(event.getSeller(), event.getBuyer(), event.getPrice());
}

@EventHandler
public void onSend(MailSendEvent event) {
    if (event.getCodPrice() > 1_000_000 && !isTrusted(event.getSenderId())) {
        event.setCancelled(true);
        event.setCancelMessage("<red>Trades above 1M need a staff witness.");
    }
}
```
