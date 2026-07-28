# replace-ble-bridge

Swap the uv-managed Python/bleak BLE bridge for a bundled, per-platform native (Rust/btleplug) binary
over the same raw-frame stdio seam — so runtime needs only a JVM (no Python/uv) and the app runs on
macOS, Linux, and Windows.
