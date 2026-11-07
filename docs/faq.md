# FAQ

## Is it free?

Yes, all of it: parcels, cash on delivery, campaigns, daily rewards, codes, the API and several servers on MySQL. The source is on GitHub under Apache-2.0.

## I'm updating from 1.x. Will I lose anything?

No. Replace the jar and restart. Mail stored by 1.x (YAML, SQLite or MySQL), vouchers, permissions and settings are kept; the 1.x texts in config.yml move to messages.yml. Back up `plugins/DonutMail` before updating, as with any update. Data from PostMaster is still migrated too.

## Does it work on Spigot?

No. DonutMail uses Paper's API (Adventure text, region schedulers). Paper is a drop-in replacement for Spigot, and Purpur, Folia and other Paper forks work too.

## Which versions?

Paper and Folia 1.20.1 to 26.3, one jar. Java 17 or newer (Minecraft itself needs Java 21 from 1.20.5 and Java 25 for 26.x). See [Installation](installation.md).

## Another plugin on my server also has a /mail command. Which one runs?

If another plugin takes `/mail` on your server, `/mailbox` and `/donutmail` still open DonutMail, and an alias in `commands.yml` can point `/mail` at it.

## Can players dupe items with it?

Every claim, payment, decline, return and expiry is one conditional update that exactly one action can win, also across servers, and a claim interrupted by a crash is finished or undone from a receipt in the player's own save data. See [How items stay safe](safety.md).

## Can players scam each other with cash on delivery?

The buyer sees the exact items before paying, and pays for exactly what is in the preview. The seller can't change a parcel after sending it; all they can do is take it back while it is unpaid, and a payment and a take-back at the same moment can't both succeed.

## What happens to parcels nobody claims?

They go back to the sender after `parcels.expire-days` (7). Returned parcels and payments never expire.

## My players' inventories are always full.

Claims hand over what fits and keep the rest in the mailbox; nothing is dropped unless you set `claims.drop-overflow: true`.

## How do I give something to every player, including new ones?

`/mail announce`. It is stored once and each player claims it once. See [Campaigns](campaigns.md).

## How do I translate it?

Edit `messages.yml` (chat) and `gui.yml` (menus) and run `/mail reload`. MiniMessage, `&` codes and hex colors all work.

## Where do I report a bug?

On [GitHub issues](https://github.com/MeherBenSalem/donut-mail/issues) or in the bug reports forum on our [Discord](https://discord.gg/TJc3CxR4jK). Include your server software and version, the plugin version, your `latest.log` and the lines from `plugins/DonutMail/logs/` around the problem.
