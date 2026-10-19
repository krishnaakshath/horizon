# Debloat profile format

One JSON file per TV model, named `<manufacturer>-<model>.json`.

```json
{
  "model": "32S5403AF",
  "match": { "manufacturer": "TCL", "fingerprint_prefix": "TCL/C06_2K_GB/C06:11" },
  "safe": ["com.example.bloat"],
  "optional": [ { "pkg": "com.example.feature", "breaks": "what stops working" } ],
  "never": ["com.example.critical"]
}
```

## Fields

- `model` (string, required): model identifier.
- `match.manufacturer` (string, required): value of `ro.product.manufacturer`.
- `match.fingerprint_prefix` (string, required): prefix of `ro.build.fingerprint`.
- `safe` (list of package names): disabled by default; no visible loss expected.
- `optional` (list of objects): disabled only when requested. Each needs `pkg` and a
  human-readable `breaks` note shown to the user before disabling.
- `never` (list of package names): never disabled, in addition to the built-in
  global never-touch list.

## Rules

- Only `pm disable-user --user 0` is used; nothing is uninstalled.
- Packages not installed on the device are skipped.
- Packages in `never` or the global never-touch list are removed from every plan
  even if also listed in `safe` or `optional`.
- Plans are split into batches of at most 10 packages.
- Missing or invalid fields make loading fail with an error.
