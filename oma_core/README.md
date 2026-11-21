# oma_core

MIT-licensed NeoForge 1.21.1 companion mod of Grandma's Cookie (de_de: Omas Keks). Spec:
`docs/OMA_CORE_SPEC.md`; fair-play guard: `docs/decisions/ADR-0005-fair-play-guard.md`.

## Layout

| Path | Content |
|---|---|
| `core/` | Minecraft-free Java 21 logic with JUnit tests; `noMinecraftImports` keeps it that way |
| `neoforge/` | the mod, built with the official NeoForge ModDevGradle plugin 2.0.148 against NeoForge 21.1.267 (same pin as the pack); compiles `core/` from source |
| `gradle/wrapper/` | Gradle 9.8.0 wrapper; `gradle-wrapper.jar` SHA-256 `238e777f…` equals services.gradle.org, distribution pinned with `distributionSha256Sum` |

## Build and test

```bash
cd oma_core
./gradlew build          # core unit tests, noMinecraftImports, checkNoClientRefs, mod jar
# jar: neoforge/build/libs/oma_core-<version>+mc1.21.1-neoforge.jar
```

Pack integration test (from the repository root):

```bash
python -I scripts/build_profile.py --side server --out runtime/server-p0 --local-jar oma_core/neoforge/build/libs/<jar>
python -I scripts/boot_server.py --server runtime/server-p0 --tag P1-oma-core-boot
```

Dev runs (`runServer`, `runClient`) use `neoforge/run/`; EULA acceptance stays with the owner.

## Implemented (0.1.0)

- Fair-play guard, part 1: in a singleplayer world created without cheats, "Open to LAN → Allow Cheats"
  is revoked on the next server tick, permission levels are re-sent, and the player gets a
  "Grandma's house rules" message in de_de/en_us. The guard logs its state on server start.
- Fair-play guard, part 2: pack-locked ModConfigSpec (TOML) values are reset right after the server configs
  load (before the worlds load) and re-checked every second, with a "house rules" message. First entry:
  `create SERVER kinetics.disableStress = false` (`oma_core/fair_play_locks.txt`). Values are converted to the entry's
  own type (Boolean, Integer, Long, Double, String, enum) and validated against its range; lists are rejected.
- `config/oma_core/fair_play.properties` (plain file, not shown in config GUIs).
- Grandma's house: original cottage blueprint (`oma_core/house/cottage.txt`, parsed and validated in `core/`). Fresh worlds
  get it once near the spawn (bounded flat-site search), world spawn inside, `spawnRadius=0`; existing worlds only via
  `/oma house place` (operator). State in `data/oma_core_world.dat` (versioned SavedData).
- Grandma (ADR-0007, revised): an Easy NPC built from `data/oma_core/easy_npc/preset/grandma/grandma.npc.snbt` (Easy NPC's
  own switches: invulnerable, not attackable by players/monsters, explosion/knockback resistant, not leashable/pushable,
  immovable; placeholder skin via `RESOURCE_LOCATION`; localized name; DE/EN greeting dialog; tag `oma_core_grandma`).
  `GrandmaKeeper` spawns her through `/easy_npc preset import data … <uuid>` and keeps exactly one (join guard, census
  after entity load, import timeout/retry, max 3 automatic restores per 10 min, `/oma repair`, target guard).
  Requires Easy NPC `[7.20.0,8)`; pack-managed Easy NPC configs in `pack-overrides/` (`allowBypassInvulnerability=false`).
- Recipe book + cookie (`cookie/`): right-click on Grandma is handled before Easy NPC. Holding Grandma's cookie → exactly one
  `kubejs:grandmas_cookie` per FTB team (core `CookieLedger` compare-and-set, `OmaWorldData` schema 2, saved before the item
  is taken); vanilla cookie → refusal; otherwise FTB Quests' book as "Grandma's Recipe Book" if the player has none. FTB Teams
  API compile-only (`maven.ftb.dev`, hash = pack jar); FTB Teams/Quests optional dependencies. Operator tools:
  `/oma grandma book <player>`, `/oma cookie handoff <player>`. Grandma's lines follow progress (greeting / working / thanks /
  delivered / vanilla): oma_core opens the Easy NPC dialog by label; preset version 2 is applied in place to existing Grandmas.
- Story progress for FTB Quests (`story/`): invisible advancements `oma_core:story/*` (criterion `minecraft:impossible`) granted by
  oma_core - met_grandma, received_recipe_book, drawer_opened, vanilla_cookie_rejected, left_cottage (16 blocks beyond the
  grounds), cookie_delivered - so story quests are server-verified, not ticked off by hand. The "recipe book given" flag is
  the received_recipe_book advancement (survives death; NeoForge drops persistent data on respawn).
- Grandma's stuck kitchen drawer (`house/KitchenDrawer`, quest C00_Q05, `docs/STORY.md`):
  - The blueprint marker `drawer` holds a Macaw's drawer counter (palette fallbacks `a|b`, barrel if Macaw's is
    missing). The position is stored in `OmaWorldData` schema 3; older houses get it on the next start, placed only
    into air.
  - Every right-click on it is cancelled on the server, so the furniture GUI never opens. Without `kubejs:cabinet_handle`
    the player gets a "stuck" message. With one, the handle is used up, drawer_opened is granted, and the player gets
    the 1987 notice (written book) and the Crumblix factory cookie, once per player. The cookie is refilled if eaten
    before Grandma saw it.
  - `/oma repair` puts a broken drawer back.
- House protection (`house/HouseProtection`): the house's own blocks (non-air blueprint cells at the recorded origin)
  cannot be broken by players outside creative mode (action-bar message); explosions skip them, pistons that would
  move or destroy them do not fire, mobs inside the grounds cannot grief, and house flower pots keep their plant. The
  free space inside stays usable. Not covered: fire, and machines that remove blocks without the break event.
- Rejoin: every login ends in the house (5 ticks after login, cross-dimension); the logout spot is remembered as work
  place unless it was at home; `/oma work return` once per join after a safety check (border, collision, lava).

## Not implemented yet

Locking FTB SNBT and `.properties` configs, the finale/second-cookie teaser, the link to FTB Quests completion, and the
owner's Blockbench model. None of these may be described as working until they pass the tests in `docs/OMA_CORE_SPEC.md`.
