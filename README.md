# Acidglow's Fluid Tanks

A NeoForge mod that adds tiered fluid tanks for storing, moving, and carrying fluids.

## Features

- Six tank tiers: Copper, Iron, Gold, Diamond, Emerald, and Netherite.
- Stores Minecraft fluids and compatible modded NeoForge fluids.
- Tanks keep their fluid when broken and placed again.
- Tank item tooltips show stored fluid and amount, for example `Lava: 1000/16000mb`.
- Compatible with fluid pipe I/O through NeoForge fluid capabilities.
- Filled tanks render their fluid in-world and in-hand.
- Modded fluids use their baked fluid texture and tint when available.
- Lava-filled tanks emit light.
- Full tanks reject bucket filling, so the bucket keeps its fluid instead of placing it outside the tank.

## Tank Capacity

Default capacities:

| Tank | Capacity |
| --- | ---: |
| Copper | 8,000 mb |
| Iron | 16,000 mb |
| Gold | 32,000 mb |
| Diamond | 64,000 mb |
| Emerald | 128,000 mb |
| Netherite | 256,000 mb |

Capacity can be scaled with the mod config capacity multiplier.

## Tank Networks

Tanks do not automatically connect just because they are adjacent. They join a network only when:

- connected with the Wrench, or
- placed directly against an existing targeted tank block.

Network rules:

- Empty tank to empty tank: allowed.
- Empty tank to liquid tank: allowed.
- Same liquid to same liquid: allowed.
- Different liquids: blocked.
- Already connected adjacent tanks can be separated with the Wrench.

## Wrench Usage

Use the Wrench in your main hand:

1. Right-click a tank to select it. The selected tank gets a yellow outline.
2. Right-click an adjacent tank to connect or disconnect it.
3. Valid actions flash both tanks green.
4. Invalid actions flash both tanks red.
5. Right-click the selected tank again to clear the selection.

Only adjacent tanks can be selected as the second target.

## Building

Requirements:

- Java compatible with the configured NeoForge toolchain.
- Minecraft `26.2`.
- NeoForge `26.2.0.23-beta`.

Build the mod jar:

```powershell
.\gradlew.bat build
```

The jar is written to:

```text
build/libs/acidglowsfluidtanks-1.0.0.jar
```
