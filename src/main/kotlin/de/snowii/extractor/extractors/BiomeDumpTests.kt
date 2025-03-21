package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.registry.BuiltinRegistries
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.HeightLimitView
import net.minecraft.world.chunk.ChunkStatus
import net.minecraft.world.chunk.ProtoChunk
import net.minecraft.world.chunk.UpgradeData
import net.minecraft.world.gen.WorldPresets
import net.minecraft.world.gen.chunk.*
import net.minecraft.world.gen.noise.NoiseConfig

class BiomeDumpTests: Extractor.Extractor {
    override fun fileName(): String = "biome_no_blend_no_beard_0.json"

    // Dumps a chunk to an array of block state ids
    override fun extract(server: MinecraftServer): JsonElement {
        val topLevelJson = JsonArray()
        val seed = 0L

        val lookup = BuiltinRegistries.createWrapperLookup()
        val wrapper = lookup.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
        val noiseParams = lookup.getOrThrow(RegistryKeys.NOISE_PARAMETERS)

        val ref = wrapper.getOrThrow(ChunkGeneratorSettings.OVERWORLD)
        val settings = ref.value()
        val config = NoiseConfig.create(settings, noiseParams, seed)


        val options = WorldPresets.getDefaultOverworldOptions(lookup)

        for (x in -5..5) {
            for (z in -5 .. 5) {
                val biomeData = JsonObject()
                biomeData.addProperty("x", x)
                biomeData.addProperty("z", z)

                val chunkPos = ChunkPos(x, z)
                val chunk = ProtoChunk(
                    chunkPos, UpgradeData.NO_UPGRADE_DATA,
                    HeightLimitView.create(options.chunkGenerator.minimumY, options.chunkGenerator.worldHeight),
                    server.registryManager.getOrThrow(RegistryKeys.BIOME), null
                )
                options.chunkGenerator.populateBiomes(config, Blender.getNoBlending(), null, chunk)
                chunk.status = ChunkStatus.BIOMES

                val minChunkY = chunk.bottomY.shr(2)
                val maxChunkY = chunk.topYInclusive.shr(2)
                val data = JsonArray()
                for (chunkX in 0..3) {
                    for (chunkZ in 0..3) {
                        for (chunkY in minChunkY..maxChunkY) {
                            val chunkData = JsonArray()
                            val biome = chunk.getBiomeForNoiseGen(chunkX, chunkY, chunkZ)
                            val id = server.registryManager.getOrThrow(RegistryKeys.BIOME).getRawId(biome.value())

                            chunkData.add(chunkX)
                            chunkData.add(chunkY)
                            chunkData.add(chunkZ)
                            chunkData.add(id)

                            data.add(chunkData)
                        }
                    }
                }

                biomeData.add("data", data)
                topLevelJson.add(biomeData)
            }
        }

        return topLevelJson
    }
}
