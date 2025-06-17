<div align="center">

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
![Current version)](https://img.shields.io/badge/current_version-1.21.6-blue)

Extractor is a Fabric mod that extracts Minecraft data (blocks, items, entities, etc.) into JSON files 
</div>

### Supported Extractors
- [x] Blocks
- [x] Entities
- [x] Items
- [x] Packets
- [x] World Event
- [x] Multi Noise
- [x] Message Type
- [x] Biomes
- [x] Entity Pose
- [x] Attributes
- [x] Sound Category
- [x] Chunk Status
- [x] Game Event
- [x] Game Rule
- [x] Translation (en_us)
- [x] Noise Parameters
- [x] Particles
- [x] Recipes
- [x] Entity Statuses
- [x] Status Effects
- [x] Screens
- [x] Spawn Eggs
- [x] Sounds
- [x] SyncedRegistries
- [x] Tags
- [x] Tests

### Running
- Gradle >= 8.12

1. Clone the repo
2. run `./gradlew runServer` or alternatively `./gralde runClient` (Join World)
3. See JSON Files in the new folder called `pumpkin_extractor_output`

### Porting 
How to port to a new Minecraft version:
1. Update versions in `gradle.properties` 
2. Attempt to run and fix any errors that come up
