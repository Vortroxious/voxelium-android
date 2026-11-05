# Control layout fixtures

Written by the iOS app's own encoder (`ControlLayoutStore.write` in
`Sources/VoxeliumCore/Controls/ControlLayout.swift`, zalithios e28b28b), compiled with
`swiftc` on macOS. `ControlLayoutTests` checks that the Kotlin port reads them and writes
the same bytes back, so a `layout.json` moves between the apps unchanged.

- `default-932x430.json`: `ControlLayout.amethystDefault(width: 932, height: 430)`
- `custom-667x375.json`: the 667 × 375 default with opacity 0.65, joystick size 180 and an
  extra toggle button `Drop / "Q" ü` (keys 81 + 340, id `11111111-2222-4333-8444-555555555555`,
  x 0.5, y 0.25, 50 × 60.5, shown in menus)
