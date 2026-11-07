// The flows the live tests run. Slots match the default gui.yml:
//   mailbox: tabs 2 inbox, 3 parcels, 5 sent, 6 rewards; mail in 9-44 (Rewards: calendar 10-16, mail 18-44);
//            47 Claim All, 49 Send a Parcel, 51 settings/redeem.
//   compose: items 0-44; 45 cancel, 47 recipient, 48 note, 49 send, 50 cash on delivery.
//   preview: 45 back; cash on delivery: 47 pay, 49 info, 51 decline; Sent: 51 take back.
import fs from 'node:fs';
import path from 'node:path';
import { Server, joinBot, prepareServer, pluginErrors, resetDatabase, sendVote, sleep, smokeConfig, webhookCatcher, lab } from './run.mjs';

const near = (a, b) => Math.abs(a - b) < 0.001;
const number = (line) => Number(String(line).trim().split(/\s+/).pop());

// ── Bot helpers ─────────────────────────────────────────────────────────────

function waitWindow(bot, trigger, timeout = 15_000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      bot.removeListener('windowOpen', onOpen);
      reject(new Error(`${bot.username}: no window opened`));
    }, timeout);
    const onOpen = (window) => {
      clearTimeout(timer);
      setTimeout(() => resolve(window), 700);
    };
    bot.once('windowOpen', onOpen);
    Promise.resolve(trigger()).catch(reject);
  });
}

function mark(bot) {
  bot.chatCursor = bot.chatLog.length;
}

async function waitChat(bot, pattern, timeout = 15_000) {
  const start = Date.now();
  if (bot.chatCursor === undefined) bot.chatCursor = 0;
  while (Date.now() - start < timeout) {
    for (let i = bot.chatCursor; i < bot.chatLog.length; i++) {
      if (pattern.test(bot.chatLog[i])) {
        bot.chatCursor = i + 1;
        return bot.chatLog[i];
      }
    }
    await sleep(100);
  }
  throw new Error(`${bot.username}: no chat matching ${pattern} (last: ${bot.chatLog.slice(-4).join(' | ')})`);
}

/** Chat and commands, paced so the vanilla spam limit never kicks a bot. */
async function say(bot, text) {
  const wait = (bot.lastSay || 0) + 1100 - Date.now();
  if (wait > 0) await sleep(wait);
  bot.lastSay = Date.now();
  bot.chat(text);
}

function slotOf(window, name, from = 9, to = 44) {
  for (let i = from; i <= to; i++) if (window.slots[i]?.name === name) return i;
  return -1;
}

function countIn(window, name, from = 9, to = 44) {
  let n = 0;
  for (let i = from; i <= to; i++) if (window.slots[i]?.name === name) n++;
  return n;
}

/** Readable text of a chat component (JSON string or object) for window dumps. */
function plainText(value) {
  if (value == null) return '';
  let node = value;
  if (typeof value === 'string') {
    try { node = JSON.parse(value); } catch { return value; }
  }
  if (typeof node === 'string') return node;
  if (Array.isArray(node)) return node.map(plainText).join('');
  if (node.type === 'compound' && node.value) {
    const simplify = (v) => (v && v.type ? (v.type === 'list' ? v.value.value.map(simplify) : v.type === 'compound'
      ? Object.fromEntries(Object.entries(v.value).map(([k, x]) => [k, simplify(x)])) : v.value) : v);
    return plainText(simplify(node));
  }
  let out = node.text ?? node[''] ?? (node.translate ? `[${node.translate}]` : '');
  if (Array.isArray(node.extra)) out += node.extra.map(plainText).join('');
  return out;
}

/** Writes what a menu shows (names and lore) to windows.txt, to review menus without a client. */
export function dumpWindow(window, label, file) {
  if (!file) return;
  const lines = [`== ${label} (${plainText(window.title)})`];
  for (let i = 0; i < window.inventoryStart; i++) {
    const item = window.slots[i];
    if (!item || item.name === 'gray_stained_glass_pane') continue;
    const name = item.customName ? plainText(item.customName) : item.displayName;
    const lore = (item.customLore || []).map(plainText);
    lines.push(`[${i}] ${item.name} x${item.count} | ${name}${lore.length ? '\n      ' + lore.join('\n      ') : ''}`);
  }
  fs.appendFileSync(file, lines.join('\n') + '\n\n');
}

function close(bot) {
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
}

async function openTab(bot, command) {
  close(bot);
  await sleep(300);
  return waitWindow(bot, () => say(bot, command));
}

/** Moves every stack of an item from the bot's inventory into the open compose menu. */
async function putIn(bot, window, name, target = 0) {
  let placed = 0;
  for (let i = 54; i <= 89; i++) {
    if (window.slots[i]?.name !== name) continue;
    await bot.clickWindow(i, 0, 0);
    await sleep(250);
    await bot.clickWindow(target + placed, 0, 0);
    await sleep(250);
    placed++;
  }
  if (placed === 0) throw new Error(`${bot.username} has no ${name} to put in`);
  return placed;
}

/** Opens the compose menu with /mail send, puts the items in and presses Send. Returns the chat answer. */
async function sendParcel(bot, to, item, { cod, expect = /Parcel sent to|cash on delivery\)/ } = {}) {
  close(bot);
  await sleep(300);
  const compose = await waitWindow(bot, () => say(bot, `/mail send ${to}${cod ? ` cod ${cod}` : ''}`));
  if (compose.slots[49]?.name !== 'lime_concrete') throw new Error(`compose menu: slot 49 is ${compose.slots[49]?.name}`);
  await putIn(bot, compose, item);
  mark(bot);
  await bot.clickWindow(49, 0, 0);
  return waitChat(bot, expect);
}

/** Runs one named check; a failure is recorded and the scenario continues where it can. */
async function check(step, label, body) {
  try {
    const detail = await body();
    step(label, detail !== false, typeof detail === 'string' ? detail : '');
    return detail !== false;
  } catch (error) {
    step(label, false, error.message);
    return false;
  }
}

function expectEq(actual, expected, what) {
  if (typeof expected === 'number' ? !near(actual, expected) : actual !== expected) {
    throw new Error(`${what}: expected ${expected}, got ${actual}`);
  }
  return `${what} ${actual}`;
}

async function balance(server, name) {
  return number((await server.lab(`balance ${name}`)).split(' via ')[0]);
}

async function count(server, name, material) {
  return number(await server.lab(`count ${name} ${material}`));
}

async function mails(server, name) {
  return (await server.lab(`mails ${name}`)).split(' ').slice(2).join(' ');
}

async function waitUntil(fn, timeout, label) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    if (await fn()) return true;
    await sleep(2000);
  }
  throw new Error(`timed out: ${label}`);
}

// ── One server, every feature ───────────────────────────────────────────────

export async function coreScenario({ target, step, port, java, result }) {
  if (target.storage === 'MYSQL') resetDatabase('donutmail_live');
  const extras = target.extras || [];
  const extraJars = [];
  if (extras.includes('votes')) extraJars.push(await (await import('./run.mjs')).fetchVotifier());
  const prepared = await prepareServer(target, target.name, port, { storage: target.storage, database: 'donutmail_live', extras, jars: extraJars });
  const windows = path.join(prepared.dir, 'windows.txt');
  result.server = `${target.project} ${target.version} build ${prepared.build} (sha256 ${prepared.sha256})`;
  const server = new Server(prepared.dir, java, prepared.jar, port);
  const hook = extras.includes('discord') ? webhookCatcher() : null;
  const bots = {};
  const join = async (name) => {
    bots[name] = await joinBot(name, target.bot, port, path.join(prepared.dir, `chat-${name}.log`));
    return bots[name];
  };
  const leave = async (name) => {
    bots[name]?.quit();
    delete bots[name];
    await sleep(1500);
  };
  try {
    await server.start();
    step('DonutMail enabled', server.lines.some((l) => /DonutMail enabled using .* storage on (Paper|Folia)/.test(l)),
      (server.lines.find((l) => /DonutMail enabled using/.test(l)) || '').replace(/.*\]: /, '').slice(0, 140));
    await check(step, 'public API available', async () => (await server.lab('api')).includes('available=true'));
    step('PlaceholderAPI expansion registered', server.lines.some((l) => /placeholders registered/.test(l)));
    await check(step, 'economy', async () => (await server.lab('balance Nobody')).split(' via ')[1]);

    // Carol joins once, so she is a known offline player later.
    const carolFirst = await join('Carol');
    await waitChat(carolFirst, /Daily reward/, 15_000).catch(() => null);
    await leave('Carol');
    const alice = await join('Alice');
    const bob = await join('Bob');
    step('bots joined', true, target.bot);
    await server.lab('money Alice 0');
    await server.lab('money Bob 1000');

    await check(step, 'daily reward delivered on first join (day 1)', async () => (await waitChat(bob, /Daily reward day 1/, 20_000)).slice(0, 70));
    await check(step, 'daily reward claimed from the Rewards tab', async () => {
      const rewards = await openTab(bob, '/mail rewards');
      dumpWindow(rewards, 'Rewards tab (day 1 waiting)', windows);
      const calendar = countIn(rewards, 'iron_ingot', 10, 16) + countIn(rewards, 'gold_ingot', 10, 16) + countIn(rewards, 'diamond', 10, 16);
      const slot = slotOf(rewards, 'sunflower', 18, 44);
      if (slot < 0) throw new Error('no daily reward in the Rewards tab');
      mark(bob);
      await bob.clickWindow(slot, 0, 0);
      await waitChat(bob, /Claimed/);
      await sleep(500);
      return `${expectEq(await count(server, 'Bob', 'IRON_INGOT'), 16, 'iron')}, calendar icons ${calendar}`;
    });

    // Online parcel through the compose menu.
    await check(step, 'parcel sent through the compose menu (online)', async () => {
      await server.lab('give Alice DIAMOND 3');
      await sleep(400);
      mark(bob);
      const answer = await sendParcel(alice, 'Bob', 'diamond');
      await waitChat(bob, /sent you a parcel/);
      return answer.slice(0, 60);
    });
    await check(step, 'parcel claimed from the Parcels tab', async () => {
      const parcels = await openTab(bob, '/mail parcels');
      const slot = slotOf(parcels, 'chest');
      if (slot < 0) throw new Error('parcel not listed');
      mark(bob);
      await bob.clickWindow(slot, 0, 0);
      await waitChat(bob, /Claimed/);
      await sleep(500);
      return expectEq(await count(server, 'Bob', 'DIAMOND'), 3, 'Bob diamonds');
    });

    // A cash-on-delivery parcel that nobody answers: it must come back to Alice when it expires.
    const expiring = await server.lab('parcel Alice Bob BONE 3 50');
    const expiringSent = Date.now();
    step('expiring cash-on-delivery parcel created (API)', /SENT/.test(expiring), expiring);

    // Offline parcel: Carol is offline, gets it when she joins.
    await check(step, 'parcel to an offline player', async () => {
      await sleep(3200);
      await server.lab('give Alice GOLD_INGOT 5');
      await sleep(400);
      return (await sendParcel(alice, 'Carol', 'gold_ingot')).slice(0, 60);
    });
    const carol = await join('Carol');
    await check(step, 'join summary lists the waiting parcel (clickable)', async () => (await waitChat(carol, /parcel\(s\) waiting/, 15_000)).trim());
    await check(step, 'offline parcel claimed after joining', async () => {
      const parcels = await openTab(carol, '/mail parcels');
      const slot = slotOf(parcels, 'chest');
      if (slot < 0) throw new Error('parcel not listed');
      mark(carol);
      await carol.clickWindow(slot, 0, 0);
      await waitChat(carol, /Claimed/);
      await sleep(500);
      return expectEq(await count(server, 'Carol', 'GOLD_INGOT'), 5, 'Carol gold');
    });

    // Cash on delivery: pay.
    await check(step, 'cash-on-delivery parcel sent (compose, cod 250)', async () => {
      await sleep(3200);
      await server.lab('give Alice EMERALD 4');
      await sleep(400);
      mark(bob);
      const answer = await sendParcel(alice, 'Bob', 'emerald', { cod: 250 });
      await waitChat(bob, /cash on delivery/);
      return answer.slice(0, 70);
    });
    await check(step, 'recipient previews and pays (money to sender, receipt)', async () => {
      const parcels = await openTab(bob, '/mail parcels');
      dumpWindow(parcels, 'Parcels tab with a cash-on-delivery parcel', windows);
      const slot = slotOf(parcels, 'ender_chest');
      if (slot < 0) throw new Error('cash-on-delivery parcel not listed');
      const preview = await waitWindow(bob, () => bob.clickWindow(slot, 0, 0));
      dumpWindow(preview, 'Cash-on-delivery preview', windows);
      if (preview.slots[0]?.name !== 'emerald') throw new Error(`preview shows ${preview.slots[0]?.name}`);
      if (preview.slots[47]?.name !== 'emerald_block') throw new Error(`pay button is ${preview.slots[47]?.name}`);
      mark(bob);
      mark(alice);
      await bob.clickWindow(47, 0, 0);
      await waitChat(bob, /You paid/);
      await waitChat(alice, /paid .*for your parcel/);
      await sleep(800);
      const parts = [expectEq(await balance(server, 'Bob'), 750, 'Bob balance'), expectEq(await count(server, 'Bob', 'EMERALD'), 4, 'Bob emeralds')];
      const inbox = await openTab(alice, '/mail');
      const receipt = slotOf(inbox, 'emerald');
      if (receipt < 0) throw new Error('no receipt in the sender\'s inbox');
      mark(alice);
      await alice.clickWindow(receipt, 0, 0);
      await waitChat(alice, /Claimed/);
      await sleep(800);
      parts.push(expectEq(await balance(server, 'Alice'), 250, 'Alice balance'));
      return parts.join(', ');
    });

    // Cash on delivery: decline.
    await check(step, 'recipient declines; items return to the sender', async () => {
      await sleep(3200);
      await server.lab('give Alice LAPIS_LAZULI 6');
      await sleep(400);
      await sendParcel(alice, 'Bob', 'lapis_lazuli', { cod: 100 });
      const parcels = await openTab(bob, '/mail parcels');
      const slot = slotOf(parcels, 'ender_chest');
      const preview = await waitWindow(bob, () => bob.clickWindow(slot, 0, 0));
      if (preview.slots[51]?.name !== 'redstone_block') throw new Error(`decline button is ${preview.slots[51]?.name}`);
      mark(bob);
      mark(alice);
      await bob.clickWindow(51, 0, 0);
      await waitChat(bob, /declined the parcel/);
      await waitChat(alice, /came back/);
      const inbox = await openTab(alice, '/mail');
      const back = slotOf(inbox, 'barrel');
      if (back < 0) throw new Error('returned parcel not in the sender\'s inbox');
      mark(alice);
      await alice.clickWindow(back, 0, 0);
      await waitChat(alice, /Claimed/);
      await sleep(500);
      return `${expectEq(await count(server, 'Alice', 'LAPIS_LAZULI'), 6, 'Alice lapis back')}, ${expectEq(await balance(server, 'Bob'), 750, 'Bob not charged')}`;
    });

    // The same player clicking Pay twice: one payment.
    await check(step, 'double click on Pay charges once', async () => {
      await server.lab('parcel Alice Bob QUARTZ 5 100');
      const parcels = await openTab(bob, '/mail parcels');
      const slot = slotOf(parcels, 'ender_chest');
      await waitWindow(bob, () => bob.clickWindow(slot, 0, 0));
      mark(bob);
      await Promise.all([bob.clickWindow(47, 0, 0), bob.clickWindow(47, 0, 0)]);
      await waitChat(bob, /You paid/);
      await sleep(2500);
      return `${expectEq(await balance(server, 'Bob'), 650, 'Bob balance')}, ${expectEq(await count(server, 'Bob', 'QUARTZ'), 5, 'quartz')}`;
    });

    // Two bots race for one cash-on-delivery parcel: the recipient pays while the sender takes it back.
    await check(step, 'pay vs take back at the same moment: one winner', async () => {
      await server.lab('parcel Alice Bob AMETHYST_SHARD 7 200');
      const parcels = await openTab(bob, '/mail parcels');
      const codSlot = slotOf(parcels, 'ender_chest');
      const bobPreview = await waitWindow(bob, () => bob.clickWindow(codSlot, 0, 0));
      const sent = await openTab(alice, '/mail sent');
      dumpWindow(sent, 'Sent tab', windows);
      const alicePreview = await waitWindow(alice, () => alice.clickWindow(9, 0, 0));
      dumpWindow(alicePreview, 'Sent parcel preview (take back)', windows);
      if (bobPreview.slots[47]?.name !== 'emerald_block' || alicePreview.slots[51]?.name !== 'hopper_minecart') {
        throw new Error(`buttons ${bobPreview.slots[47]?.name}/${alicePreview.slots[51]?.name}`);
      }
      mark(bob);
      mark(alice);
      await Promise.all([bob.clickWindow(47, 0, 0), alice.clickWindow(51, 0, 0)]);
      await sleep(4000);
      const paid = bob.chatLog.slice(bob.chatCursor).some((l) => /You paid/.test(l));
      const recalled = alice.chatLog.slice(alice.chatCursor).some((l) => /is back in your mailbox/.test(l));
      if (paid === recalled) throw new Error(`paid=${paid} recalled=${recalled}`);
      const shards = await count(server, 'Bob', 'AMETHYST_SHARD');
      const bobBalance = await balance(server, 'Bob');
      const aliceMail = await mails(server, 'Alice');
      if (paid) {
        expectEq(shards, 7, 'buyer shards');
        expectEq(bobBalance, 450, 'buyer balance');
        if (!/RECEIPT/.test(aliceMail)) throw new Error(`no receipt: ${aliceMail}`);
      } else {
        expectEq(shards, 0, 'buyer shards');
        expectEq(bobBalance, 650, 'buyer balance');
        if (!/RETURN/.test(aliceMail)) throw new Error(`no returned parcel: ${aliceMail}`);
      }
      return `${paid ? 'payment' : 'take-back'} won; the other was refused`;
    });

    // The parcel menu's own buttons: recipient, note and price typed in chat (chat prompts).
    await check(step, 'compose menu buttons: recipient, note and price through chat', async () => {
      await sleep(3200);
      await server.lab('money Bob 1000');
      await server.lab('give Alice COAL 7');
      await sleep(400);
      const box = await openTab(alice, '/mail');
      const compose = await waitWindow(alice, () => alice.clickWindow(49, 0, 0));
      if (compose.slots[47]?.name !== 'player_head') throw new Error(`recipient button is ${compose.slots[47]?.name}`);
      mark(alice);
      let window = await waitWindow(alice, async () => {
        await alice.clickWindow(47, 0, 0);
        await waitChat(alice, /name of the player/);
        await say(alice, 'Bob');
      });
      window = await waitWindow(alice, async () => {
        await alice.clickWindow(48, 0, 0);
        await waitChat(alice, /short note/);
        await say(alice, 'Payment for the coal, thanks!');
      });
      window = await waitWindow(alice, async () => {
        await alice.clickWindow(50, 0, 0);
        await waitChat(alice, /cash-on-delivery price/);
        await say(alice, '75');
      });
      await putIn(alice, window, 'coal');
      await sleep(400);
      dumpWindow(alice.currentWindow, 'Compose menu (recipient, note, price set)', windows);
      mark(alice);
      mark(bob);
      await alice.clickWindow(49, 0, 0);
      const answer = await waitChat(alice, /Parcel sent to Bob for \$75/);
      await waitChat(bob, /for \$75/);
      const parcels = await openTab(bob, '/mail parcels');
      const slot = slotOf(parcels, 'ender_chest');
      const preview = await waitWindow(bob, () => bob.clickWindow(slot, 0, 0));
      mark(bob);
      await bob.clickWindow(47, 0, 0);
      await waitChat(bob, /You paid/);
      await sleep(800);
      return `${answer.replace(/.*» /, '')}; ${expectEq(await count(server, 'Bob', 'COAL'), 7, 'coal')}, ${expectEq(await balance(server, 'Bob'), 925, 'Bob balance')}`;
    });

    await check(step, 'Claim All button', async () => {
      await server.lab('deliver Bob BREAD 1 4');
      await server.lab('deliver Bob BREAD 1 6');
      await sleep(800);
      const inbox = await openTab(bob, '/mail');
      mark(bob);
      await bob.clickWindow(47, 0, 0);
      const line = await waitChat(bob, /Claimed \d+ mail/);
      await sleep(600);
      return `${line.replace(/.*» /, '')} ${expectEq(await count(server, 'Bob', 'BREAD'), 10, 'bread')}`;
    });

    // Full inventory: partial claim.
    await check(step, 'full inventory: partial claim, the rest stays', async () => {
      await server.lab('clear Bob');
      await server.lab('fill Bob COBBLESTONE 2');
      await server.lab('deliver Bob DIAMOND_SWORD 5 1');
      const inbox = await openTab(bob, '/mail');
      const slot = slotOf(inbox, 'chest');
      if (slot < 0) throw new Error('delivery not listed');
      mark(bob);
      await bob.clickWindow(slot, 0, 0);
      const partial = await waitChat(bob, /stay in your mailbox/);
      await sleep(500);
      const first = await count(server, 'Bob', 'DIAMOND_SWORD');
      expectEq(first, 2, 'swords after the first claim');
      await server.lab('clear Bob');
      const again = await openTab(bob, '/mail');
      const rest = slotOf(again, 'chest');
      mark(bob);
      await bob.clickWindow(rest, 0, 0);
      await waitChat(bob, /Claimed/);
      await sleep(500);
      return `${partial.replace(/.*» /, '').slice(0, 50)}; then ${expectEq(await count(server, 'Bob', 'DIAMOND_SWORD'), 3, 'swords')}`;
    });

    // Campaign: one mail for everyone, once each.
    await check(step, 'campaign announced, claimed once', async () => {
      mark(bob);
      await server.run('mail announce name:launch money:100 <gold>Launch week</gold> thanks for playing', /Campaign .*launch.* created/);
      await waitChat(bob, /sent everyone mail/);
      const before = await balance(server, 'Bob');
      const inbox = await openTab(bob, '/mail');
      dumpWindow(inbox, 'Inbox with a campaign', windows);
      if (inbox.slots[9]?.name !== 'nether_star') throw new Error(`slot 9 is ${inbox.slots[9]?.name}`);
      mark(bob);
      await bob.clickWindow(9, 0, 0);
      await waitChat(bob, /Claimed/);
      await sleep(800);
      const again = await openTab(bob, '/mail');
      if (countIn(again, 'nether_star') !== 0) throw new Error('campaign still listed after claiming');
      return expectEq(await balance(server, 'Bob') - before, 100, 'campaign money');
    });
    if (hook) {
      await check(step, 'Discord webhook posted the campaign', async () => {
        await waitUntil(async () => hook.bodies.length > 0, 15_000, 'webhook');
        const body = JSON.parse(hook.bodies[0].body);
        if (!/Launch week/.test(body.embeds[0].description)) throw new Error(hook.bodies[0].body);
        return `embed "${body.embeds[0].title}"`;
      });
    }

    // Reward codes.
    await check(step, 'reward code limits (2 uses, 1 per player)', async () => {
      await server.run('mail code create SPRING uses:2 per-player:1 money:50 Spring reward', /Code .*SPRING.* created/);
      mark(alice);
      await say(alice, '/mail redeem spring');
      await waitChat(alice, /redeemed/);
      await sleep(1200);
      await say(alice, '/mail redeem SPRING');
      await waitChat(alice, /already redeemed/);
      mark(bob);
      await say(bob, '/mail redeem spring');
      await waitChat(bob, /redeemed/);
      mark(carol);
      await say(carol, '/mail redeem spring');
      await waitChat(carol, /used up/);
      const rewards = await openTab(alice, '/mail rewards');
      const slot = slotOf(rewards, 'name_tag', 18, 44);
      const before = await balance(server, 'Alice');
      mark(alice);
      await alice.clickWindow(slot, 0, 0);
      await waitChat(alice, /Claimed/);
      await sleep(800);
      return `OK, already, OK, used up; ${expectEq(await balance(server, 'Alice') - before, 50, 'code money')}`;
    });

    await check(step, 'redeem through the Rewards tab button', async () => {
      await server.run('mail code create GUICODE money:5', /Code .*GUICODE.* created/);
      const rewards = await openTab(bob, '/mail rewards');
      if (rewards.slots[51]?.name !== 'name_tag') throw new Error(`redeem button is ${rewards.slots[51]?.name}`);
      mark(bob);
      await waitWindow(bob, async () => {
        await bob.clickWindow(51, 0, 0);
        await waitChat(bob, /Type the code/);
        await say(bob, 'guicode');
      });
      return (await waitChat(bob, /redeemed/)).replace(/.*» /, '');
    });

    // Deliveries from the console.
    await check(step, 'console give to a player, online and all', async () => {
      mark(bob);
      await server.run('mail give Bob item DIAMOND 5 Console gift', /Mail sent to 1 player/);
      await waitChat(bob, /New mail from/);
      await server.run('mail give online money 25', /Mail sent to 3 player/);
      await server.run('mail give all item APPLE 3', /every known player as campaign/);
      await server.lab('clear Bob');
      mark(bob);
      await say(bob, '/mail claimall');
      await waitChat(bob, /Claimed \d+ mail/);
      await sleep(600);
      const diamonds = await count(server, 'Bob', 'DIAMOND');
      const inbox = await openTab(bob, '/mail');
      const star = slotOf(inbox, 'nether_star');
      if (star < 0) throw new Error('give-all campaign not listed');
      mark(bob);
      await bob.clickWindow(star, 0, 0);
      await waitChat(bob, /Claimed/);
      await sleep(600);
      return `${expectEq(diamonds, 5, 'diamonds')}, ${expectEq(await count(server, 'Bob', 'APPLE'), 3, 'apples')}`;
    });

    // Block list and refuse toggle.
    await check(step, 'block list and refuse toggle', async () => {
      mark(bob);
      await say(bob, '/mail block Alice');
      await waitChat(bob, /no longer accept parcels from Alice/);
      await sleep(3200);
      await server.lab('give Alice DIRT 2');
      await sleep(400);
      await sendParcel(alice, 'Bob', 'dirt', { expect: /not accepting parcels from you/ });
      close(alice);
      await sleep(800);
      const kept = await count(server, 'Alice', 'DIRT');
      await say(bob, '/mail unblock Alice');
      await waitChat(bob, /accept parcels from Alice again/);
      await say(bob, '/mail toggle');
      await waitChat(bob, /refuse all parcels/);
      await sleep(3200);
      await sendParcel(alice, 'Bob', 'dirt', { expect: /not accepting parcels right now/ });
      close(alice);
      await say(bob, '/mail toggle');
      await waitChat(bob, /accept parcels again/);
      return `blocked and refused; ${expectEq(kept, 2, 'items kept by the sender')}`;
    });

    await check(step, 'anti-spam: cooldown and parcels per recipient', async () => {
      await sleep(3200);
      await server.lab('give Alice STICK 4');
      await server.lab('give Alice FEATHER 4');
      await server.lab('give Alice STRING 4');
      await sleep(400);
      await sendParcel(alice, 'Carol', 'stick');
      await sleep(300);
      const cooldown = await sendParcel(alice, 'Carol', 'feather', { expect: /Wait \d+s/ });
      close(alice);
      await server.lab('parcel Alice Carol STICK 1 0');
      await server.lab('parcel Alice Carol STICK 1 0');
      await sleep(3500);
      const limit = await sendParcel(alice, 'Carol', 'string', { expect: /already have 3 parcels waiting/ });
      close(alice);
      await sleep(800);
      const kept = await count(server, 'Alice', 'FEATHER') + await count(server, 'Alice', 'STRING');
      expectEq(kept, 8, 'refused items back with the sender');
      return `${cooldown.replace(/.*» /, '')}; ${limit.replace(/.*» /, '').slice(0, 50)}`;
    });

    await check(step, 'unanswered cash-on-delivery parcel returns on expiry', async () => {
      await waitUntil(async () => /RETURN/.test(await mails(server, 'Alice')) && !/COD/.test(await mails(server, 'Bob')),
        Math.max(10_000, expiringSent + 170_000 - Date.now()), 'expired parcel returned');
      await server.lab('clear Alice');
      const inbox = await openTab(alice, '/mail');
      let claimed = 0;
      for (let i = 0; i < 4; i++) {
        const back = slotOf(inbox, 'barrel');
        if (back < 0) break;
        mark(alice);
        await alice.clickWindow(back, 0, 0);
        await waitChat(alice, /Claimed/);
        claimed++;
        await sleep(800);
        if (!alice.currentWindow) break;
      }
      return `${expectEq(await count(server, 'Alice', 'BONE'), 3, 'bones back')} after ${Math.round((Date.now() - expiringSent) / 1000)}s`;
    });

    await check(step, 'PlaceholderAPI values', async () => {
      const line = await server.run('papi parse Bob %donutmail_unread%|%donutmail_parcels%|%donutmail_cod%|%donutmail_daily_streak%|%donutmail_daily_next_day%',
        /\b\d+\|\d+\|\d+\|1\|\d+\s*$/);
      return line.replace(/.*INFO\]: /, '');
    });
    await check(step, 'console /mail admin info', async () => (await server.run('mail admin info', /Storage: \w+/)).replace(/.*INFO\]: /, '').trim());

    await check(step, 'mailbox block opens the mailbox', async () => {
      close(bob);
      const placed = (await server.lab('place Bob 2 0 0 LECTERN')).split(' ')[1];
      const [, x, y, z] = placed.split(',').map(Number);
      await sleep(1000);
      const block = bob.blockAt(new (bob.entity.position.constructor)(x, y, z));
      if (!block || block.name !== 'lectern') throw new Error(`block at ${placed} is ${block?.name}`);
      try {
        const window = await waitWindow(bob, () => bob.activateBlock(block), 8_000);
        return window.slots[2]?.name === 'chest' ? `opened by the bot's right-click at ${placed}` : false;
      } catch (error) {
        if (!target.via) throw error;
        // Through ViaBackwards the bot's block click never reaches the server (no interact event at all);
        // fire the same Bukkit event from the fixture to check DonutMail's side.
        const window = await waitWindow(bob, () => server.lab(`click Bob ${x} ${y} ${z}`));
        return window.slots[2]?.name === 'chest'
          ? `opened by a right-click event fired by the fixture at ${placed} (the bot's click does not pass ViaBackwards)` : false;
      }
    });

    await check(step, 'staff view and removal', async () => {
      await server.lab('deliver Bob STONE 1 1');
      await server.run('op Carol', /Made Carol a server operator|Opped Carol|Carol/);
      await sleep(500);
      close(carol);
      const view = await waitWindow(carol, () => say(carol, '/mail admin view Bob'));
      if (!view.slots[0]) throw new Error('staff view empty');
      mark(carol);
      await carol.clickWindow(0, 1, 1);
      await waitChat(carol, /Removed that mail/);
      await server.run('deop Carol', /Carol/);
      return 'removed through the staff view';
    });

    await check(step, '/mail reload', async () => (await server.run('mail reload', /DonutMail reloaded/)).replace(/.*INFO\]: /, '').slice(0, 60));

    await check(step, 'API events fired', async () => {
      const line = await server.lab('events');
      for (const name of ['claim', 'send', 'daily', 'code', 'returned-declined']) {
        if (!new RegExp(`\\b${name}=\\d+`).test(line)) throw new Error(`missing ${name}: ${line}`);
      }
      return line.replace('events ', '').slice(0, 150);
    });

    // Restart: everything persists, campaign claims stay claimed, and the streak continues.
    const beforeBob = await mails(server, 'Bob');
    const beforeCarol = await mails(server, 'Carol');
    await server.run('mail admin streak Bob 2', /streak of Bob set to 2/);
    // Alice is writing a parcel when the server stops: the items must survive.
    let composing = false;
    try {
      await server.lab('give Alice CLAY_BALL 2');
      await sleep(400);
      close(alice);
      const compose = await waitWindow(alice, () => say(alice, '/mail send Bob'));
      await putIn(alice, compose, 'clay_ball');
      composing = true;
    } catch (error) {
      step('parcel menu open during the stop', false, error.message);
    }
    for (const name of Object.keys(bots)) if (name !== 'Alice') await leave(name);
    await server.stop();
    await leave('Alice');
    await check(step, 'restart', async () => {
      server.lines.length = 0;
      server.cursor = 0;
      await server.start();
      return 'started again';
    });
    const bob2 = await join('Bob');
    await check(step, 'mail survives the restart', async () => {
      const afterBob = await mails(server, 'Bob');
      const afterCarol = await mails(server, 'Carol');
      const strip = (value) => value.replace(/PENDING:DAILY=\d+,?/, '').replace(/^none$/, '').replace(/,$/, '');
      if (strip(afterBob) !== strip(beforeBob) || afterCarol !== beforeCarol) {
        throw new Error(`Bob ${beforeBob} -> ${afterBob}; Carol ${beforeCarol} -> ${afterCarol}`);
      }
      return `Bob: ${afterBob}`;
    });
    if (composing) {
      await check(step, 'items in an open parcel menu survive a server stop', async () => {
        const alice2 = await join('Alice');
        await sleep(1500);
        let total = await count(server, 'Alice', 'CLAY_BALL');
        if (total < 2) {
          const inbox = await openTab(alice2, '/mail');
          const back = slotOf(inbox, 'barrel');
          if (back < 0) throw new Error(`not in the inventory (${total}) nor the mailbox: ${await mails(server, 'Alice')}`);
          mark(alice2);
          await alice2.clickWindow(back, 0, 0);
          await waitChat(alice2, /Claimed/);
          await sleep(600);
          total = await count(server, 'Alice', 'CLAY_BALL');
          return expectEq(total, 2, 'clay balls (from the mailbox)');
        }
        return expectEq(total, 2, 'clay balls (back in the inventory)');
      });
    }
    await check(step, 'daily streak continues (day 3)', async () => {
      await waitChat(bob2, /Daily reward day 3/, 20_000);
      await server.lab('clear Bob');
      const rewards = await openTab(bob2, '/mail rewards');
      const slot = slotOf(rewards, 'sunflower', 18, 44);
      mark(bob2);
      await bob2.clickWindow(slot, 0, 0);
      await waitChat(bob2, /Claimed/);
      await sleep(600);
      const line = await server.run('papi parse Bob %donutmail_daily_streak%', /INFO\]: \d+\s*$/);
      return `${expectEq(await count(server, 'Bob', 'EXPERIENCE_BOTTLE'), 32, 'bottles')}, streak ${number(line)}`;
    });
    await check(step, 'campaign stays claimed after restart; a new player can claim it', async () => {
      const inbox = await openTab(bob2, '/mail');
      if (countIn(inbox, 'nether_star') !== 0) throw new Error('Bob sees a campaign he claimed');
      const dave = await join('Dave');
      const daveBox = await openTab(dave, '/mail');
      const stars = countIn(daveBox, 'nether_star');
      if (stars !== 1) throw new Error(`Dave sees ${stars} campaigns (launch only expected; give-all is for players known before it)`);
      mark(dave);
      await dave.clickWindow(9, 0, 0);
      await waitChat(dave, /Claimed/);
      return 'Bob: none; Dave: launch claimed';
    });

    if (extras.includes('votes')) {
      await check(step, 'NuVotifier vote reward in the mailbox', async () => {
        await sendVote(prepared.dir, 'Bob');
        await waitUntil(async () => /VOTE/.test(await mails(server, 'Bob')), 20_000, 'vote mail');
        const rewards = await openTab(bob2, '/mail rewards');
        const slot = slotOf(rewards, 'diamond', 18, 44);
        if (slot < 0) throw new Error('vote reward not listed');
        await server.lab('clear Bob');
        mark(bob2);
        await bob2.clickWindow(slot, 0, 0);
        await waitChat(bob2, /Claimed/);
        await sleep(500);
        return expectEq(await count(server, 'Bob', 'DIAMOND'), 1, 'vote diamond');
      });
    }

    if (extras.includes('crash')) {
      await crashTests({ server, step, join, leave, bots });
    }
  } finally {
    for (const name of Object.keys(bots)) bots[name].quit();
    await sleep(1000);
    await server.stop();
    hook?.close();
    result.errors = pluginErrors(readConsole(prepared.dir));
    step('no DonutMail errors in the console', result.errors.length === 0, `${result.errors.length} error line(s)`);
  }
}

function readConsole(dir) {
  return fs.readFileSync(path.join(dir, 'console.log'), 'utf8').split('\n').map((l) => l.replace(/\x1b\[[0-9;]*m/g, ''));
}

/** The server dies right after handing items over, before the claim is saved; nothing may be lost or doubled. */
async function crashTests({ server, step, join, leave, bots }) {
  for (const mode of ['nosave', 'save']) {
    await check(step, `crash mid-claim (${mode === 'save' ? 'player data saved' : 'player data not saved'})`, async () => {
      const material = mode === 'save' ? 'EMERALD_BLOCK' : 'GOLD_BLOCK';
      for (const name of Object.keys(bots)) await leave(name);
      await server.stop();
      server.lines.length = 0;
      server.cursor = 0;
      await server.start([`-Ddonutmail.test.crash-after-delivery=${mode}`]);
      let bob = await join('Bob');
      await server.lab('clear Bob');
      await server.lab('save Bob');
      await server.lab(`deliver Bob ${material} 2 1`);
      const inbox = await openTab(bob, '/mail');
      const slot = slotOf(inbox, 'chest');
      if (slot < 0) throw new Error('delivery not listed');
      bob.clickWindow(slot, 0, 0).catch(() => null);
      const code = await Promise.race([server.exited, sleep(30_000).then(() => 'alive')]);
      if (code === 'alive') throw new Error('the crash hook did not stop the server');
      bob.quit();
      delete bots.Bob;
      await sleep(1500);
      server.lines.length = 0;
      server.cursor = 0;
      await server.start();
      bob = await join('Bob');
      if (mode === 'save') {
        await waitChat(bob, /interrupted by a restart was finished/, 20_000);
        await sleep(1000);
        const have = await count(server, 'Bob', material);
        const left = await mails(server, 'Bob');
        if (/CLAIMING/.test(left)) throw new Error(`still locked: ${left}`);
        return `${expectEq(have, 2, 'blocks')} (saved with the player), claim finished, none left to claim again`;
      }
      await sleep(3000);
      const have = await count(server, 'Bob', material);
      expectEq(have, 0, 'blocks after the crash (player data rolled back)');
      const box = await openTab(bob, '/mail');
      const again = slotOf(box, 'chest');
      if (again < 0) throw new Error(`mail not back: ${await mails(server, 'Bob')}`);
      mark(bob);
      await bob.clickWindow(again, 0, 0);
      await waitChat(bob, /Claimed/);
      await sleep(600);
      return `rolled back, mail released, claimed again: ${expectEq(await count(server, 'Bob', material), 2, 'blocks')}`;
    });
  }
}

// ── Two servers on one MySQL database ───────────────────────────────────────

export async function networkScenario({ target, step, port, java, result }) {
  resetDatabase('donutmail_net');
  const a = await prepareServer(target, `${target.name}-alpha`, port, { storage: 'MYSQL', database: 'donutmail_net', serverId: 'alpha' });
  const b = await prepareServer(target, `${target.name}-beta`, port + 1, { storage: 'MYSQL', database: 'donutmail_net', serverId: 'beta' });
  result.server = `${target.project} ${target.version} build ${a.build} x2, MariaDB`;
  const alpha = new Server(a.dir, java, a.jar, port);
  const beta = new Server(b.dir, java, b.jar, port + 1);
  const bots = [];
  try {
    await Promise.all([alpha.start(), beta.start()]);
    step('two servers on one database', true, 'alpha + beta');
    const bob = await joinBot('Bob', target.bot, port, path.join(a.dir, 'chat-Bob.log'));
    bots.push(bob);
    const alice = await joinBot('Alice', target.bot, port + 1, path.join(b.dir, 'chat-Alice.log'));
    bots.push(alice);
    await alpha.lab('money Bob 1000');
    await check(step, 'parcel sent on beta reaches Bob on alpha (live notice)', async () => {
      await beta.lab('give Alice DIAMOND 2');
      await sleep(400);
      mark(bob);
      await sendParcel(alice, 'Bob', 'diamond');
      const started = Date.now();
      await waitChat(bob, /sent you a parcel/, 15_000);
      const parcels = await openTab(bob, '/mail parcels');
      mark(bob);
      await bob.clickWindow(slotOf(parcels, 'chest'), 0, 0);
      await waitChat(bob, /Claimed/);
      await sleep(500);
      return `${expectEq(await count(alpha, 'Bob', 'DIAMOND'), 2, 'diamonds on alpha')}, notice after ${Date.now() - started} ms`;
    });
    await check(step, 'cash on delivery across servers: pay on alpha vs take back on beta, one winner', async () => {
      const wins = { pay: 0, back: 0 };
      for (let round = 0; round < 3; round++) {
        await beta.lab('parcel Alice Bob EMERALD 3 100');
        await sleep(2500);
        const parcels = await openTab(bob, '/mail parcels');
        const codSlot = slotOf(parcels, 'ender_chest');
        if (codSlot < 0) throw new Error('parcel from beta not listed on alpha');
        await waitWindow(bob, () => bob.clickWindow(codSlot, 0, 0));
        await openTab(alice, '/mail sent');
        await waitWindow(alice, () => alice.clickWindow(9, 0, 0));
        mark(bob);
        mark(alice);
        await Promise.all([bob.clickWindow(47, 0, 0), alice.clickWindow(51, 0, 0)]);
        await sleep(4000);
        const paid = bob.chatLog.slice(bob.chatCursor).some((l) => /You paid/.test(l));
        const back = alice.chatLog.slice(alice.chatCursor).some((l) => /is back in your mailbox/.test(l));
        if (paid === back) throw new Error(`round ${round}: paid=${paid} back=${back}`);
        wins[paid ? 'pay' : 'back']++;
        close(bob);
        close(alice);
      }
      const emeralds = await count(alpha, 'Bob', 'EMERALD');
      expectEq(emeralds, wins.pay * 3, 'emeralds = payments won x 3');
      expectEq(await balance(alpha, 'Bob'), 1000 - wins.pay * 100, 'Bob charged only for wins');
      return `payment won ${wins.pay}, take-back won ${wins.back}; never both`;
    });
    await check(step, 'campaign from alpha, claimed once across both servers', async () => {
      mark(alice);
      await alpha.run('mail announce name:network money:10 Hello network', /Campaign .*network.* created/);
      await waitChat(alice, /sent everyone mail/, 15_000);
      const box = await openTab(alice, '/mail');
      if (box.slots[9]?.name !== 'nether_star') throw new Error('campaign not visible on beta');
      mark(alice);
      await alice.clickWindow(9, 0, 0);
      await waitChat(alice, /Claimed/);
      // Bob claims on alpha, moves to beta, and it must not be offered again.
      const bobBox = await openTab(bob, '/mail');
      mark(bob);
      await bob.clickWindow(9, 0, 0);
      await waitChat(bob, /Claimed/);
      bob.quit();
      await sleep(2000);
      const bobOnBeta = await joinBot('Bob', target.bot, port + 1, path.join(b.dir, 'chat-Bob.log'));
      bots.push(bobOnBeta);
      const again = await openTab(bobOnBeta, '/mail');
      if (countIn(again, 'nether_star') !== 0) throw new Error('Bob is offered the campaign again on beta');
      return 'claimed once on each player, not offered again on the other server';
    });
    await check(step, 'daily reward once per day across servers', async () => {
      const line = await beta.lab('events');
      const daily = Number((line.match(/daily=(\d+)/) || [0, 0])[1]);
      const lines = await alpha.lab('events');
      const dailyAlpha = Number((lines.match(/daily=(\d+)/) || [0, 0])[1]);
      // Bob joined alpha (day delivered there), then beta the same day: beta delivered only Alice's.
      return `${expectEq(daily + dailyAlpha, 2, 'daily deliveries for 2 players on 2 servers')}`;
    });
  } finally {
    for (const bot of bots) bot.quit();
    await sleep(1000);
    await Promise.all([alpha.stop(), beta.stop()]);
    result.errors = [...pluginErrors(readConsole(a.dir)), ...pluginErrors(readConsole(b.dir))];
    step('no DonutMail errors in either console', result.errors.length === 0, `${result.errors.length} error line(s)`);
  }
}

// ── Upgrade from DonutMail 1.1.0 ────────────────────────────────────────────

export async function upgradeScenario({ target, step, port, java, result }) {
  const oldJar = process.env.DONUTMAIL_110_JAR;
  if (!oldJar || !fs.existsSync(oldJar)) throw new Error('set DONUTMAIL_110_JAR to a DonutMail 1.1.0 jar');
  for (const storage of ['YAML', 'SQLITE']) {
    // 1.1.0 runs without the fixture (it is built against 2.0); it is added with the 2.0.0 jar.
    const prepared = await prepareServer(target, `${target.name}-${storage.toLowerCase()}`, port, { donutJar: oldJar, config: false, fixture: false });
    result.server = `${target.project} ${target.version} build ${prepared.build}`;
    const server = new Server(prepared.dir, java, prepared.jar, port);
    const bots = [];
    try {
      await server.start();
      if (storage === 'SQLITE') {
        const config = path.join(prepared.dir, 'plugins', 'DonutMail', 'config.yml');
        fs.writeFileSync(config, fs.readFileSync(config, 'utf8').replace('type: YAML', 'type: SQLITE'));
        await server.stop();
        server.lines.length = 0;
        server.cursor = 0;
        await server.start();
      }
      step(`1.1.0 running (${storage})`, server.lines.some((l) => new RegExp(`DonutMail enabled with storage=${storage}`).test(l)));
      const bob = await joinBot('Bob', target.bot, port, null);
      bots.push(bob);
      const alice = await joinBot('Alice', target.bot, port, null);
      bots.push(alice);
      await check(step, `1.1.0: item mail, text mail and voucher sent (${storage})`, async () => {
        await server.run('give Alice minecraft:diamond 3', /Gave 3|Given|Alice/);
        await sleep(500);
        const compose = await waitWindow(alice, () => say(alice, '/mail senditem Bob'));
        await putIn(alice, compose, 'diamond');
        await alice.clickWindow(49, 0, 0);
        await waitChat(alice, /Sent 1 item stack/);
        await server.run('mail send Bob Hello from 1.1', /Mail sent to 1/);
        await server.run('mail voucher send vote_reward Bob', /Mail sent to 1/);
        return '3 mails';
      });
      for (const bot of bots) bot.quit();
      bots.length = 0;
      await sleep(1000);
      await server.stop();
      // Replace the jar: 1.1.0 out, 2.0.0 in, same data folder.
      fs.rmSync(path.join(prepared.dir, 'plugins', path.basename(oldJar)));
      const { pluginJarPath, fixtureJar } = await import('./run.mjs');
      for (const jar of [pluginJarPath(), fixtureJar()]) fs.copyFileSync(jar, path.join(prepared.dir, 'plugins', path.basename(jar)));
      server.lines.length = 0;
      server.cursor = 0;
      await server.start();
      step(`2.0.0 starts on the 1.1.0 data (${storage})`, server.lines.some((l) => new RegExp(`DonutMail enabled using ${storage} storage`).test(l)),
        (server.lines.find((l) => /Upgraded|Converted|Moved DonutMail 1.x/.test(l)) || '').replace(/.*\]: /, ''));
      const bob2 = await joinBot('Bob', target.bot, port, null);
      bots.push(bob2);
      await check(step, `1.1.0 mail listed and claimable in 2.0.0 (${storage})`, async () => {
        const listed = await mails(server, 'Bob');
        const inbox = await openTab(bob2, '/mail');
        const chest = slotOf(inbox, 'chest');
        const paper = countIn(inbox, 'paper');
        if (chest < 0) throw new Error(`item mail not listed: ${listed}`);
        mark(bob2);
        await bob2.clickWindow(chest, 0, 0);
        await waitChat(bob2, /Claimed/);
        await sleep(600);
        return `${listed}; ${expectEq(await count(server, 'Bob', 'DIAMOND'), 3, 'diamonds from 1.1.0')}, ${paper} text/voucher mail shown`;
      });
      await check(step, `config.yml migrated, messages.yml created (${storage})`, async () => {
        const config = fs.readFileSync(path.join(prepared.dir, 'plugins', 'DonutMail', 'config.yml'), 'utf8');
        if (/title-main|chat-format/.test(config)) throw new Error('1.x message keys still in config.yml');
        if (!new RegExp(`type: ${storage}`).test(config)) throw new Error('storage type changed');
        for (const file of ['messages.yml', 'gui.yml', 'daily.yml']) {
          if (!fs.existsSync(path.join(prepared.dir, 'plugins', 'DonutMail', file))) throw new Error(`${file} missing`);
        }
        return `storage ${storage} kept, messages.yml/gui.yml/daily.yml created`;
      });
    } finally {
      for (const bot of bots) bot.quit();
      await sleep(1000);
      await server.stop();
      const errors = pluginErrors(readConsole(prepared.dir));
      result.errors.push(...errors);
      step(`no DonutMail errors (${storage})`, errors.length === 0, `${errors.length} error line(s)`);
    }
  }
}
