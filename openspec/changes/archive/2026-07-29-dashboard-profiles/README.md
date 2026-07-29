# dashboard-profiles

Named, switchable dashboard profiles (create/switch/rename/duplicate/delete different panel layouts),
persisted **server-side as JSON** — one file per profile (`dashboards/<name>.json`) via a small REST
API. The active-profile selection stays per-device (localStorage).
