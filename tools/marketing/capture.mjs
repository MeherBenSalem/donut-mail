// Captures the listing screenshots and the GIF from a real game: a Paper 1.21.11 server running DonutMail and the
// unmodified vanilla client (software rendering under Xvfb), driven through console commands and XTEST mouse and
// keyboard input. Mineflayer bots play the other players (Kiri and Willow): they send the parcels, pay and decline.
//
// Needs: Xvfb, ImageMagick (import), ffmpeg, python3 with python-xlib, portablemc, a JDK 21, the plugin and the
// smoke-test fixture built, and the smoke-test downloads (tools/smoke caches them in $MAILLAB_HOME/dl: paper-1.21.11.jar,
// Vault-1.7.3.jar, PlaceholderAPI-2.12.3.jar, NuVotifier-2.7.3.jar). Run from the repository root:
//   JAVA21_HOME=/path/to/jdk21 node tools/marketing/capture.mjs
// Outputs: marketing/images/screenshots/*.jpg, marketing/images/gifs/cod.gif and marketing/source/console/*.
import { spawn, execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Server, joinBot, sendVote, webhookCatcher, fixtureJar, sleep } from '../smoke/run.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '..', '..');
const home = process.env.CAPTURE_HOME || path.join(os.homedir(), '.cache', 'donutmail-capture');
const dl = path.join(process.env.MAILLAB_HOME || path.join(os.homedir(), '.cache', 'donutmail-smoke'), 'dl');
const PORT = Number(process.env.CAPTURE_PORT || 25640);
const VERSION = '1.21.11';
const PLAYER = 'Moonbeam';
const DISPLAY = process.env.CAPTURE_DISPLAY || ':99';
const W = 1920;
const H = 1080;
const SCALE = 3;
const shots = path.join(repo, 'marketing', 'images', 'screenshots');
const gifs = path.join(repo, 'marketing', 'images', 'gifs');
const consoleDir = path.join(repo, 'marketing', 'source', 'console');
const checks = path.join(home, 'checks');
const python = process.env.CAPTURE_PYTHON || 'python3';
const portablemc = process.env.PORTABLEMC || 'portablemc';
const clientDir = process.env.CAPTURE_CLIENT_DIR || path.join(home, 'client');
const java = process.env.JAVA21_HOME ? path.join(process.env.JAVA21_HOME, 'bin', 'java') : 'java';
// CAPTURE_SCENES="mailbox block,live" retakes some scenes: the setup still runs, the console files are left alone.
const ONLY = process.env.CAPTURE_SCENES ? new Set(process.env.CAPTURE_SCENES.split(',').map((name) => name.trim())) : null;

// Noon, clear sky, no command echo or advancement messages in chat. 1.21.11 renamed the game rules, so both
// spellings are sent; the server rejects the one it does not know.
const WORLD_SETUP = ['time set 6000', 'weather clear',
  'gamerule advance_time false', 'gamerule advance_weather false', 'gamerule show_advancement_messages false', 'gamerule send_command_feedback false',
  'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false', 'gamerule announceAdvancements false', 'gamerule sendCommandFeedback false'];

const x = (...args) => execFileSync(python, [path.join(here, 'xinput.py'), ...args.map(String)], { env: { ...process.env, DISPLAY } });
const log = (line) => console.log(`[${new Date().toISOString().slice(11, 19)}] ${line}`);

// ── The vanilla client ──────────────────────────────────────────────────────

/** Pixel centre of a container slot in a chest menu with the given number of rows (GUI scale 3). */
function slot(index, rows = 6) {
  const left = Math.floor((W / SCALE - 176) / 2);
  const top = Math.floor((H / SCALE - (114 + rows * 18)) / 2);
  return [(left + 8 + (index % 9) * 18 + 8) * SCALE, (top + 18 + Math.floor(index / 9) * 18 + 8) * SCALE];
}

/** Pixel centre of the player's own inventory slot (0-8 hotbar, 9-35 the rest) below a chest menu. */
function ownSlot(index, rows = 6) {
  const left = Math.floor((W / SCALE - 176) / 2);
  const top = Math.floor((H / SCALE - (114 + rows * 18)) / 2);
  const y = index < 9 ? rows * 18 + 89 : rows * 18 + 31 + Math.floor((index - 9) / 9) * 18;
  return [(left + 8 + (index % 9) * 18 + 8) * SCALE, (top + y + 8) * SCALE];
}

function screenshot(name, { dir = shots, quality = 92 } = {}) {
  fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, `${name}.jpg`);
  execFileSync('import', ['-display', DISPLAY, '-window', 'root', '-quality', String(quality), file]);
  log(`  captured ${path.relative(repo, file)}`);
  return file;
}

/**
 * Whether a chest menu is open: its light grey panel (#C6C6C6) left of the title and of the bottom row. The mouse is only moved or clicked
 * while one is, because in the world the same input would turn the camera or hit a block.
 */
function menuOpen() {
  const grey = (box) => execFileSync('import', ['-display', DISPLAY, '-window', 'root', '-crop', box, '+repage', '-format',
    '%[fx:int(255*mean.r)],%[fx:int(255*mean.g)],%[fx:int(255*mean.b)]', 'info:']).toString().split(',').map(Number)
    .every((value) => Math.abs(value - 198) <= 8);
  // Two spots of the panel: beside the title and beside the bottom row.
  return grey('4x4+709+240') && grey('4x4+709+560');
}

async function waitMenu(timeout = 8000) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    if (menuOpen()) return;
    await sleep(250);
  }
  throw new Error('no menu is open');
}

async function hoverAt([px, py], settle = 700) {
  await waitMenu();
  x('move', px - 9, py - 9);
  await sleep(120);
  x('move', px, py);
  await sleep(settle);
}

const hover = (index, settle) => hoverAt(slot(index), settle);
/** Moves the pointer off the menu (no tooltip), once a menu is open. */
const park = () => hoverAt([1700, 300], 600);
const hoverOwn = (index, settle) => hoverAt(ownSlot(index), settle);

async function clickAt([px, py], settle = 1200, button = 1) {
  await waitMenu();
  x('click', px, py, button);
  await sleep(settle);
}

const clickSlot = (index, settle) => clickAt(slot(index), settle);

/** Picks up a stack from the player's inventory and puts it into a menu slot (two plain clicks, like a player). */
async function moveIn(own, target) {
  await clickAt(ownSlot(own), 350);
  await clickAt(slot(target), 450);
}

/** Opens chat with T and types a line; Enter unless `send` is false. */
async function typeInChat(text, send = true) {
  x('key', 't');
  await sleep(700);
  x('type', text);
  await sleep(400);
  if (send) {
    x('key', 'Return');
    await sleep(900);
  }
}

/** Records the screen for `seconds` (x11grab); returns a promise for the finished MP4. */
function record(name, seconds) {
  const raw = path.join(home, `${name}.mp4`);
  const proc = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'x11grab', '-video_size', `${W}x${H}`, '-framerate', '20', '-i', `${DISPLAY}.0`,
    '-t', String(seconds), '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '16', raw], { stdio: 'ignore' });
  return new Promise((resolve) => proc.on('exit', () => resolve(raw)));
}

/**
 * A 640x360 GIF from a recording: the left 1600x900 of the screen below the top 180 rows, which holds the menu,
 * its tooltips and the chat, scaled by 2.5.
 */
function gif(raw, name, { fps = 10, limit = 1_000_000 } = {}) {
  fs.mkdirSync(gifs, { recursive: true });
  const file = path.join(gifs, `${name}.gif`);
  // The palette shrinks until the GIF fits the size limit (falling petals make every frame differ a little).
  for (const colors of [160, 128, 112, 96, 80, 64]) {
    execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', raw, '-vf',
      `crop=1600:900:0:180,fps=${fps},scale=640:360:flags=lanczos,split[a][b];[a]palettegen=max_colors=${colors}:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle`,
      file]);
    if (fs.statSync(file).size <= limit) break;
  }
  log(`  recorded ${path.relative(repo, file)} (${Math.round(fs.statSync(file).size / 1024)} KB)`);
}

function writeClientOptions() {
  fs.mkdirSync(clientDir, { recursive: true });
  fs.writeFileSync(path.join(clientDir, 'options.txt'), [
    'version:4671', 'guiScale:3', 'renderDistance:10', 'simulationDistance:5', 'maxFps:30', 'graphicsMode:1',
    'pauseOnLostFocus:false', 'narrator:0', 'tutorialStep:none', 'skipMultiplayerWarning:true', 'joinedFirstServer:true',
    'onboardAccessibility:false', 'lang:en_us', 'fov:0.0', 'bobView:false', 'chatScale:1.0', 'chatWidth:1.0',
    'soundCategory_master:0.0', 'darkMojangStudiosBackground:true', 'hideServerAddress:true', 'renderClouds:"fancy"',
    'entityShadows:true', 'particles:0', 'mipmapLevels:4', 'ao:true', 'menuBackgroundBlurriness:3',
  ].join('\n') + '\n');
}

// ── The server ──────────────────────────────────────────────────────────────

function cached(name) {
  const file = path.join(dl, name);
  if (!fs.existsSync(file)) throw new Error(`${file} is missing: run node tools/smoke/run.mjs paper-1.21.11 once to download it`);
  return file;
}

function prepareServer() {
  const dir = path.join(home, 'server');
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(path.join(dir, 'plugins', 'DonutMail'), { recursive: true });
  fs.writeFileSync(path.join(dir, 'eula.txt'), 'eula=true\n');
  fs.writeFileSync(path.join(dir, 'server.properties'), [
    'online-mode=false', 'white-list=false', 'enforce-secure-profile=false', `server-port=${PORT}`, 'level-seed=8675309',
    'difficulty=peaceful', 'spawn-monsters=false', 'view-distance=10', 'simulation-distance=5', 'spawn-protection=0',
    'motd=DonutMail', 'gamemode=survival', 'allow-flight=true',
  ].join('\n') + '\n');
  const target = path.join(repo, 'target');
  const plugin = process.env.PLUGIN_JAR || path.join(target, fs.readdirSync(target).find((f) => /^DonutMail-.*\.jar$/.test(f) && !f.startsWith('original')));
  const jars = [plugin, fixtureJar(), cached('Vault-1.7.3.jar'), cached('PlaceholderAPI-2.12.3.jar'), cached('NuVotifier-2.7.3.jar')];
  for (const jar of jars) fs.copyFileSync(jar, path.join(dir, 'plugins', path.basename(jar)));
  // Defaults, plus postage, vote rewards and a Discord webhook pointed at a local endpoint (its JSON is kept).
  fs.writeFileSync(path.join(dir, 'plugins', 'DonutMail', 'config.yml'), [
    'parcels:', '  postage:', '    enabled: true', '    flat: 5', '    per-stack: 1',
    'votes:', '  enabled: true', '  money: 10', "  items: ['DIAMOND:1']",
    'discord:', '  enabled: true', "  webhook-url: 'http://127.0.0.1:18089/hook'",
  ].join('\n') + '\n');
  return { dir, jar: cached(`paper-${VERSION}.jar`) };
}

// ── Bots ────────────────────────────────────────────────────────────────────

function waitWindow(bot, trigger, timeout = 15_000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      bot.removeListener('windowOpen', onOpen);
      reject(new Error(`${bot.username}: no window opened`));
    }, timeout);
    const onOpen = (window) => {
      clearTimeout(timer);
      setTimeout(() => resolve(window), 600);
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
  throw new Error(`${bot.username}: no chat matching ${pattern} (last: ${bot.chatLog.slice(-3).join(' | ')})`);
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

function close(bot) {
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
}

async function openTab(bot, command) {
  close(bot);
  await sleep(300);
  return waitWindow(bot, () => say(bot, command));
}

/** Moves every stack of an item from the bot's inventory into the open parcel menu, from slot `target` on. */
async function putIn(bot, window, name, target) {
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

/** Writes a parcel through the menu like a player: /mail send, an optional note typed in chat, the items, Send. */
async function botParcel(bot, to, items, { cod, note } = {}) {
  close(bot);
  await sleep(300);
  let window = await waitWindow(bot, () => say(bot, `/mail send ${to}${cod ? ` cod ${cod}` : ''}`));
  if (note) {
    mark(bot);
    window = await waitWindow(bot, async () => {
      await bot.clickWindow(48, 0, 0);
      await waitChat(bot, /short note/);
      await say(bot, note);
    });
  }
  let target = 0;
  for (const name of items) target += await putIn(bot, window, name, target);
  await sleep(500);
  mark(bot);
  await bot.clickWindow(49, 0, 0);
  const answer = await waitChat(bot, /Parcel sent to|Wait \d+s|not accepting/);
  close(bot);
  log(`  ${bot.username}: ${answer.replace(/.*» /, '')}`);
  return answer;
}

/** The bot opens its Parcels tab, previews the cash-on-delivery parcel and pays (47) or declines (51). */
async function botAnswer(bot, pay) {
  const parcels = await openTab(bot, '/mail parcels');
  const at = slotOf(parcels, 'ender_chest');
  if (at < 0) throw new Error(`${bot.username}: no cash-on-delivery parcel`);
  await waitWindow(bot, () => bot.clickWindow(at, 0, 0));
  mark(bot);
  await bot.clickWindow(pay ? 47 : 51, 0, 0);
  const answer = await waitChat(bot, pay ? /You paid/ : /declined the parcel/);
  log(`  ${bot.username}: ${answer.replace(/.*» /, '')}`);
  await sleep(300);
  close(bot);
}

// ── The run ─────────────────────────────────────────────────────────────────

async function main() {
  const { dir, jar } = prepareServer();
  fs.mkdirSync(consoleDir, { recursive: true });
  const hook = webhookCatcher(18089);
  const server = new Server(dir, java, jar, PORT);
  await server.start();
  log('server up');
  for (const command of WORLD_SETUP) server.command(command);
  const bots = {};
  let xvfb;
  let client;
  try {
    await setup(server, bots, dir, hook);

    xvfb = spawn('Xvfb', [DISPLAY, '-screen', '0', `${W}x${H}x24`, '-nolisten', 'tcp'], { stdio: 'ignore' });
    await sleep(1500);
    writeClientOptions();
    client = spawn(portablemc, ['--main-dir', clientDir, 'start', `mojang:${VERSION}`, '-u', PLAYER, '--join-server', '127.0.0.1',
      '--join-server-port', String(PORT), '--resolution', `${W}x${H}`, '--jvm', java, '--jvm-arg=-Xmx2G'],
    { env: { ...process.env, DISPLAY, LIBGL_ALWAYS_SOFTWARE: '1', __GL_SYNC_TO_VBLANK: '0' }, stdio: ['ignore', 'pipe', 'pipe'] });
    const clientLog = fs.createWriteStream(path.join(home, 'client.log'));
    client.stdout.pipe(clientLog);
    client.stderr.pipe(clientLog);
    server.mark();
    await server.waitFor(new RegExp(`${PLAYER} joined the game`), 400_000, 'client join');
    log('client joined');
    await scenes(server, bots, dir, hook);
  } finally {
    for (const bot of Object.values(bots)) bot.quit();
    if (client) client.kill('SIGTERM');
    await sleep(2000);
    await server.stop();
    if (xvfb) xvfb.kill('SIGTERM');
    hook.close();
  }
}

/** Everything that happens before Moonbeam's client joins: mail waiting, a streak, a vote and a code. */
async function setup(server, bots, dir, hook) {
  // Moonbeam played yesterday: join once so the server knows the name, take day 1, then set a 3-day streak.
  const first = await joinBot(PLAYER, VERSION, PORT);
  await waitChat(first, /Daily reward/, 20_000).catch(() => null);
  const rewards = await openTab(first, '/mail rewards');
  const daily = slotOf(rewards, 'sunflower', 18, 44);
  if (daily >= 0) {
    mark(first);
    await first.clickWindow(daily, 0, 0);
    await waitChat(first, /Claimed/).catch(() => null);
  }
  first.quit();
  await sleep(1500);
  await server.run(`mail admin streak ${PLAYER} 3`, /streak/i);

  bots.Kiri = await joinBot('Kiri', VERSION, PORT);
  bots.Willow = await joinBot('Willow', VERSION, PORT);
  bots.Draxen = await joinBot('Draxen', VERSION, PORT);
  await server.lab(`money ${PLAYER} 20000`);
  await server.lab('money Kiri 50000');
  await server.lab('money Willow 5000');
  await server.lab('money Draxen 5000');
  const { Kiri, Willow, Draxen } = bots;

  // Parcels from three players, written through the parcel menu: Kiri's elytra kit for cash on delivery, Willow's wood
  // and a shulker box of saplings, Draxen's iron, and last Kiri's enchanted gear (the newest, previewed later).
  for (const command of [
    'give Kiri elytra 1', 'give Kiri firework_rocket 128', 'give Kiri golden_apple 16',
    'give Kiri diamond_chestplate[enchantments={protection:4,unbreaking:3,mending:1}] 1',
    'give Kiri diamond_pickaxe[enchantments={efficiency:5,unbreaking:3,mending:1}] 1',
    'give Kiri diamond_sword[enchantments={sharpness:5,looting:3,unbreaking:3,mending:1}] 1',
    'give Kiri torch 64', 'give Kiri cooked_beef 32',
    'give Willow cherry_log 128', 'give Willow lantern 16',
    'give Willow pink_shulker_box[container=[{slot:0,item:{id:"minecraft:cherry_sapling",count:64}},{slot:1,item:{id:"minecraft:pink_petals",count:64}},{slot:2,item:{id:"minecraft:bone_meal",count:64}}]] 1',
    'give Draxen iron_ingot 128', 'give Draxen redstone 64',
  ]) server.command(command);
  await sleep(1500);
  await Promise.all([
    botParcel(Kiri, PLAYER, ['elytra', 'firework_rocket', 'diamond_chestplate', 'golden_apple'],
      { cod: '12.5k', note: 'Elytra kit as promised. The rockets are on me!' }),
    botParcel(Willow, PLAYER, ['cherry_log', 'lantern'], { note: 'Wood for the bridge. Thanks for helping!' }),
    botParcel(Draxen, PLAYER, ['iron_ingot', 'redstone'], { note: 'Iron for the farm, as promised.' }),
  ]);
  await sleep(11_000); // the default parcel cooldown is 10 seconds
  await botParcel(Willow, PLAYER, ['pink_shulker_box'], { note: 'A shulker of saplings for your garden.' })
    .catch((error) => log(`  Willow's shulker parcel was not sent: ${error.message}`));
  await botParcel(Kiri, PLAYER, ['diamond_pickaxe', 'diamond_sword', 'torch', 'cooked_beef'], { note: 'Spare gear from the mine trip. Enjoy!' });
  for (const name of ['Willow', 'Draxen']) {
    bots[name].quit();
    delete bots[name];
  }

  // The Inbox: server mail from the console (to Moonbeam and Willow), two sales Moonbeam made earlier through the API
  // (Kiri paid both: payments) and one Kiri declined (a returned parcel), then two campaigns. Eighteen entries, two
  // full rows: the oldest, the build contest prize, ends the second row, so its tooltip opens beside the menu.
  const both = `${PLAYER};Willow`;
  const sale = async (material, amount, price, pay) => {
    const sent = await server.lab(`parcel ${PLAYER} Kiri ${material} ${amount} ${price}`);
    if (!/SENT/.test(sent)) throw new Error(sent);
    await sleep(800);
    await botAnswer(Kiri, pay);
  };
  const steps = [
    `mail give ${both} money 2500 <gold>Build contest</gold> prize. Thanks for taking part!`,
    `mail give ${both} item EXPERIENCE_BOTTLE 32 <gold>Thanks for helping at the build event!`,
    `mail send ${both} <gold>Welcome!</gold> Parcels, rewards and server news all arrive in this mailbox.`,
    `mail give ${both} item NAME_TAG 3 Name tags from the spawn shop.`,
    () => sale('DIAMOND_BLOCK', 8, 3200, true),
    `mail send ${both} The nether hub opens on Saturday. See you there!`,
    `mail give ${both} item GOLDEN_CARROT 32 Supplies for the end trip.`,
    () => sale('ENDER_PEARL', 16, 480, false),
    `mail give ${both} item FIREWORK_ROCKET 32 Fireworks for the launch party.`,
    `mail send ${both} Server restart at 18:00 for the weekly update.`,
    `mail give ${both} item COOKED_BEEF 32 Supplies for the nether trip.`,
    () => sale('NETHERITE_INGOT', 2, 6000, true),
    `mail give ${both} money 500 Weekly login bonus.`,
    `mail send ${both} Market day is Sunday at spawn. Bring your goods!`,
    `mail give ${both} item TORCH 64 Torches for the new mine.`,
    `mail give ${both} money 250 Thanks for reporting a bug!`,
  ];
  for (const step of steps) {
    if (typeof step === 'function') await step();
    else await server.run(step, /Mail sent to/);
    await sleep(1100);
  }
  await server.run('mail announce name:spring money:250 end:3d <green>Spring festival</green> this weekend at spawn!', /Campaign .*spring.* created/);
  await sleep(1200);
  await server.run('mail announce name:launch money:100 end:7d <gold>Launch week</gold> thanks for playing', /Campaign .*launch.* created/);
  await sleep(1500);
  // Rewards: three votes on three (made-up) vote sites, and a code to redeem later.
  for (const site of ['ExampleVotes', 'SampleServers', 'DemoList']) {
    await sendVote(dir, PLAYER, site);
    await sleep(1200);
  }
  await server.run('mail code create SPRING uses:2 per-player:1 money:50 Spring reward', /Code .*SPRING.* created/);
  const launch = hook.bodies.find((body) => /Launch week/.test(body.body));
  if (launch && !ONLY) fs.writeFileSync(path.join(consoleDir, 'discord-campaign.json'), JSON.stringify(JSON.parse(launch.body), null, 2) + '\n');
  log(`setup done (webhook posts: ${hook.bodies.length})`);
}

/** Saves the console lines a command prints (without the log prefix). */
async function consoleOutput(server, command, file, settle = 2500) {
  if (ONLY) return [];
  const from = server.lines.length;
  server.command(command);
  await sleep(settle);
  const lines = server.lines.slice(from).map((line) => line.replace(/^\[[^\]]*INFO\]:? ?/, '')).filter((line) => line.trim() && !/\[LAB\]/.test(line));
  fs.writeFileSync(path.join(consoleDir, file), lines.join('\n') + '\n');
  log(`  console ${file}: ${lines.length} lines`);
  return lines;
}

async function scene(name, body) {
  if (ONLY && !ONLY.has(name)) return;
  try {
    await body();
  } catch (error) {
    log(`  SCENE ${name} FAILED: ${error.message}`);
    try {
      x('key', 'Escape');
    } catch {
      // ignore
    }
    await sleep(1000);
  }
}

async function scenes(server, bots, dir, hook) {
  const { Kiri } = bots;
  const sudo = (line) => server.lab(`sudo ${PLAYER} ${line}`);

  // Placeholder values while everything is still waiting.
  await sleep(4000);
  await consoleOutput(server, `papi parse ${PLAYER} unread=%donutmail_unread% waiting=%donutmail_waiting% parcels=%donutmail_parcels% cod=%donutmail_cod% rewards=%donutmail_rewards% campaigns=%donutmail_campaigns% streak=%donutmail_daily_streak% next=%donutmail_daily_next_day%`,
    'placeholders.txt');

  // A view over a cherry grove: the backdrop of every menu. Moonbeam stands on one leaf block five blocks up.
  server.mark();
  server.command('locate biome minecraft:cherry_grove');
  const located = await server.waitFor(/cherry_grove is at \[(-?\d+), [^,]+, (-?\d+)\]/, 120_000, 'locate');
  const [cx, cz] = [Number(located[1]), Number(located[2])];
  server.command(`spreadplayers ${cx} ${cz} 0 1 false ${PLAYER}`);
  await sleep(1500);
  server.mark();
  server.command(`data get entity ${PLAYER} Pos`);
  const pos = await server.waitFor(/has the following entity data: \[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/, 10_000, 'position');
  const ground = [Math.floor(Number(pos[1])), Math.floor(Number(pos[2])), Math.floor(Number(pos[3]))];
  const perch = [Number(pos[1]), Number(pos[2]) + 5, Number(pos[3])];
  log(`  ground at ${ground.join(' ')}`);
  server.command(`execute as ${PLAYER} at @s run setblock ~ ~4 ~ minecraft:cherry_leaves[persistent=true]`);
  server.command(`execute as ${PLAYER} at @s run tp @s ~ ~5 ~ 135 14`);
  server.command(`recipe give ${PLAYER} *`);
  // Advancements the received items would unlock, granted now so their toasts are gone before the shots.
  for (const id of ['end/elytra', 'story/shiny_gear', 'story/mine_diamond', 'nether/obtain_blaze_rod', 'story/smelt_iron']) {
    server.command(`advancement grant ${PLAYER} only minecraft:${id}`);
  }
  server.command(`clear ${PLAYER}`);
  // What Moonbeam will send: two books and pearls (sold to Kiri), a totem and apples (the GIF), blaze rods
  // (declined by Kiri) and saplings (a gift to Willow).
  server.command(`item replace entity ${PLAYER} hotbar.0 with enchanted_book[stored_enchantments={mending:1}] 1`);
  server.command(`item replace entity ${PLAYER} hotbar.1 with enchanted_book[stored_enchantments={unbreaking:3}] 1`);
  server.command(`item replace entity ${PLAYER} hotbar.2 with ender_pearl 16`);
  server.command(`item replace entity ${PLAYER} hotbar.3 with ender_pearl 16`);
  server.command(`item replace entity ${PLAYER} hotbar.4 with totem_of_undying 1`);
  server.command(`item replace entity ${PLAYER} hotbar.5 with golden_apple 16`);
  server.command(`item replace entity ${PLAYER} hotbar.6 with blaze_rod 32`);
  server.command(`item replace entity ${PLAYER} hotbar.7 with cherry_sapling 8`);
  server.command(`item replace entity ${PLAYER} hotbar.8 with air`);
  await sleep(60_000); // terrain loads slowly with software rendering

  await scene('join summary', async () => {
    // The daily reward and the join summary arrived while the world loaded; the open chat shows them.
    x('key', 't');
    await sleep(1200);
    screenshot('join-summary');
    x('key', 'Escape');
    await sleep(800);
  });

  await scene('scenery', async () => {
    x('key', 'F1');
    await sleep(1500);
    screenshot('scenery');
    x('key', 'F1');
    await sleep(800);
  });

  // Inbox: [9] the launch campaign, [10] the spring campaign, then sixteen mails newest first: [26] is the oldest, the
  // build contest prize, at the end of the second row.
  await scene('inbox', async () => {
    await sudo('mail');
    await sleep(2500);
    await hover(26);
    screenshot('inbox');
    await hover(9);
    screenshot('inbox-campaign');
    await hover(4);
    screenshot('mailbox-profile');
  });

  // Parcels: [9] Kiri's cash-on-delivery elytra kit, then newest first: [10] Kiri's gear, [11] Willow's shulker box...
  await scene('parcels', async () => {
    await clickSlot(3, 1800);
    await hover(9);
    screenshot('parcels-cod');
    // Right-click previews a parcel before claiming it.
    await clickAt(slot(10), 1800, 3);
    await hover(0);
    screenshot('preview');
    await hover(49);
    screenshot('preview-info');
    await clickSlot(45, 1800);
  });

  await scene('cash on delivery', async () => {
    await clickSlot(9, 1800);
    await hover(47);
    screenshot('cod-preview');
    await hover(51);
    screenshot('cod-decline');
    await clickSlot(47, 2200);
  });

  // Rewards: the calendar (day 4 today) and [18] the daily reward, [19] the vote reward.
  await scene('rewards', async () => {
    await sudo('mail rewards');
    await sleep(2500);
    await hover(16);
    screenshot('rewards');
    await hover(13);
    screenshot('rewards-today');
    await hover(19);
    screenshot('rewards-vote');
    // Redeem a Code: the menu closes, the code is typed in chat, the tab opens again.
    await clickSlot(51, 1500);
    await typeInChat('SPRING', false);
    await sleep(500);
    screenshot('redeem-prompt');
    x('key', 'Return');
    await sleep(2500); // the plugin opens the Rewards tab again
    await hover(51);
    screenshot('redeem');
    x('key', 'Escape');
    await sleep(1000);
  });

  // Selling to Kiri with cash on delivery: the parcel menu, a note typed in chat, the items, Send.
  await scene('compose', async () => {
    await sudo('mail send Kiri cod 2500');
    await sleep(2500);
    await clickSlot(48, 1500);
    await typeInChat('Mending and Unbreaking III, plus the pearls as agreed.');
    await sleep(1800);
    await moveIn(0, 0);
    await moveIn(1, 1);
    await moveIn(2, 2);
    await moveIn(3, 3);
    await hover(49, 1000);
    screenshot('compose');
    mark(Kiri);
    await clickSlot(49, 2500);
    await waitChat(Kiri, /sent you a parcel for/);
    await botAnswer(Kiri, true);
    await sleep(1800);
    screenshot('paid-chat');
    await sudo('mail');
    await sleep(2500);
    await hover(11); // after the two campaigns: the newest mail
    screenshot('receipt');
    x('key', 'Escape');
    await sleep(1000);
  });

  // The GIF: another sale in real time, from an empty parcel menu to the payment arriving.
  await scene('gif', async () => {
    await sleep(8000); // the parcel cooldown
    const recording = record('cod', 15);
    await sleep(300);
    await sudo('mail send Kiri cod 1.5k');
    await sleep(1300);
    await moveIn(4, 0);
    await moveIn(5, 1);
    await hover(49, 1300);
    mark(Kiri);
    await clickSlot(49, 900);
    await waitChat(Kiri, /sent you a parcel for/);
    await botAnswer(Kiri, true);
    await sleep(1600);
    await sudo('mail');
    await sleep(1300);
    await hover(11, 2500);
    const raw = await recording;
    x('key', 'Escape');
    gif(raw, 'cod');
  });

  // A parcel Kiri declines: it comes back to Moonbeam.
  await scene('returned', async () => {
    await sleep(9000);
    await sudo('mail send Kiri cod 400');
    await sleep(2000);
    await moveIn(6, 0);
    mark(Kiri);
    await clickSlot(49, 1500);
    await waitChat(Kiri, /sent you a parcel for/);
    await botAnswer(Kiri, false);
    await sleep(1600);
    await sudo('mail');
    await sleep(2500);
    await hover(11);
    screenshot('returned');
    x('key', 'Escape');
    await sleep(1000);
  });

  // A gift to Willow, who is offline; then the Sent tab and taking a parcel back.
  await scene('sent', async () => {
    await sleep(9000);
    await sudo('mail send Willow');
    await sleep(2000);
    await moveIn(7, 0);
    await clickSlot(49, 2000);
    await sudo('mail sent');
    await sleep(2500);
    await hover(12);
    screenshot('sent');
    await clickSlot(9, 1800);
    await hover(51);
    screenshot('take-back');
    x('key', 'Escape');
    await sleep(1000);
  });

  // A mailbox in the grove: a lectern on the ground below the perch, made a mailbox with /mail admin setblock.
  await scene('mailbox block', async () => {
    const [gx, , gz] = ground;
    /** The height of the ground at x, z (the first air above it), read from a marker entity. */
    const surface = async (bx, bz) => {
      server.command(`execute positioned ${bx} 0 ${bz} positioned over motion_blocking_no_leaves run summon minecraft:marker ~ ~ ~ {Tags:["probe"]}`);
      await sleep(500);
      server.mark();
      server.command('data get entity @e[type=minecraft:marker,tag=probe,limit=1] Pos');
      const found = await server.waitFor(/has the following entity data: \[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/, 10_000, 'surface');
      server.command('kill @e[type=minecraft:marker,tag=probe]');
      await sleep(300);
      return Math.floor(Number(found[2]));
    };
    const [lx, lz] = [gx - 2, gz - 2];
    // The player's own ground (the position read after spreadplayers can be above it, still falling). The lectern
    // stands on the same level, two blocks ahead: the 3x3 patch between is cleared up to head height and its holes
    // filled, so no slope or plant hides the lectern from the crosshair.
    const gy = await surface(gx, gz);
    const ly = gy;
    server.command(`fill ${lx} ${gy} ${lz} ${gx} ${gy + 2} ${gz} air`);
    server.command(`fill ${lx} ${gy - 1} ${lz} ${gx} ${gy - 1} ${gz} grass_block replace air`);
    server.command(`setblock ${lx} ${ly} ${lz} lectern[facing=east]`);
    server.command(`setblock ${lx} ${ly} ${lz + 1} lantern`);
    // Look at the lectern from eye height (tp ... facing aims from the feet).
    const [dx, dy, dz] = [lx - gx, ly + 0.45 - (gy + 1.62), lz - gz];
    const yaw = (Math.atan2(-dx, dz) * 180) / Math.PI;
    const pitch = (-Math.atan2(dy, Math.hypot(dx, dz)) * 180) / Math.PI;
    server.command(`op ${PLAYER}`);
    server.command(`tp ${PLAYER} ${gx + 0.5} ${gy} ${gz + 0.5} ${yaw.toFixed(1)} ${pitch.toFixed(1)}`);
    log(`  lectern at ${lx} ${ly} ${lz}, player at ${gx} ${gy} ${gz}`);
    if (ONLY) x('key', '9'); // a retake holds the empty last hotbar slot (in a full run the first one is empty by now)
    await sleep(6000);
    await typeInChat('/mail admin setblock');
    await sleep(1500);
    screenshot('mailbox-block');
    const saved = fs.readFileSync(path.join(dir, 'plugins', 'DonutMail', 'config.yml'), 'utf8').match(/locations:[^\n]*(\n\s+- [^\n]*)*/);
    log(`  mailbox blocks in config.yml: ${saved ? saved[0].replace(/\s+/g, ' ') : '?'}`);
    x('button', 3); // right-click the block in front, without moving the camera
    await sleep(2500);
    screenshot('mailbox-block-open', { dir: checks });
    x('key', 'Escape');
    await sleep(1000);
    // Back up to the perch for the rest.
    server.command(`tp ${PLAYER} ${perch[0]} ${perch[1]} ${perch[2]} 135 14`);
    await sleep(4000);
  });

  // Live notices: Kiri sends a parcel while Moonbeam is in the world.
  await scene('live', async () => {
    server.command('give Kiri golden_carrot 32');
    await sleep(1000);
    await botParcel(Kiri, PLAYER, ['golden_carrot']);
    await sleep(700);
    screenshot('live-title');
  });

  // A campaign started while Moonbeam is online: the broadcast in chat, and the post to the webhook.
  await scene('campaign', async () => {
    await sleep(3000);
    await server.run('mail announce name:weekend money:250 end:2d <aqua>Weekend event</aqua>: double vote rewards until Sunday', /Campaign .*weekend.* created/);
    await sleep(1800);
    screenshot('campaign-chat');
    if (hook.bodies[1]) fs.writeFileSync(path.join(consoleDir, 'discord-campaign-weekend.json'), JSON.stringify(JSON.parse(hook.bodies[1].body), null, 2) + '\n');
  });

  // Claim All on a full Inbox.
  await scene('claim all', async () => {
    await sudo('mail');
    await sleep(2500);
    await clickSlot(47, 4000);
    await park();
    screenshot('claim-all');
    x('key', 'Escape');
    await sleep(1000);
  });

  // Staff: Moonbeam is made an operator for the rest.
  server.command(`op ${PLAYER}`);
  await sleep(1500);
  await scene('staff view', async () => {
    // Willow's mailbox: Moonbeam's parcel [0], the thirteen server mails [1-13], the daily reward [14].
    await sudo('mail admin view Willow');
    await sleep(2500);
    await hover(8);
    screenshot('admin-view');
    x('key', 'Escape');
    await sleep(1000);
  });

  await scene('admin info', async () => {
    await typeInChat('/mail admin info');
    await sleep(1200);
    screenshot('admin-info');
    await consoleOutput(server, 'mail admin info', 'admin-info.txt');
  });

  // A shift-click check of the parcel menu's Send button (kept outside the listing images).
  await scene('shift-click check', async () => {
    server.command(`item replace entity ${PLAYER} hotbar.8 with cherry_sapling 4`);
    await sleep(800);
    await sudo('mail send Kiri');
    await sleep(2000);
    x('shiftclick', ...ownSlot(8));
    await sleep(900);
    await hover(49);
    screenshot('send-after-shift-click', { dir: checks });
    x('key', 'Escape');
    await sleep(1200);
  });

  if (ONLY) return;
  // The console's answers to the campaign and code commands of the setup (shown on the cards).
  const answer = (pattern) => (server.lines.find((line) => pattern.test(line)) || '').replace(/^\[[^\]]*INFO\]:? ?/, '');
  fs.writeFileSync(path.join(consoleDir, 'announce.txt'), answer(/Campaign .*launch.* created/) + '\n');
  fs.writeFileSync(path.join(consoleDir, 'code-create.txt'), answer(/Code .*SPRING.* created/) + '\n');
  const from = server.lines.length;
  server.command('lab events');
  await sleep(1500);
  const events = server.lines.slice(from).find((line) => /\[LAB\] events/.test(line));
  if (events) fs.writeFileSync(path.join(consoleDir, 'events.txt'), events.replace(/.*\[LAB\] /, '') + '\n');
  const audit = path.join(dir, 'plugins', 'DonutMail', 'logs');
  const auditFile = fs.existsSync(audit) ? fs.readdirSync(audit).find((f) => f.startsWith('audit-')) : null;
  if (auditFile) fs.copyFileSync(path.join(audit, auditFile), path.join(consoleDir, 'audit.txt'));
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
