<div align="center">

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
![Current version)](https://img.shields.io/badge/current_version-26.3-blue)

Extractor is a Fabric mod that extracts Minecraft data (blocks, items, entities, etc.) into JSON files 
</div>

### Supported Extractors
- [x] Blocks
- [x] Entities
- [x] Items
- [x] Packets
- [x] World Event
- [x] Multi Noise
- [x] Entity Pose
- [x] Attributes
- [x] Sound Category
- [x] Chunk Status
- [x] Game Event
- [x] Game Rules
- [x] Translation (en_us)
- [x] Particles
- [x] Entity Statuses
- [x] Status Effects
- [x] Screens
- [x] Sounds
- [x] Tests
- [x] Custom Stats
- [x] Slot Ranges
- [x] Fluids
- [x] Data Components
- [x] Properties
- [x] Flower Pot Transformations
- [x] Villager Data
- [x] Stats
- [x] Map Colors
- [x] Map Decorations
- [x] Dye Colors
- [x] Potion
- [x] Tracked Data
- [x] Metadata Types
- [x] Scoreboard Display Slot

### Running

1. Clone the repo
2. run `./gradlew runServer` or alternatively `./gradle runClient` (Join World)
3. See JSON Files in the new folder called `pumpkin_extractor_output`

### Porting 
How to port to a new Minecraft version:
1. Update versions in `gradle.properties` 
2. Attempt to run and fix any errors that come up
