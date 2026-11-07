// Boots real Paper and Folia servers and drives DonutMail with mineflayer bots over the network protocol.
//
// Usage (from the repository root, after `mvn package` and the fixture build, see README.md):
//   node tools/smoke/run.mjs                    # every target
//   node tools/smoke/run.mjs paper-1.21.11      # selected targets
//
// Downloads (server jars from PaperMC Fill, plugins from Modrinth and GitHub, Temurin JDKs) are verified
// against their published checksums and cached in $MAILLAB_HOME/dl (default ~/.cache/donutmail-smoke).
import { spawn, execFileSync } from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import http from 'node:http';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import mineflayer from 'mineflayer';
import { coreScenario, networkScenario, upgradeScenario } from './scenario.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '..', '..');
export const lab = process.env.MAILLAB_HOME || path.join(os.homedir(), '.cache', 'donutmail-smoke');
const dl = path.join(lab, 'dl');
const BASE_PORT = Number(process.env.MAILLAB_PORT || 25601);
const MYSQL = {
  host: process.env.MAILLAB_MYSQL_HOST || '127.0.0.1',
  port: Number(process.env.MAILLAB_MYSQL_PORT || 3307),
  user: process.env.MAILLAB_MYSQL_USER || 'mail',
  password: process.env.MAILLAB_MYSQL_PASSWORD || 'mailpass',
};

// economy: 'essentials' = Vault + EssentialsX (official downloads); 'lab' = VaultUnlocked + the fixture's economy (Folia).
export const MATRIX = [
  { name: 'paper-1.20.1', kind: 'core', project: 'paper', version: '1.20.1', java: 17, bot: '1.20.1', economy: 'essentials', storage: 'YAML' },
  { name: 'paper-1.21.11', kind: 'core', project: 'paper', version: '1.21.11', java: 21, bot: '1.21.11', economy: 'essentials', storage: 'SQLITE',
    extras: ['votes', 'discord', 'crash'] },
  { name: 'folia-1.21.11', kind: 'core', project: 'folia', version: '1.21.11', java: 21, bot: '1.21.11', economy: 'lab', storage: 'SQLITE' },
  { name: 'paper-26.3', kind: 'core', project: 'paper', version: '26.3', java: 25, bot: '26.1', via: true, economy: 'essentials', storage: 'MYSQL' },
  { name: 'folia-26.3', kind: 'core', project: 'folia', version: '26.3', java: 25, bot: '26.1', via: true, economy: 'lab', storage: 'SQLITE' },
  { name: 'network-paper-1.21.11', kind: 'network', project: 'paper', version: '1.21.11', java: 21, bot: '1.21.11', economy: 'lab', storage: 'MYSQL' },
  { name: 'upgrade-paper-26.3', kind: 'upgrade', project: 'paper', version: '26.3', java: 25, bot: '26.1', via: true, economy: 'lab' },
  { name: 'folia-1.20.1', kind: 'core', project: 'folia', version: '1.20.1', java: 17, bot: '1.20.1', economy: 'lab', storage: 'YAML' },
];

export const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// ── Verified downloads ──────────────────────────────────────────────────────

async function fetchBuffer(url) {
  const response = await fetch(url, { headers: { 'User-Agent': 'NightBeam-DonutMail-smoke' } });
  if (!response.ok) throw new Error(`${url}: HTTP ${response.status}`);
  return Buffer.from(await response.arrayBuffer());
}

function save(file, data, algorithm, expected) {
  const digest = crypto.createHash(algorithm).update(data).digest('hex');
  if (expected && digest !== expected) throw new Error(`${path.basename(file)}: ${algorithm} ${digest} does not match ${expected}`);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, data);
  return file;
}

function sha256(file) {
  return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
}

async function serverJar(project, version) {
  const build = JSON.parse((await fetchBuffer(`https://fill.papermc.io/v3/projects/${project}/versions/${version}/builds/latest`)).toString());
  const download = build.downloads['server:default'];
  const file = path.join(dl, `${project}-${version}.jar`);
  if (fs.existsSync(file) && sha256(file) === download.checksums.sha256) return { file, build: build.id, sha256: download.checksums.sha256 };
  save(file, await fetchBuffer(download.url), 'sha256', download.checksums.sha256);
  return { file, build: build.id, sha256: download.checksums.sha256 };
}

async function modrinth(project, version, name) {
  const file = path.join(dl, name);
  if (fs.existsSync(file)) return file;
  const versions = JSON.parse((await fetchBuffer(`https://api.modrinth.com/v2/project/${project}/version`)).toString());
  const entry = versions.find((v) => v.version_number === version);
  const primary = entry.files.find((f) => f.primary) || entry.files[0];
  return save(file, await fetchBuffer(primary.url), 'sha512', primary.hashes.sha512);
}

async function github(url, name) {
  const file = path.join(dl, name);
  if (fs.existsSync(file)) return file;
  return save(file, await fetchBuffer(url), 'sha256', null);
}

const PLUGINS = {
  vault: () => github('https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar', 'Vault-1.7.3.jar'),
  essentials: () => modrinth('essentialsx', '2.22.0', 'EssentialsX-2.22.0.jar'),
  vaultunlocked: () => modrinth('vaultunlocked', '2.20.3', 'VaultUnlocked-2.20.3.jar'),
  placeholderapi: () => modrinth('placeholderapi', '2.12.3', 'PlaceholderAPI-2.12.3.jar'),
  viaversion: () => modrinth('viaversion', '5.12.2', 'ViaVersion-5.12.2.jar'),
  viabackwards: () => modrinth('viabackwards', '5.12.2', 'ViaBackwards-5.12.2.jar'),
  votifier: () => github('https://github.com/NuVotifier/NuVotifier/releases/download/v2.7.3/nuvotifier.jar', 'NuVotifier-2.7.3.jar'),
};

function javaFor(major) {
  const fromEnv = process.env[`JAVA${major}_HOME`];
  if (fromEnv) return path.join(fromEnv, 'bin', 'java');
  const cached = path.join(dl, `jdk${major}`, 'bin', 'java');
  if (fs.existsSync(cached)) return cached;
  throw new Error(`Set JAVA${major}_HOME or put a JDK ${major} in ${path.dirname(path.dirname(cached))}`);
}

function pluginJar() {
  if (process.env.PLUGIN_JAR) return process.env.PLUGIN_JAR;
  const jar = path.join(repo, 'target', fs.readdirSync(path.join(repo, 'target')).find((f) => /^DonutMail-.*\.jar$/.test(f)) || 'missing');
  if (!fs.existsSync(jar)) throw new Error('Build the plugin first: mvn -DskipTests package');
  return jar;
}

export function pluginJarPath() {
  return pluginJar();
}

export function fetchVotifier() {
  return PLUGINS.votifier();
}

export function fixtureJar() {
  return path.join(here, 'fixture', 'target', 'MailLabFixture.jar');
}

// ── Servers ─────────────────────────────────────────────────────────────────

/** One running server: stdin for console commands, and a line log that waitFor() scans. */
export class Server {
  constructor(dir, java, jar, port, jvmArgs = []) {
    Object.assign(this, { dir, java, jar, port, jvmArgs, lines: [], cursor: 0, waiters: [] });
  }

  start(extraArgs = []) {
    const log = fs.createWriteStream(path.join(this.dir, 'console.log'), { flags: 'a' });
    this.process = spawn(this.java, ['-Xms512M', '-Xmx1536M', '-Dterminal.jline=false', '-Dterminal.ansi=false', '-Dfile.encoding=UTF-8',
      '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-Dpaper.disableChannelLimit=true', ...this.jvmArgs, ...extraArgs,
      '-jar', this.jar, '--nogui'], { cwd: this.dir });
    let buffer = '';
    const onData = (chunk) => {
      log.write(chunk);
      buffer += chunk.toString('utf8');
      let index;
      while ((index = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, index).replace(/\x1b\[[0-9;]*m/g, '');
        buffer = buffer.slice(index + 1);
        this.lines.push(line);
        this.waiters = this.waiters.filter((waiter) => !waiter.test(line));
      }
    };
    this.process.stdout.on('data', onData);
    this.process.stderr.on('data', onData);
    this.exited = new Promise((resolve) => this.process.on('exit', (code) => resolve(code)));
    return this.waitFor(/Done \([0-9.,]+s\)!/, 300_000, 'server start');
  }

  waitFor(pattern, timeout = 30_000, label = String(pattern)) {
    for (let i = this.cursor; i < this.lines.length; i++) {
      const match = this.lines[i].match(pattern);
      if (match) {
        this.cursor = i + 1;
        return Promise.resolve(match);
      }
    }
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.waiters = this.waiters.filter((waiter) => waiter !== entry);
        reject(new Error(`timed out waiting for ${label}`));
      }, timeout);
      const entry = {
        test: (line) => {
          const match = line.match(pattern);
          if (!match) return false;
          clearTimeout(timer);
          this.cursor = this.lines.length;
          resolve(match);
          return true;
        },
      };
      this.waiters.push(entry);
    });
  }

  mark() {
    this.cursor = this.lines.length;
  }

  command(line) {
    this.process.stdin.write(line + '\n');
  }

  /** Runs a console command and waits for a matching line. */
  async run(line, expect, timeout = 15_000) {
    this.mark();
    this.command(line);
    return (await this.waitFor(expect, timeout, line))[0];
  }

  /** Runs a fixture command and returns the [LAB] line it prints. */
  async lab(line, expect) {
    this.mark();
    this.command('lab ' + line);
    const match = await this.waitFor(expect || new RegExp(`\\[LAB\\] (${line.split(' ')[0]}|error) .*`), 20_000, `lab ${line}`);
    if (match[0].includes('[LAB] error')) throw new Error(match[0]);
    return match[0].replace(/.*\[LAB\] /, '');
  }

  async stop() {
    if (!this.process || this.process.exitCode !== null) return;
    this.command('stop');
    const timeout = sleep(120_000).then(() => 'timeout');
    if ((await Promise.race([this.exited, timeout])) === 'timeout') this.process.kill('SIGKILL');
  }
}

/** A mineflayer bot with its chat recorded so scenarios can wait for plugin messages. */
export async function joinBot(name, version, port, chatFile) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port, username: name, version, auth: 'offline', checkTimeoutInterval: 180_000 });
  if (chatFile) bot.on('messagestr', (message) => fs.appendFileSync(chatFile, `${new Date().toISOString()} ${message}\n`));
  // The bots never move: with ViaBackwards, newer servers can reject translated movement packets.
  bot.physicsEnabled = false;
  bot.once('login', () => { bot.physicsEnabled = false; });
  const write = bot._client.write.bind(bot._client);
  bot._client.write = (packet, params) => (/^(position|position_look|look|flying)$/.test(packet) ? undefined : write(packet, params));
  bot.chatLog = [];
  bot.on('messagestr', (message) => bot.chatLog.push(message));
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${name} did not spawn`)), 120_000);
    bot.once('spawn', () => { clearTimeout(timer); resolve(); });
    bot.once('kicked', (reason) => { clearTimeout(timer); reject(new Error(`${name} kicked: ${JSON.stringify(reason)}`)); });
    bot.once('error', (error) => { clearTimeout(timer); reject(error); });
  });
  await sleep(1500);
  return bot;
}

function serverProperties(port) {
  return [
    'online-mode=false', 'white-list=false', 'enforce-secure-profile=false', `server-port=${port}`, 'level-type=minecraft\\:flat',
    'generate-structures=false', 'spawn-protection=0', 'view-distance=3', 'simulation-distance=3', 'spawn-monsters=false',
    'spawn-animals=false', 'difficulty=peaceful', 'motd=DonutMail live test', 'max-players=20',
  ].join('\n') + '\n';
}

/** The config.yml the scenarios rely on; DonutMail adds every other default on start. */
export function smokeConfig({ storage, database = 'donutmail_test', serverId = '', extras = [] }) {
  const lines = [
    'storage:', `  type: ${storage}`, '  mysql:', `    host: ${MYSQL.host}`, `    port: ${MYSQL.port}`, `    database: ${database}`,
    `    username: ${MYSQL.user}`, `    password: ${MYSQL.password}`, '    pool-size: 6',
    'network:', `  server-id: '${serverId}'`, '  poll-interval-seconds: 1',
    'mail:', '  expiry-scan-seconds: 5',
    // About 100 seconds, so expiry can be watched in a test run.
    'parcels:', '  expire-days: 0.0012', '  cooldown-seconds: 3', '  max-per-day: 100', '  max-pending-per-recipient: 3',
    'codes:', '  attempt-cooldown-seconds: 1',
    'notifications:', '  join-delay-seconds: 1',
  ];
  if (extras.includes('votes')) {
    lines.push('votes:', '  enabled: true', '  money: 10', "  items: ['DIAMOND:1']");
  }
  if (extras.includes('discord')) {
    lines.push('discord:', '  enabled: true', "  webhook-url: 'http://127.0.0.1:18089/hook'");
  }
  return lines.join('\n') + '\n';
}

export async function prepareServer(target, name, port, { storage, database, serverId, extras = [], jars: extraJars = [], donutJar = pluginJar(), config = true, fixture = true } = {}) {
  const dir = path.join(lab, 'servers', name);
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(path.join(dir, 'plugins'), { recursive: true });
  fs.writeFileSync(path.join(dir, 'eula.txt'), 'eula=true\n');
  fs.writeFileSync(path.join(dir, 'server.properties'), serverProperties(port));
  const jars = [donutJar, await PLUGINS.placeholderapi(), ...extraJars];
  if (fixture) jars.push(fixtureJar());
  if (target.economy === 'essentials') jars.push(await PLUGINS.vault(), await PLUGINS.essentials());
  else jars.push(await PLUGINS.vaultunlocked());
  if (target.via) jars.push(await PLUGINS.viaversion(), await PLUGINS.viabackwards());
  for (const jar of jars) fs.copyFileSync(jar, path.join(dir, 'plugins', path.basename(jar)));
  if (config) {
    fs.mkdirSync(path.join(dir, 'plugins', 'DonutMail'), { recursive: true });
    fs.writeFileSync(path.join(dir, 'plugins', 'DonutMail', 'config.yml'), smokeConfig({ storage, database, serverId, extras }));
  }
  const server = await serverJar(target.project, target.version);
  return { dir, jar: server.file, build: server.build, sha256: server.sha256 };
}

/** Plugin problems in a console log: severe/error lines from DonutMail, its stack frames and linkage errors. */
export function pluginErrors(lines) {
  const problems = [];
  lines.forEach((line) => {
    const ours = /\[DonutMail\]/.test(line) && /(ERROR|SEVERE)/.test(line) && !/\[crash test\]/.test(line);
    const frame = /at io\.nightbeam\.donutmail\./.test(line);
    const load = /Could not load '.*DonutMail|Error occurred while enabling DonutMail|NoSuchMethodError|NoClassDefFoundError|NoSuchFieldError|AbstractMethodError|IncompatibleClassChangeError/.test(line);
    if (ours || frame || load) problems.push(line);
  });
  return problems;
}

export function resetDatabase(database) {
  const sql = `DROP DATABASE IF EXISTS ${database}; CREATE DATABASE ${database};`;
  execFileSync('mariadb', ['-h', MYSQL.host, '-P', String(MYSQL.port), `-u${MYSQL.user}`, `-p${MYSQL.password}`, '-e', sql]);
}

/** A local HTTP endpoint standing in for a Discord webhook. */
export function webhookCatcher(port = 18089) {
  const bodies = [];
  const server = http.createServer((request, response) => {
    let body = '';
    request.on('data', (chunk) => { body += chunk; });
    request.on('end', () => {
      bodies.push({ path: request.url, body });
      response.writeHead(204);
      response.end();
    });
  });
  server.listen(port, '127.0.0.1');
  return { bodies, close: () => server.close() };
}

/** Sends a NuVotifier protocol v1 vote (RSA-encrypted with the server's public key). */
export async function sendVote(dir, username, service = 'MailLabList', port = 8192) {
  const keyFile = path.join(dir, 'plugins', 'Votifier', 'rsa', 'public.key');
  const der = Buffer.from(fs.readFileSync(keyFile, 'utf8').trim(), 'base64');
  const key = crypto.createPublicKey({ key: der, format: 'der', type: 'spki' });
  const payload = Buffer.from(`VOTE\n${service}\n${username}\n127.0.0.1\n${Date.now()}\n`, 'utf8');
  const block = crypto.publicEncrypt({ key, padding: crypto.constants.RSA_PKCS1_PADDING }, payload);
  await new Promise((resolve, reject) => {
    const socket = net.connect(port, '127.0.0.1', () => {
      socket.once('data', () => socket.end(block));
    });
    socket.on('close', resolve);
    socket.on('error', reject);
  });
}

async function runTarget(target, index) {
  const started = Date.now();
  const result = { target: target.name, java: target.java, bot: target.bot, economy: target.economy, storage: target.storage, steps: [], errors: [] };
  const step = (label, ok, detail = '') => {
    result.steps.push({ label, ok, detail });
    console.log(`  ${ok ? 'PASS' : 'FAIL'} ${label}${detail ? ` (${detail})` : ''}`);
  };
  const context = { target, step, port: BASE_PORT + index * 2, java: javaFor(target.java), result };
  try {
    if (target.kind === 'core') await coreScenario(context);
    else if (target.kind === 'network') await networkScenario(context);
    else await upgradeScenario(context);
  } catch (error) {
    step('scenario', false, error.stack ? error.stack.split('\n').slice(0, 3).join(' | ') : String(error));
  }
  result.ok = result.steps.length > 0 && result.steps.every((s) => s.ok) && result.errors.length === 0;
  result.seconds = Math.round((Date.now() - started) / 1000);
  return result;
}

async function main() {
  const wanted = process.argv.slice(2);
  const targets = MATRIX.filter((t) => wanted.length === 0 || wanted.includes(t.name));
  const results = [];
  for (const target of targets) {
    console.log(`\n== ${target.name} (Java ${target.java}, bot ${target.bot}${target.via ? ' via ViaBackwards' : ''}, ${target.economy}, ${target.storage || 'YAML→2.0'})`);
    const result = await runTarget(target, MATRIX.indexOf(target));
    for (const error of result.errors.slice(0, 20)) console.log(`  ERROR ${error}`);
    results.push(result);
    fs.mkdirSync(path.join(lab, 'reports'), { recursive: true });
    fs.writeFileSync(path.join(lab, 'reports', `${target.name}.json`), JSON.stringify(result, null, 2));
  }
  console.log('\n| Target | Java | Storage | Steps | Plugin errors | Result |\n| --- | --- | --- | --- | --- | --- |');
  for (const r of results) {
    console.log(`| ${r.target} | ${r.java} | ${r.storage || '-'} | ${r.steps.filter((s) => s.ok).length}/${r.steps.length} | ${r.errors.length} | ${r.ok ? 'PASS' : 'FAIL'} |`);
  }
  process.exit(results.every((r) => r.ok) ? 0 : 1);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  main().catch((error) => {
    console.error(error);
    process.exit(1);
  });
}
