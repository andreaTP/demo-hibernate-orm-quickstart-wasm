Notes on running on WASM:

- Dev service for sqlite is not used
- Alter sequence is not really supported by SQLite, it was:

> ALTER SEQUENCE known_fruits_id_seq RESTART WITH 4;
