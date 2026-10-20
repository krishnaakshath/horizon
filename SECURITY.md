# Security policy

## Reporting a vulnerability

Please do not open a public issue for security problems. Use GitHub's private reporting instead: open the
repository's **Security** tab and choose **Report a vulnerability**. Include the affected module and version, steps to
reproduce, and the impact you see.

You can expect an acknowledgement within a few days. Fixes are released as a new version, and the report is credited in
the changelog if you want that.

## Supported versions

Only the latest release receives fixes.

## Scope

In scope: the launcher and companion apps, the signature-permission channels between them, the phone remote server
(pairing and local web page), and the provisioning tool.

Useful background: the launcher has no INTERNET permission; Browser, Store, Weather and Link are the only modules with
network access; the modules talk to each other only through signature-protected broadcasts and providers.
