# Create Aeronautics Compatibility

## Validated Jar Metadata

Checked against:

`E:\Games\CurseForge\Instances\Instances\Fractured Skies\mods\create-aeronautics-bundled-1.21.1-1.2.1.jar`

The supplied bundled jar exposes:

- Outer wrapper mod: `aeronautics_bundled` version `1.2.1`
- Nested runtime mod: `aeronautics` version `1.2.1`
- Nested runtime mod: `simulated` version `1.2.1`
- Nested runtime mod: `offroad` version `1.2.1`

The nested `aeronautics` mod declares hard dependencies on `create`, `sable`, and `simulated`.

## Dependency Policy

This mod declares `aeronautics` as a required dependency with version range `[1.2.1,)`.

It does not require `aeronautics_bundled`. The bundled wrapper is a distribution format, while `aeronautics` is the actual mod id used by the runtime Aeronautics mod. Requiring `aeronautics` keeps standalone and bundled Aeronautics distributions compatible.

No local absolute jar path is committed into Gradle. The supplied jar was used for metadata inspection only.

For local NeoForge run configs, set `aeronautics_bundled_jar` in ignored `gradle-local.properties` or set the `AERONAUTICS_BUNDLED_JAR` environment variable. The current workspace-local ignored file points at the supplied bundled jar.

## Current Integration Boundary

The MVP movement adapter is server-authoritative and resolves Aeronautics ships through Sable sublevels.

Recording starts from a linked Autopilot Seat, finds the assembled Sable sublevel containing that seat, and samples the seat's world-space position. The player does not need to sit in a seat.

Playback resolves the same Sable sublevel and applies linear velocity through Sable's physics handle. It does not teleport the ship.

Known constraint: the MVP only uses the first linked Autopilot Seat on a station. Multi-ship station routing remains a later logistics phase.

## Collision And Damage Compatibility

The automation code must not disable collisions, set vehicles invulnerable, or phase them through terrain.

Current playback only applies server-side motion toward recorded points and stops on predicted collision, stall, obstruction, invalid route, missing vehicle, missing station, or dimension mismatch. Collision damage mods should still see normal collision/damage behavior because collision and damage systems are not bypassed.

## Manual Validation Checklist

- Launch a dedicated or integrated server with Create Aeronautics `1.2.1` or newer.
- Confirm the mod fails to load when `aeronautics` is absent.
- Confirm the mod loads with the supplied bundled jar.
- Build a Sable/Aeronautics airship with an Autopilot Seat.
- Right-click the Airship Station with the Autopilot Seat item, then place the seat on the assembled ship.
- Record a route from an Airship Station while piloting.
- Reload the world and confirm the route persists.
- Start playback and confirm ping-pong movement or document the required Sable/Simulated adapter gap.
- Place an obstruction on the route and confirm playback stops without disabling collision damage.
