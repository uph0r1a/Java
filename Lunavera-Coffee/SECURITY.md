# Security

Lunavera Coffee holds staff passwords (as hashes), PINs, customer names and phone numbers, and the shop's takings, so a hole in it matters.

## Reporting a hole

**Please do not open a public issue for a security problem.**

- On GitHub, use **Security → Report a vulnerability** (private reporting) on this repository, or
- tell the shop owner who gave you the program, in person or by a private message.

Say what you did, what you saw, the version (the title of `CHANGELOG.md`'s newest entry) and whether it was SQLite or MariaDB. Please do not include real customer data or real passwords.

## What to expect

This is a small project kept by its owner, so there is no promised response time. A report is read, answered, and fixed in the next version where it can be reproduced; the fix is written in `CHANGELOG.md` without the steps to exploit it.

## Supported versions

Only the newest version is fixed.

## What is already in place

The list of protections and the ones still open are in `TODO.md` section 2A (security) and the finished ones in `COMPLETE.md`. For a shop that uses MariaDB, `docs/DATABASE.md` section 7 explains the settings (a database user that is not `root`, TLS for another till on the network).
