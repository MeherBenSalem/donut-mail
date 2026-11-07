// Summarises the live test reports (tools/smoke writes one JSON per target to $MAILLAB_HOME/reports) into
// marketing/source/compat.json, which the compatibility card and the description's table use.
//   node tools/marketing/compat.mjs [reports dir]
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '..', '..');
const reports = process.argv[2] || path.join(process.env.MAILLAB_HOME || path.join(os.homedir(), '.cache', 'donutmail-smoke'), 'reports');
const ORDER = ['paper-1.20.1', 'folia-1.20.1', 'paper-1.21.11', 'folia-1.21.11', 'paper-26.3', 'folia-26.3', 'network-paper-1.21.11', 'upgrade-paper-26.3'];
const STORAGE = { YAML: 'YAML', SQLITE: 'SQLite', MYSQL: 'MariaDB' };
const ECONOMY = { essentials: 'Vault + EssentialsX', lab: 'VaultUnlocked' };

const pom = fs.readFileSync(path.join(repo, 'pom.xml'), 'utf8');
const plugin = pom.match(/<artifactId>donut-mail<\/artifactId>\s*<version>([^<]+)<\/version>/)?.[1] || '?';
let newest = 0;
const targets = ORDER.filter((name) => fs.existsSync(path.join(reports, `${name}.json`))).map((name) => {
  const file = path.join(reports, `${name}.json`);
  newest = Math.max(newest, fs.statSync(file).mtimeMs);
  const report = JSON.parse(fs.readFileSync(file, 'utf8'));
  const [, project, version] = report.server.match(/^(\w+) ([\w.]+)/) || [];
  const build = report.server.match(/build (\d+)/)?.[1];
  return {
    name,
    kind: name.startsWith('network') ? 'network' : name.startsWith('upgrade') ? 'upgrade' : 'single',
    project: project === 'folia' ? 'Folia' : 'Paper',
    version,
    build: build ? Number(build) : null,
    java: report.java,
    bot: report.bot,
    economy: ECONOMY[report.economy] || report.economy,
    storage: name.startsWith('upgrade') ? 'YAML (1.1.0 data)' : STORAGE[report.storage] || report.storage,
    passed: report.steps.filter((step) => step.ok).length,
    steps: report.steps.length,
    errors: report.errors.length,
    ok: report.ok,
  };
});
const date = new Date(newest).toISOString().slice(0, 10);
const target = path.join(repo, 'marketing', 'source', 'compat.json');
fs.writeFileSync(target, JSON.stringify({ plugin, date, targets }, null, 2) + '\n');
console.log(`${path.relative(repo, target)}: ${targets.length} targets, ${targets.filter((t) => t.ok).length} passed (${date})`);
