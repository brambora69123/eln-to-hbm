# HBM-ELN Bridge

A Minecraft **1.7.10** Forge mod that bridges [HBM's Nuclear Tech Mod](https://github.com/HbmMods/Hbm-s-Nuclear-Tech-GIT)
steam system with [Electrical Age](https://github.com/Electrical-Age/ElectricalAge)'s mechanical
power grid.

It adds a **Hybrid Steam Turbine** that consumes HBM steam from the HBM fluid network and outputs
**ELN mechanical shaft power**, letting you drive Electrical Age generators (and anything else on a
shaft) from an HBM boiler/reactor setup — and feed the spent low-pressure steam straight back into
your cooling towers.

> This is a standalone add-on. It does not modify HBM or ELN; it only depends on them.

## Features

- **Two variants**
  - **Large Hybrid Steam Turbine** — a 3×3×3 multiblock that matches HBM's *industrial steam turbine*
    steam-wise (750,000 mB input / 3,000,000 mB output tanks, 20% of tank consumed per tick).
  - **Small Hybrid Steam Turbine** — a single block with tanks and throughput scaled down (default 1/9).
- **HBM-accurate steam processing** — always drains steam and produces the next cooler tier
  (STEAM → SPENTSTEAM, HOTSTEAM → STEAM, …), so your boiler → turbine → cooling-tower loop never stalls.
- **Tiered operation** — right-click to cycle the steam tier
  (STEAM / HOTSTEAM / SUPERHOTSTEAM / ULTRAHOTSTEAM); tanks resize per tier like HBM.
- **Proper ELN shaft integration** — peak power at **200 rad/s**, with an overspeed governor that
  tapers power to zero by 235 rad/s so the shaft can't run away to the 250 rad/s explosion limit.
- **Signal-cable throttle** — feed a tachometer (or any gate signal) into the front/back underside to
  govern mechanical output. Steam throughput is independent of the throttle.
- **Tooling support** — responds to the multimeter and thermometer, and shows mode / speed / energy /
  efficiency / throttle in Waila.
- **Sound** — plays the ELN steam-turbine loop, with pitch/volume tracking throughput.
- **Configurable** — tank sizes, consumption %, overspeed cutoff, heat→joule factor, shaft mass, and
  more (see [Configuration](#configuration)).

## Requirements

- Minecraft 1.7.10 with Minecraft Forge
- [HBM's Nuclear Tech Mod](https://github.com/HbmMods/Hbm-s-Nuclear-Tech-GIT) (`hbm`)
- [Electrical Age](https://github.com/Electrical-Age/ElectricalAge) (`eln`)

Both are hard dependencies (`required-after:hbm;required-after:eln`).

## Usage

1. Craft/obtain the turbine (creative tab: ELN machines).
2. Place it and connect HBM steam pipes to any face of the structure.
3. Connect an ELN shaft to the shaft ports:
   - **Large:** front/back, at the vertical centre of the 3×3×3.
   - **Small:** the block's left/right faces.
4. Right-click (empty hand) to select the steam tier you're feeding it.
5. (Optional) Feed a signal wire into the front/back underside to throttle mechanical output.
6. Attach a generator (or clutch/brake) as a load. **The turbine needs a load or brake** — with no
   load and no throttle it will coast at its current speed; without any governing it relies on the
   built-in overspeed cutoff to stay under the explosion limit.

## Building

This project uses the GTNH Forge (RetroFuturaGradle) buildscript.

```bash
./gradlew build
```

### Dependencies

- **HBM** is pulled automatically from the NTM Maven (`https://maven.ntmr.dev/releases`).
- **ELN** is consumed as a local **preshadow** jar (it uses standard `kotlin.reflect.KClass`, which is
  required to compile against). Point the build at your ELN jar with a Gradle property:

  ```bash
  ./gradlew build -PelnJar=/path/to/eln-<version>-dev-preshadow.jar
  ```

  or add `elnJar=/path/to/...` to your `~/.gradle/gradle.properties`.

  The preshadow jar strips ELN's shadowed libraries, so this project re-adds them explicitly
  (`org.semver4j:semver4j`, `org.apache.commons:commons-math3`). At **runtime**, place a normal ELN
  **dev** jar in your mods folder.

> Building/running currently expects Java for the GTNH toolchain (see `.java-version` /
> `gradle-daemon-jvm.properties`).

## Configuration

Config file: `config/elnhbmbridge.cfg`. Key options:

| Option | Default | Description |
| --- | --- | --- |
| `inputTankSize` | 750000 | Input tank capacity (mB, STEAM tier) |
| `outputTankSize` | 3000000 | Output tank capacity (mB, STEAM tier) |
| `consumptionPercent` | 0.2 | Fraction of the input tank consumed per tick (HBM default) |
| `smallScale` | 0.111 | Small turbine tank/throughput as a fraction of the large one |
| `overspeedCutoffRads` | 235 | Above this speed the turbine adds no power |
| `heToJouleFactor` | 0.01 | 1 HBM heat-unit → X ELN joules (raise for more torque) |
| `shaftMass` | 5.0 | Base shaft inertia (kg·m²); the large turbine ×27 |
| `mechanicalEfficiency` | 0.9 | Bearing/mechanical efficiency |
| `soundEnabled` | true | Play the turbine sound |
| `largeDamageId` / `smallDamageId` | 200 / 201 | Item damage values |

If the turbine spins up too slowly or too quickly for your generator load, adjust `heToJouleFactor`.

## Project layout

```
src/main/kotlin/elnhbm/bridge/
├── BridgeMod.kt          # @Mod entry point
├── BridgeConfig.kt       # Forge config
├── CommonProxy.kt        # descriptor registration
├── ClientProxy.kt
├── descriptor/HybridTurbineDescriptor.kt   # multiblock/ghost group, shaft ports, model
├── element/HybridTurbineElement.kt         # steam processing + shaft power + HBM fluid net
└── render/HybridTurbineRender.kt           # shaft render + sound
```

## License

MIT — see [LICENSE](LICENSE).
