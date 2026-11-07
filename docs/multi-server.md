# Several servers

Point every server at the same MySQL or MariaDB database and mail follows players across the network: a parcel sent on one server reaches its recipient on another, campaigns and reward codes are shared, and each player's daily reward is delivered once per day across all servers.

## Set up MySQL

On **every** server:

```yaml
storage:
  type: MYSQL
  mysql:
    host: db.example.com
    port: 3306
    database: donutmail
    username: mail
    password: change-me
    pool-size: 10

network:
  server-id: survival-1      # optional; a name for this server in the audit log
  poll-interval-seconds: 3
```

Storage settings need a full restart, not `/mail reload`. Mail is not copied between storage types: to move a single server from YAML or SQLite, start with an empty MySQL database on a quiet day, or let old mail be claimed first.

## How it stays consistent

Every change of a mail is one conditional update in MySQL: claim, payment, decline, return, expiry, recall and staff removal each check the mail's current state and change it in one step, and only one server can win. A second server that tries the same thing at the same moment waits for the first to commit, sees the mail has changed, and does nothing. MySQL runs at READ COMMITTED for DonutMail's connections, and the transactions that also create a mail (receipts, returned parcels, campaign claims, daily rewards, code rewards) commit both or neither.

Claims are tied to the server that started them. If a server stops in the middle of a claim, it finishes or undoes that claim the next time the player joins it (see [How items stay safe](safety.md)).

## Updates between servers

Each change writes a small event row in the same transaction. Every server reads new rows every `poll-interval-seconds` and:

- tells online players about new mail (chat, title, action bar, sound);
- refreshes mailbox counts and placeholders;
- reloads campaigns when one is created, ended or deleted.

Old event rows are cleaned up after an hour.

## Economy

Cash-on-delivery payments, receipts and money rewards go through Vault on the server where the player clicks. On a network, use an economy that shares balances between servers (most MySQL-backed economy plugins do).
