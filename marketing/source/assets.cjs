// The listing artwork. Each entry returns { html, width, height, transparent?, file? } for render.cjs.
// Crops are pixel boxes [x, y, w, h] of the 1920x1080 captures in images/screenshots/ (GUI scale 3), measured by
// tools/marketing/crops.py into crops.json: the chest part of each menu joined with the hovered slot's tooltip, and
// "<name>-tip" for a tooltip alone.
const fs = require('fs');
const path = require('path');
const r = require('./render.cjs');

const HEADERS = {
  mailbox: 'The Mailbox', parcels: 'Parcels', cod: 'Cash on Delivery', safety: 'Built to Be Safe', campaigns: 'Campaigns',
  daily: 'Daily Rewards', codes: 'Reward Codes', notifications: 'Notifications', integrations: 'Integrations',
  staff: 'Staff Tools', network: 'Several Servers', config: 'Configuration', compatibility: 'Compatibility',
  upgrade: 'Upgrading from 1.x', commands: 'Commands & Permissions', faq: 'Questions', gallery: 'Gallery',
};

const MEASURED = path.join(__dirname, 'crops.json');
const CROP = Object.assign({
  // Chat strips (bottom left), the title over the world and the mailbox block are fixed regions.
  'join-chat': [0, 674, 990, 292],
  'paid-lines': [0, 874, 1000, 90],
  'campaign-line': [0, 903, 800, 60],
  'live-scene': [0, 360, 1320, 620],
  'block-scene': [360, 140, 1200, 840],
}, fs.existsSync(MEASURED) ? JSON.parse(fs.readFileSync(MEASURED, 'utf8')) : {});
const FILES = {
  'join-chat': 'join-summary', 'paid-lines': 'paid-chat', 'campaign-line': 'campaign-chat', 'live-scene': 'live-title',
  'block-scene': 'mailbox-block',
};

/** Width and height of a crop scaled to fit maxW x maxH. */
const fitted = (name, maxW, maxH) => {
  const box = CROP[name] || [0, 0, 16, 9];
  const width = Math.round(Math.min(maxW, maxH * box[2] / box[3]));
  return [width, Math.round(width * box[3] / box[2])];
};
const fileOf = (name) => (FILES[name] || name.replace(/-tip$/, '')) + '.jpg';

/** A framed crop of a capture, as wide as possible within maxW x maxH. */
const shotArt = (name, maxW, maxH, style = '') => {
  if (!CROP[name]) {
    // A capture that is missing (or not measured yet) shows as an empty frame, so the other assets still render.
    console.warn(`no crop for ${name}: run tools/marketing/capture.mjs and crops.py`);
    const [w, h] = fitted(name, maxW, maxH);
    return r.framed(`<div style="width:${w}px;height:${h}px"></div>`, style);
  }
  return r.framed(r.region(fileOf(name), ...CROP[name], fitted(name, maxW, maxH)[0]), style);
};

// The card art area is 850 x 780. main() sits top-left, inset() bottom-right; each label is pinned to its frame's corner.
const ART_W = 850;
const ART_H = 780;
const main = (name, maxW, maxH, top, text, kind = '', left = 0) =>
  `${shotArt(name, maxW, maxH, `position:absolute;left:${left}px;top:${top}px`)}${text ? r.label(text, `left:${left + 24}px;top:${top - 22}px`, kind) : ''}`;
const inset = (name, maxW, maxH, bottom, text, kind = 'gold', right = -20) => {
  const [w, h] = fitted(name, maxW, maxH);
  const left = ART_W - right - w;
  const top = ART_H - bottom - h;
  // Thin strips (chat lines) get the label above the frame, so it covers no text.
  const labelTop = h < 200 ? top - 50 : top - 22;
  return `${shotArt(name, maxW, maxH, `position:absolute;left:${left}px;top:${top}px`)}${text ? r.label(text, `left:${left + 24}px;top:${labelTop}px`, kind) : ''}`;
};

const card = (key, options) => () => ({ width: 1600, height: 900, file: `card-${key}.png`, html: r.card(options) });
const hl = (text, color) => `<span style="color:var(--${color})">${text}</span>`;
const consoleLines = (name) => r.consoleText(name).map(r.escapeHtml).join('\n');

const assets = {
  icon: () => ({ width: 512, height: 512, html: r.page(512, 512, `
    <div style="position:absolute;inset:0;background:radial-gradient(circle at 50% 42%,#1d2a3f 0%,#101521 58%,#090b11 100%)"></div>
    ${r.sprinkles(26, 0.55)}
    <div style="position:absolute;left:46px;top:46px;width:420px;height:420px;border-radius:50%;
      background:radial-gradient(circle,rgba(94,184,255,.30) 0%,rgba(94,184,255,0) 66%)"></div>
    <div style="position:absolute;left:64px;top:56px;filter:drop-shadow(0 18px 26px rgba(0,0,0,.6))">${r.donutSvg(384)}</div>
    <div style="position:absolute;right:40px;bottom:40px;display:flex;align-items:center;gap:10px;padding:12px 24px 11px 20px;border-radius:999px;
      background:linear-gradient(180deg,#ffd76a,#f0a92e);box-shadow:0 10px 26px rgba(0,0,0,.55),inset 0 -3px 0 rgba(120,70,0,.35)">
      ${r.envelopeSvg(44)}
      <span style="font-family:DmDisplay;font-weight:900;font-size:44px;line-height:1;color:#3b2203;letter-spacing:.04em">MAIL</span></div>`) }),

  divider: () => ({ width: 1400, height: 40, transparent: true, html: r.divider() }),

  banner: () => ({ width: 1800, height: 600, html: r.page(1800, 600, `
    ${r.backdrop(0.5, 6)}
    ${r.sprinkles(60, 0.4)}
    <div style="position:absolute;left:870px;top:-40px;width:1120px;height:700px;perspective:1700px">
      <div style="transform:rotateY(-17deg) rotateX(4deg) translateZ(0);transform-origin:left center;margin-top:62px">
        ${shotArt('inbox', 860, 500, 'display:inline-block')}
      </div>
    </div>
    <div style="position:absolute;inset:0;background:linear-gradient(90deg,rgba(14,10,20,.95) 0%,rgba(14,10,20,.86) 40%,rgba(14,10,20,.15) 62%,rgba(14,10,20,0) 100%)"></div>
    <div style="position:absolute;left:90px;top:0;bottom:0;width:820px;display:flex;flex-direction:column;justify-content:center;z-index:2">
      <div class="kicker"><span class="dot"></span>${r.RELEASE.version} · ${r.RELEASE.name}</div>
      <div style="display:flex;align-items:center;gap:26px;margin:22px 0 6px">
        <div style="width:118px;height:118px;filter:drop-shadow(0 12px 20px rgba(0,0,0,.6))">${r.donutSvg(118)}</div>
        <div class="display" style="font-size:104px;line-height:.92">Donut<br><span class="grad">Mail</span></div>
      </div>
      <div class="rule" style="width:72%;margin:26px 0 24px"></div>
      <div style="font-size:31px;line-height:1.35;color:var(--text)">Parcels, cash on delivery, campaigns and daily<br>rewards, in a mailbox nobody can dupe. <b class="hi">Free.</b></div>
      <div style="margin-top:30px;display:flex;align-items:center">
        <span class="pill"><i></i>Paper</span><span class="pill"><i></i>Folia</span>
        <span style="font-family:DmDisplay;font-weight:700;font-size:26px;color:var(--gold-hi);margin-left:12px">${r.RANGE}</span>
      </div>
    </div>`) }),

  mailbox: card('mailbox', {
    kicker: 'The Mailbox', title: 'Every mail in one place',
    points: [
      '<b class="hi">Inbox, Parcels, Sent and Rewards</b> tabs',
      'New mail glows and is marked “● New”',
      'Right-click to preview, left-click to claim',
      '<b class="hi">Claim All</b> takes everything that fits',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('inbox', 850, 460, 30, 'Inbox')}
      ${inset('mailbox-profile', 520, 272, 0, 'Your counts and streak')}
    </div>`,
  }),

  parcels: card('parcels', {
    kicker: 'Parcels', title: 'Send anything to anyone',
    points: [
      'Drop items in, pick any player, <b class="hi">online or offline</b>',
      'Add a note; player names tab-complete',
      'Take a parcel back until it is claimed',
      'Unclaimed parcels come back after 7 days',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('compose', 600, 450, 30, 'The parcel menu')}
      ${inset('preview-info', 430, 345, 0, 'What the recipient sees')}
    </div>`,
  }),

  cod: card('cash-on-delivery', {
    kicker: 'Cash on Delivery', title: 'Trade safely, even offline',
    points: [
      'Set a price; the buyer sees the <b class="hi">exact items</b> before paying',
      'Pay and collect, or decline and the items go back',
      'The money reaches the seller as mail',
      'A double click on Pay charges once',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('cod-decline', 620, 400, 30, 'Pay or decline')}
      ${inset('cod-preview-tip', 360, 190, 200, 'Balance before and after')}
      ${inset('paid-lines', 760, 100, 0, 'The seller is told', 'mint', 70)}
    </div>`,
  }),

  safety: card('safety', {
    kicker: 'Built to Be Safe', title: 'Nothing lost, nothing doubled',
    points: [
      'Every claim, payment and return is one conditional update',
      'Exactly one action wins, on one server or several',
      'A claim cut off by a crash is finished or undone on the next join',
      'Declined, expired and taken-back parcels go back to the sender',
    ],
    art: (() => {
      const player = (name, action, color, result, ok) => `<div class="panel" style="padding:20px 24px;width:350px">
        <div style="font-family:DmDisplay;font-weight:700;font-size:15px;letter-spacing:.16em;text-transform:uppercase;color:var(--dim)">The same parcel</div>
        <div style="font-family:DmDisplay;font-weight:800;font-size:29px;color:${color};margin:4px 0 10px">${name} ${action}</div>
        <div style="font-size:20px;color:${ok ? 'var(--mint)' : 'var(--red)'};font-weight:600">${result}</div></div>`;
      const k = (word) => hl(word, 'accent-hi');
      const s = (word) => hl(word, 'gold-hi');
      const sql = `${hl('-- Pay', 'faint')}
${k('UPDATE')} donutmail_mail ${k('SET')} status = ${s("'CLAIMING'")}, claim_token = ?
 ${k('WHERE')} id = ? ${k('AND')} status = ${s("'COD'")}
${hl('-- Take back', 'faint')}
${k('UPDATE')} donutmail_mail ${k('SET')} status = ${s("'RETURNED'")}, reason = ?
 ${k('WHERE')} id = ? ${k('AND')} status ${k('IN')} (${s("'PENDING'")}, ${s("'COD'")})
   ${k('AND')} cod_paid_at = 0`;
      return `<div style="position:relative;height:760px;display:flex;flex-direction:column;justify-content:center;gap:24px">
        <div style="display:flex;justify-content:space-between">
          ${player('Kiri', 'clicks Pay', 'var(--hi)', '1 row updated → pays, gets the items', true)}
          ${player('Moonbeam', 'takes it back', 'var(--hi)', '0 rows → “already paid”, nothing moves', false)}
        </div>
        <div style="display:flex;justify-content:space-around;font-family:DmDisplay;font-weight:800;font-size:34px;color:var(--accent)"><span>↓</span><span>↓</span></div>
        ${r.codePanel('One parcel, one winner (YAML, SQLite or MySQL)', sql, { width: 760, size: 19 })}
        ${r.stats([['Winners per parcel', '1'], ['Racing attempts tested', '6 to 10'], ['Items dropped', '0']])}
      </div>`;
    })(),
  }),

  campaigns: card('campaigns', {
    kicker: 'Campaigns', title: 'One mail for every player',
    points: [
      'Stored once, <b class="hi">claimed once per player</b>',
      'Players who join later see it too',
      'Money, items, commands and vouchers',
      'Optional start, end and permission',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('inbox-campaign-tip', 470, 370, 30, 'In every mailbox')}
      <div style="position:absolute;right:-20px;top:440px">${r.codePanel('Console', `${hl('&gt;', 'faint')} mail announce name:launch money:100 end:7d
  &lt;gold&gt;Launch week&lt;/gold&gt; thanks for playing
${hl(r.escapeHtml(r.consoleText('announce.txt')[0] || ''), 'mint')}`, { width: 640, size: 17 })}</div>
      ${inset('campaign-line', 700, 50, 10, 'Announced to everyone online')}
    </div>`,
  }),

  daily: card('daily', {
    kicker: 'Daily Rewards', title: 'A reason to log in every day',
    points: [
      'A <b class="hi">7-day streak calendar</b> on the Rewards tab',
      'Items, money, commands and vouchers per day',
      'Delivered when a player joins on a new day',
      'One reward per player per day, across servers too',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('rewards', 800, 410, 30, 'The streak calendar')}
      <div style="position:absolute;right:-20px;bottom:0">${r.codePanel('daily.yml (bundled)', r.yaml(r.fileLines('src/main/resources/daily.yml', 28, 38)), { width: 640, size: 16 })}</div>
    </div>`,
  }),

  codes: card('codes', {
    kicker: 'Reward Codes', title: 'Codes for events and streams',
    points: [
      '<code class="inline">/mail redeem SPRING</code>, or the Redeem button',
      'Total uses, uses per player and an expiry',
      'Tested: 10 players racing for 3 uses got exactly 3 rewards',
      'A cooldown between attempts stops guessing',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('redeem', 760, 470, 30, 'The Rewards tab')}
      <div style="position:absolute;right:-20px;bottom:0">${r.codePanel('Console', `${hl('&gt;', 'faint')} mail code create SPRING uses:2 per-player:1
  money:50 Spring reward
${hl(r.escapeHtml(r.consoleText('code-create.txt')[0] || ''), 'mint')}`, { width: 600, size: 17 })}</div>
    </div>`,
  }),

  notifications: card('notifications', {
    kicker: 'Notifications', title: 'Nobody misses their mail',
    points: [
      'A title, action bar, chat line and sound when mail arrives',
      'A summary with a clickable link on join',
      'Payments, returns and campaigns are announced too',
      'Also for mail sent on another server',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('live-scene', 830, 440, 30, 'The moment it arrives')}
      ${inset('join-chat', 600, 175, 0, 'When you join')}
    </div>`,
  }),

  integrations: card('integrations', {
    kicker: 'Integrations', title: 'Plays well with others',
    points: [
      '<b class="hi">PlaceholderAPI</b>: 12 placeholders',
      'A developer API with 6 events',
      'NuVotifier votes become rewards',
      'New campaigns posted to Discord',
    ],
    art: (() => {
      // The embed DonutMail posted for the launch campaign (console/discord-campaign.json), drawn like Discord's dark theme.
      const post = r.consoleJson('discord-campaign.json');
      const embed = post.embeds[0];
      const color = '#' + embed.color.toString(16).padStart(6, '0');
      const field = (f) => `<div style="min-width:170px"><div style="font-weight:700;font-size:18px;color:#f2f3f5">${r.escapeHtml(f.name)}</div>
        <div style="font-size:18px;color:#dbdee1">${f.name === 'Ends' ? 'in 7 days' : r.escapeHtml(f.value)}</div></div>`;
      const card = `<div style="width:540px;border-radius:10px;background:#2b2d31;padding:24px 24px 16px;border-left:5px solid ${color};font-family:DmText;box-shadow:0 26px 70px rgba(0,0,0,.6)">
        <div style="font-size:17px;color:#f2f3f5;font-weight:700;margin-bottom:10px">${r.escapeHtml(post.username)} <span style="font-size:12px;background:#5865f2;color:#fff;border-radius:4px;padding:1px 5px;margin-left:4px;vertical-align:2px">APP</span></div>
        <div style="font-size:21px;color:#f2f3f5;font-weight:700;margin-bottom:8px">${r.escapeHtml(embed.title)}</div>
        <div style="font-size:18px;color:#dbdee1;line-height:1.45;margin-bottom:14px">${r.escapeHtml(embed.description)}</div>
        <div style="display:flex;gap:18px;flex-wrap:wrap;margin-bottom:14px">${embed.fields.map(field).join('')}</div>
        <div style="font-size:14px;color:#949ba4">${r.escapeHtml(embed.footer.text)}</div></div>`;
      const values = Object.fromEntries((r.consoleText('placeholders.txt')[0] || '').split(' ').map((pair) => pair.split('=')));
      const papi = [['unread', values.unread], ['parcels', values.parcels], ['cod', values.cod], ['daily_streak', values.streak], ['daily_next_day', values.next]]
        .map(([key, value]) => `${hl(`%donutmail_${key}%`, 'gold-hi')}${' '.repeat(26 - key.length)}${hl(value, 'sky')}`).join('\n');
      const api = `${hl('DonutMailAPI', 'accent-hi')}.deliver(Delivery.to(playerId)
    .sender(${hl('"Crates"', 'gold-hi')})
    .item(<span>new</span> ItemStack(Material.DIAMOND, ${hl('3', 'sky')}))
    .money(${hl('250', 'sky')}).rewardsTab().build());
${hl('// MailSendEvent, MailClaimEvent, ParcelPaidEvent,', 'faint')}
${hl('// ParcelReturnedEvent, DailyRewardEvent, CodeRedeemEvent', 'faint')}`;
      return `<div style="position:relative;height:780px">
        <div style="position:absolute;left:0;top:34px">${card}</div>
        ${r.label('Discord webhook', 'left:24px;top:8px', 'mint')}
        <div style="position:absolute;right:-20px;top:300px">${r.codePanel('PlaceholderAPI, live values', papi, { width: 480, size: 17 })}</div>
        <div style="position:absolute;left:0;bottom:0">${r.codePanel('Developer API', api, { width: 720, size: 17 })}</div>
      </div>`;
    })(),
  }),

  staff: card('staff', {
    kicker: 'Staff Tools', title: 'Tools for your staff',
    points: [
      '<code class="inline">/mail admin view</code>: inspect and remove',
      '<code class="inline">info</code>, <code class="inline">purge</code> and <code class="inline">streak</code> for upkeep',
      'Mailbox blocks: right-click to open',
      'An audit line for every send, claim and payment',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('admin-view', 850, 440, 30, 'Staff view')}
      <div style="position:absolute;right:-20px;bottom:0">${r.codePanel('Console · /mail admin info', consoleLines('admin-info.txt'), { width: 760, size: 15 })}</div>
    </div>`,
  }),

  network: card('network', {
    kicker: 'Several Servers', title: 'One mailbox, every server',
    points: [
      'Point every server at one <b class="hi">MySQL or MariaDB</b> database',
      'Mail reaches players on any server, with a live notice',
      'Campaigns, codes and daily rewards are shared',
      'Claims and payments stay single-winner',
    ],
    art: (() => {
      const net = r.compat().targets.find((t) => t.kind === 'network');
      const box = (name, detail, sub) => `<div class="panel" style="padding:18px 22px;width:300px">
        <div style="font-family:DmDisplay;font-weight:700;font-size:15px;letter-spacing:.16em;text-transform:uppercase;color:var(--dim)">${sub}</div>
        <div style="font-family:DmDisplay;font-weight:800;font-size:28px;color:var(--hi);margin:3px 0 8px">${name}</div>
        <div style="font-size:18px;color:var(--text)">${detail}</div></div>`;
      const db = `<div style="width:190px;text-align:center">
        <svg width="120" height="130" viewBox="0 0 120 130"><g fill="none" stroke="#5eb8ff" stroke-width="4">
          <ellipse cx="60" cy="22" rx="52" ry="16" fill="rgba(94,184,255,.12)"/><path d="M8 22v86c0 9 23 16 52 16s52-7 52-16V22"/>
          <path d="M8 52c0 9 23 16 52 16s52-7 52-16M8 80c0 9 23 16 52 16s52-7 52-16"/></g></svg>
        <div style="font-family:DmDisplay;font-weight:800;font-size:22px;color:var(--hi)">One database</div></div>`;
      const yamlText = r.yaml(['storage:', '  type: MYSQL', 'network:', "  server-id: 'survival-1'", '  poll-interval-seconds: 3']);
      return `<div style="position:relative;height:760px;display:flex;flex-direction:column;justify-content:center;gap:22px">
        <div style="display:flex;align-items:center;justify-content:space-between">
          ${box('survival-1', 'Kiri sends a parcel', 'Server')}
          ${db}
          ${box('survival-2', 'Moonbeam gets a live notice', 'Server')}
        </div>
        <div style="display:flex;gap:22px;align-items:stretch">
          ${r.codePanel('config.yml (every server)', yamlText, { width: 420, size: 18 })}
          <div class="panel" style="flex:1;padding:18px 22px;font-size:18px;line-height:1.5;color:var(--text)">
            <div style="font-family:DmDisplay;font-weight:700;font-size:15px;letter-spacing:.16em;text-transform:uppercase;color:var(--dim);margin-bottom:6px">Live test, ${net ? `${net.project} ${net.version}` : ''} × 2</div>
            ${net ? `${net.passed}/${net.steps} checks passed` : ''}: a parcel reached the other server with a live notice after 2.0 s (polling every second); pay against take-back had one winner every round; one campaign claim and one daily reward per player.</div>
        </div>
        ${r.stats([['Live notice', '2.0 s'], ['Winners per round', '1'], ['Database', 'MariaDB 10.11']])}
      </div>`;
    })(),
  }),

  config: card('config', {
    kicker: 'Configuration', title: 'Make it yours',
    points: [
      'Every slot, material and word in <code class="inline">gui.yml</code>',
      'Chat, titles and the action bar in <code class="inline">messages.yml</code>',
      'MiniMessage, &amp; codes and hex colors, mixed freely',
      'New settings arrive on update; yours are kept',
    ],
    art: `<div style="position:relative;height:780px">
      <div style="position:absolute;left:0;top:10px">${r.codePanel('gui.yml', r.yaml(r.fileLines('src/main/resources/gui.yml', 32, 37)), { width: 820, size: 17, wrap: true })}</div>
      <div style="position:absolute;left:0;top:318px">${r.codePanel('messages.yml', r.yaml(r.fileLines('src/main/resources/messages.yml', 95, 97)), { width: 600, size: 17 })}</div>
      ${inset('inbox', 600, 296, 0, 'In game')}
    </div>`,
  }),

  compatibility: card('compatibility', {
    kicker: 'Compatibility', title: 'Tested live on Paper and Folia',
    points: [
      'Paper and Folia, region-thread safe',
      `Minecraft ${r.RANGE}, one jar`,
      'Bots send, pay, decline and claim on each server',
      'Vault or VaultUnlocked for the money features',
    ],
    art: (() => {
      const data = r.compat();
      const targets = data ? data.targets : [];
      const tile = (t) => {
        const head = t.kind === 'network' ? `2 × ${t.project}` : t.kind === 'upgrade' ? `${t.project}, from 1.1.0` : t.project;
        const sub = t.kind === 'network' ? `one ${t.storage} database` : t.storage;
        return `<div class="panel" style="padding:14px 18px 12px;border-color:${t.ok ? 'rgba(73,214,160,.45)' : 'rgba(255,107,107,.6)'}">
        <div style="font-family:DmDisplay;font-weight:700;font-size:14px;letter-spacing:.14em;text-transform:uppercase;color:var(--dim)">${head}</div>
        <div style="font-family:DmDisplay;font-weight:800;font-size:31px;color:var(--hi);line-height:1.1">${t.version}</div>
        <div style="font-size:16px;color:${t.ok ? 'var(--mint)' : 'var(--red)'};font-weight:600;margin-top:3px">${t.ok ? '✔' : '✖'} ${t.passed}/${t.steps} checks · Java ${t.java}</div>
        <div style="font-size:15px;color:var(--dim);margin-top:2px">${sub}</div></div>`;
      };
      return `<div style="display:grid;grid-template-columns:repeat(3,1fr);gap:16px;width:800px">${targets.map(tile).join('')}</div>
        <div style="margin-top:18px;font-size:18px;color:var(--dim)">DonutMail ${data ? data.plugin : ''} · live runs of tools/smoke with mineflayer bots, ${data ? data.date : ''}</div>`;
    })(),
  }),
};

for (const [key, title] of Object.entries(HEADERS)) {
  assets[`header-${key}`] = () => ({ width: 1400, height: 120, transparent: true, html: r.header(title) });
}
module.exports = assets;
