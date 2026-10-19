# Security Policy

## Reporting a vulnerability

Please report security issues **privately**, not as public issues:

1. Preferred: use GitHub Security Advisories —
   **Security tab → Report a vulnerability** on this repository
   (https://github.com/apoorvsingh090/strengthlog/security/advisories/new).
2. If you cannot use advisories, contact [@apoorvsingh090](https://github.com/apoorvsingh090)
   through GitHub's private contact options.

You should receive an acknowledgement within a few days. Please allow
reasonable time for a fix before any public disclosure.

## Scope

Relevant vulnerabilities include:

- Data exposure from the local Room database or CSV export/import paths
  (e.g. path traversal or malformed CSV handling in
  `app/src/main/java/com/lolmax/app/data/csv/`)
- Injection or unsafe parsing in workout/exercise imports
- Anything that would require or sneak in the `INTERNET` permission

## Design note

StrengthLog is offline by construction: no `INTERNET` permission, no accounts,
no telemetry. A finding that requires network access to exploit is usually out
of scope unless it also affects local data.

## Supported versions

Only the latest code on `main` is supported. There are no published versioned
releases yet; pull the newest commit to get fixes.
