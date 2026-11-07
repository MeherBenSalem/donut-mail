# Marketing assets: 2.0 "The Post Office Update"

Listing copy and artwork for CurseForge, Modrinth and Hangar. Nothing here ships in the plugin jar.

## Listing

| File | Use |
| --- | --- |
| [`curseforge/description.md`](curseforge/description.md) | Full project description (Markdown; also works on Modrinth and Hangar) |
| [`curseforge/summary.txt`](curseforge/summary.txt) | Short project summary (180 characters) |
| [`../DonutMail-2.0.0-PatchNotes.md`](../DonutMail-2.0.0-PatchNotes.md) | File changelog the publish workflow uploads |

Image links in the description point at `raw.githubusercontent.com/MeherBenSalem/donut-mail/main/marketing/images/...`,
so they work once this folder is on `main`. To host on CurseForge instead, upload the images to the project gallery and
replace each URL.

The description runs in this order:
1. the mailbox, parcels, cash on delivery, campaigns, daily rewards and reward codes;
2. notifications, safety, integrations, staff tools and several servers;
3. upgrading from 1.x and configuration;
4. compatibility, commands and permissions, the FAQ, and a gallery of 2.0.0 screenshots.

The artwork shares the pixel donut, fonts and dark backdrop of DonutAuctionHouse and DonutOrders. DonutMail uses
sky-blue icing (the auction house uses pink, orders mint), and its icon tag is a gold "MAIL" with an envelope.

## Adding a Minecraft version

The supported range comes from [`release/supported-minecraft.json`](../release/supported-minecraft.json), which the
publish workflow also uses. The banner and the compatibility card read it, so a new version needs no artwork edits:

1. Add the version to `release/supported-minecraft.json` and to `MATRIX` in
   [`tools/smoke/run.mjs`](../tools/smoke/run.mjs).
2. Run the live matrix, then `node tools/marketing/compat.mjs` to refresh `source/compat.json` from its reports.
3. Rerun the renderer for `banner compatibility`, and update the compatibility table and the tested versions in
   `curseforge/description.md`.

A new release name or number goes in `RELEASE` in `source/render.cjs` and in the opening lines of the description.

## Images

| File | Size | Use |
| --- | --- | --- |
| `images/banner.png` | 1800×600, 762 KB | Description header: the full mailbox with a delivery's tooltip beside it, "2.0 · The Post Office Update", Paper and Folia, the versions |
| `images/icon.png` | 512×512, 104 KB | Project avatar: the pixel donut with sky-blue icing and a gold envelope "MAIL" tag |
| `images/card-mailbox.png` | 1600×900, 946 KB | The Inbox, two rows of mail with a delivery's tooltip beside it, plus the counts and streak on your head |
| `images/card-parcels.png` | 1600×900, 964 KB | The parcel menu with recipient, cash-on-delivery price and postage, plus the preview of Kiri's gear parcel |
| `images/card-cash-on-delivery.png` | 1600×900, 998 KB | The cash-on-delivery preview with Pay and Decline, the Pay tooltip with the balance, and the seller's chat lines |
| `images/card-safety.png` | 1600×900, 809 KB | Pay against take-back on one parcel: the two conditional updates (from `SqlMailStorage`) and the tested numbers |
| `images/card-campaigns.png` | 1600×900, 964 KB | A campaign's tooltip, the `/mail announce` command with the console's answer, and the broadcast in chat |
| `images/card-daily.png` | 1600×900, 882 KB | The Rewards tab with the 7-day calendar, plus days 1 to 3 of the bundled `daily.yml` |
| `images/card-codes.png` | 1600×900, 986 KB | The Rewards tab with the Redeem a Code button, plus `/mail code create` and the console's answer |
| `images/card-notifications.png` | 1600×900, 1466 KB | The New Mail title, action bar and chat line over the world, plus the join summary |
| `images/card-integrations.png` | 1600×900, 785 KB | The Discord embed DonutMail posted, live PlaceholderAPI values and the developer API with its six events |
| `images/card-staff.png` | 1600×900, 882 KB | The staff view with a removable delivery, plus the console output of `/mail admin info` |
| `images/card-network.png` | 1600×900, 837 KB | Two servers on one database, the MySQL settings and the results of the two-server live test |
| `images/card-config.png` | 1600×900, 826 KB | `gui.yml` and `messages.yml` lines, plus the Inbox they produce |
| `images/card-compatibility.png` | 1600×900, 847 KB | Every target of the 2.0.0 live matrix (`source/compat.json`) |
| `images/header-*.png` | 1400×120, transparent | 17 section headings for the description |
| `images/divider.png` | 1400×40, transparent | Section divider |
| `images/gifs/cod.gif` | 640×360, 15 s, 960 KB | Selling with cash on delivery in real time: the parcel menu, Send, Kiri pays, the payment in the Inbox |
| `images/screenshots/*.jpg` | 1920×1080 | 27 captures: gallery uploads and card art |

### How the screenshots were captured

Every screenshot and the GIF are unedited captures of the **vanilla Minecraft 1.21.11 client**, launched with
portablemc, on a **Paper 1.21.11** server running DonutMail 2.0.0 (the release jar, SHA-256 `06bf4581…5ebdd`).
- The client ran in a 1920×1080 Xvfb display with software rendering (Mesa llvmpipe) at GUI scale 3.
- It was driven by [`tools/marketing/capture.mjs`](../tools/marketing/capture.mjs) through console commands and XTEST
  mouse and keyboard input ([`xinput.py`](../tools/marketing/xinput.py)). The mouse is moved or clicked just while a
  menu is open (the script checks the menu's grey panel on screen), so the camera never turns between shots.

- **The world** is seed 8675309 on peaceful, at noon, in the nearest cherry grove, as for the other Donut plugins.
  - Moonbeam stands on a single leaf block five blocks up (out of view), so the camera looks over the grove.
  - Recipes and the advancements the received items would unlock (elytra, diamond, blaze rod, iron) were granted
    before the shots, so no toasts cover the menus.
  - `scenery.jpg` (and the blurred card backdrop) has the HUD hidden with F1. Every other shot has the HUD on.
- **The mail** was all sent by real actions before and during the shots:
  - Three other players, Kiri, Willow and Draxen (mineflayer bots), wrote parcels to Moonbeam through the parcel
    menu: Kiri's elytra kit for $12,500 cash on delivery and an enchanted gear parcel, Willow's wood and a shulker box
    of saplings, Draxen's iron. Each note was typed through the menu's chat prompt.
  - The console sent the server mail (`/mail give` and `/mail send` to Moonbeam and Willow), two campaigns
    (`/mail announce`), a reward code, and three NuVotifier votes (protocol v1, from three made-up vote sites).
  - Three earlier sales of Moonbeam's were sent through the API (the smoke-test fixture's `lab parcel`); Kiri paid two
    and declined one, which gave the two payments and the returned parcel in the Inbox.
  - Moonbeam joined once the day before (a bot took the day 1 reward), and `/mail admin streak Moonbeam 3` set the
    streak, so the client's join delivered day 4.
- **Real actions in the shots:** Moonbeam previewed and paid Kiri's elytra kit, redeemed `SPRING` through the Rewards
  tab, wrote a cash-on-delivery parcel to Kiri with a note (Kiri paid), sold a second one on camera for the GIF (Kiri
  paid), sent a third that Kiri declined, and sent Willow a gift. Kiri's last parcel produced the live title. The
  campaign broadcast is a `/mail announce` made while Moonbeam was online. The staff shots were taken after
  `/op Moonbeam`; the mailbox block is a lectern made a mailbox with `/mail admin setblock` (its location was checked
  in `config.yml`).
- **Configuration**: the defaults, except postage (`flat: 5`, `per-stack: 1`), vote rewards (`money: 10`,
  `items: ['DIAMOND:1']`) and a Discord webhook pointed at a local endpoint, which kept the JSON DonutMail posted.
  The economy is the fixture's in-memory Vault economy (it shows as `MailLabEconomy` in `/mail admin info`), so prices
  use Vault's plain `$` formatting.
- **`gifs/cod.gif`** is a real-time recording (x11grab at 20 fps, then 10 fps, the left 1600×900 below the top 180
  rows, scaled to 640×360 so the menu and the chat both show; the palette shrinks until the file is under 1 MB).
- **Console text** in `source/console/` was saved by the same run: `/mail admin info`, `papi parse` values, the
  Discord payload, the answers to `/mail announce` and `/mail code create`, the API event counts and the audit log.

## Regenerating the artwork

```bash
MAVEN_OPTS=-Xmx1g mvn -B -DskipTests package
mvn -B -f tools/smoke/fixture/pom.xml package -Ddonutmail.version=2.0.0 -Ddonutmail.jar="$PWD/target/DonutMail-2.0.0.jar"
node tools/smoke/run.mjs paper-1.21.11                       # once, to download the server and plugin jars
JAVA21_HOME=/path/to/jdk21 node tools/marketing/capture.mjs  # screenshots, the GIF and console text (about 8 minutes)
cp marketing/images/screenshots/scenery.jpg marketing/source/backdrop.jpg   # then resize to 1280×720
python3 tools/marketing/crops.py                             # crop boxes of menus and tooltips
node tools/marketing/compat.mjs                              # the compatibility card's data, from the live reports
NODE_PATH="$(npm root -g)" node marketing/source/render.cjs  # every image, or name the ones to render
```

- `capture.mjs` needs Xvfb, ImageMagick, ffmpeg, python3 with python-xlib and portablemc (see its header);
  `CAPTURE_SCENES="mailbox block"` retakes single scenes without touching the console files.
- `render.cjs` needs Playwright with Chromium. The fonts are Outfit and Inter (SIL Open Font License 1.1, in
  `source/fonts/`); the script uses no other fonts.
- `source/backdrop.jpg` is the blurred background scene, a 1280×720 copy of `screenshots/scenery.jpg`.
- `source/compat.json` summarises the reports of the 2.0.0 live matrix (`tools/smoke`, eight targets).
- The configuration card prints the current `mailbox.tabs.inbox` lines of `gui.yml` and the `notify` title lines of
  `messages.yml`; the daily card prints days 1 to 3 of the bundled `daily.yml`. The Discord card is a preview of the
  embed DonutMail posted for the launch campaign (`source/console/discord-campaign.json`), drawn in Discord's dark
  theme; it was posted to a local endpoint, not to Discord.

## Gallery uploads

For the CurseForge and Modrinth galleries, in this order:

| # | Image | Title | Description |
| --- | --- | --- | --- |
| 1 | `images/card-mailbox.png` | The Mailbox | Inbox, Parcels, Sent and Rewards tabs; new mail glows; preview, claim, or Claim All. |
| 2 | `images/card-parcels.png` | Send Parcels | Send items to any player, online or offline, with a note, and take them back until claimed. |
| 3 | `images/card-cash-on-delivery.png` | Cash on Delivery | The buyer sees the exact items, then pays or declines; the seller gets the money as mail. |
| 4 | `images/card-campaigns.png` | Campaigns | One mail for every player, including players who join later, claimed once each. |
| 5 | `images/card-daily.png` | Daily Rewards | A 7-day streak calendar, delivered to the Rewards tab on a new day. |
| 6 | `images/card-codes.png` | Reward Codes | `/mail redeem` codes with total uses, uses per player and an expiry. |
| 7 | `images/card-notifications.png` | Notifications | A title, action bar, chat line and sound when mail arrives, and a summary on join. |
| 8 | `images/card-safety.png` | Built to Be Safe | Every claim, payment and return is one conditional update that exactly one action wins. |
| 9 | `images/card-integrations.png` | Integrations | PlaceholderAPI, a developer API with six events, NuVotifier votes and a Discord webhook. |
| 10 | `images/card-staff.png` | Staff Tools | Inspect and remove mail, check health, restore streaks, set mailbox blocks; an audit log. |
| 11 | `images/card-network.png` | Several Servers | One MySQL or MariaDB database: mail, campaigns, codes and daily rewards follow players. |
| 12 | `images/card-config.png` | Configuration | Every menu slot, material and word, with MiniMessage, & codes and hex colors. |
| 13 | `images/card-compatibility.png` | Compatibility | Paper and Folia 1.20.1 to 26.3 from one jar, tested live on six servers, a network and an upgrade. |
| 14 | `images/screenshots/inbox.jpg` | The Inbox | Server mail, payments, a returned parcel and two campaigns. |
| 15 | `images/screenshots/cod-preview.jpg` | Pay and Collect | The exact items above, and your balance before and after paying. |
| 16 | `images/screenshots/compose.jpg` | The Parcel Menu | Recipient, note, cash-on-delivery price and postage before you send. |
| 17 | `images/screenshots/paid-chat.jpg` | Paid | The seller is told the moment the buyer pays. |
| 18 | `images/screenshots/rewards.jpg` | The Rewards Tab | The streak calendar, the daily reward and three vote rewards. |
| 19 | `images/screenshots/join-summary.jpg` | Welcome Back | Everything waiting for you, with a clickable link, when you join. |
| 20 | `images/screenshots/live-title.jpg` | New Mail | A title, the action bar and a chat line the moment a parcel arrives. |
| 21 | `images/screenshots/mailbox-block.jpg` | A Mailbox at Spawn | A lectern made into a mailbox with `/mail admin setblock`. |
| 22 | `images/screenshots/admin-view.jpg` | Staff View | A player's mail, waiting and history, with shift-right-click to remove. |

The featured image is the banner (`images/banner.png`); the project icon is `images/icon.png`.

## Wording

Say what the plugin does, in plain words, with numbers that are 2.0.0 defaults from `config.yml`, `daily.yml` or the
code, or results from the 2.0.0 patch notes. Never "first" or "only". Do not use other servers', plugins' or games'
names, logos or art in listing images or titles, and do not compare against named competitors. Do not claim what the
patch notes list as not tested: Purpur, versions between the tested ones, MySQL itself (MariaDB was tested), a real
Discord channel. The made-up player names in the screenshots (Moonbeam, Kiri, Willow, Draxen) and vote sites are not
real.

## Listing conventions

The line right under the banner holds nothing but functional links (Docs, Discord, Source and issues). Promotion follows the
description: the license paragraph, the "Running a server?" line, and the "More from NightBeam Studio" block with the
Follow line, all as in DonutOrders and DonutAuctionHouse. There is no badge row and no Pro banner. Summaries do not
repeat the project title.
