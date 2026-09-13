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

The mod config also includes `tankTiersCanConnect`. When set to `true`, tanks from
different tiers can connect to the same network. When set to `false`, tanks only
connect to tanks of the same tier.

## Tank Networks

Tanks do not automatically connect just because they are adjacent. They join a network only when:

- connected with the Wrench, or
- placed directly against an existing targeted tank block.

Network rules:

- Tank tier compatibility follows `tankTiersCanConnect`: when it is `false`,
  only tanks of the same tier can connect.
- A tank placed into an existing network links to the targeted tank and to any
  adjacent compatible tanks that are already part of that network.
- Joining two networks links every pair of compatible adjacent tanks in the
  resulting network.
- Empty tank to empty tank: allowed.
- Empty tank to liquid tank: allowed.
- Same liquid to same liquid: allowed.
- Different liquids: blocked.
- Already connected adjacent tanks can be separated with the Wrench.

## Wrench Usage

Use the Wrench in your main hand:

1. Right-click a tank to select it. The selected tank gets a yellow outline.
2. Right-click an adjacent tank to connect its network, or disconnect the first
   selected tank from its current network.
3. Valid actions flash both tanks green.
4. Invalid actions flash both tanks red.
5. Right-click the selected tank again to clear the selection.

Only adjacent tanks can be selected as the second target.

When disconnecting from a network of three or more tanks, every direct link
from the first selected tank is removed. A two-tank network is split at the
selected connection.

## Building

Requirements:

- Java 25.
- Minecraft `26.2`.
- NeoForge `26.2.0.82`.

Build the mod jar:

```bash
./gradlew build
```

```powershell
.\gradlew.bat build
```

The jar is written to:

```text
build/libs/acidglowsfluidtanks-1.0.3.jar
```

## License

This project is licensed under the [MIT License](LICENSE).
