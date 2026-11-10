package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.BiomeResolver
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings
import net.minecraft.world.level.levelgen.RandomState
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.Random

class BiomeHashes : Extractor.Extractor {
    private val logger = LoggerFactory.getLogger("biome-hashes")

    companion object {
        const val SEED: Long = 13579
        const val CHUNK_SAMPLE_SEED: Long = 123456
        const val NUM_SAMPLE_CHUNKS: Int = 2500

        fun sampledChunkPositions(): List<ChunkPos> {
            val rng = Random(CHUNK_SAMPLE_SEED)
            val result = mutableListOf<ChunkPos>()
            for (i in 0 until NUM_SAMPLE_CHUNKS) {
                val x = rng.nextInt(1_000_000) - 500_000
                val z = rng.nextInt(1_000_000) - 500_000
                result.add(ChunkPos(x, z))
            }
            return result
        }
    }

    override fun fileName(): String = "biome_hashes.json"
    override fun isTest(): Boolean = true

    private data class BiomeKey(val sectionY: Int, val x: Int, val y: Int, val z: Int)

    override fun extract(server: MinecraftServer): JsonElement {
        val json = JsonObject()
        json.addProperty("seed", SEED)
        json.addProperty("chunk_sample_seed", CHUNK_SAMPLE_SEED)
        json.addProperty("num_chunks", NUM_SAMPLE_CHUNKS)

        val noiseRegistry = server.registryAccess().lookupOrThrow(Registries.NOISE)
        val noiseSettingsRegistry = server.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS)

        val dimensions = listOf(
            Triple("overworld", Level.OVERWORLD, NoiseGeneratorSettings.OVERWORLD),
            Triple("the_nether", Level.NETHER, NoiseGeneratorSettings.NETHER),
            Triple("the_end", Level.END, NoiseGeneratorSettings.END)
        )

        val sampledPositions = sampledChunkPositions()

        for ((name, levelKey, noiseSettingsKey) in dimensions) {
            val level = server.getLevel(levelKey) ?: continue
            val chunkGenerator = level.chunkSource.generator
            val biomeSource = chunkGenerator.biomeSource
            val settings = noiseSettingsRegistry.getOrThrow(noiseSettingsKey).value()
            val randomState = RandomState.create(noiseRegistry, SEED, settings)
            val resolver = biomeSource.createUncachedResolver(randomState)

            val dimJson = JsonObject()
            val minSectionY = level.minSectionY
            val maxSectionY = level.maxSectionY
            dimJson.addProperty("min_section_y", minSectionY)
            dimJson.addProperty("max_section_y", maxSectionY)

            val hashesArray = JsonArray()
            for (pos in sampledPositions) {
                val hash = chunkBiomeHash(resolver, pos.x, pos.z, minSectionY, maxSectionY)
                val entry = JsonArray()
                entry.add(pos.x)
                entry.add(pos.z)
                entry.add(hash)
                hashesArray.add(entry)
            }
            dimJson.add("hashes", hashesArray)
            json.add(name, dimJson)
            logger.info("Extracted 26.3 biome hashes for $name: ${hashesArray.size()} chunks")
        }

        return json
    }

    private fun chunkBiomeHash(
        resolver: BiomeResolver,
        chunkX: Int,
        chunkZ: Int,
        minSectionY: Int,
        maxSectionY: Int
    ): String {
        val biomes = HashMap<BiomeKey, String>()

        for (sectionY in minSectionY..maxSectionY) {
            for (x in 0 until 4) {
                for (y in 0 until 4) {
                    for (z in 0 until 4) {
                        val quartX = chunkX * 4 + x
                        val quartY = sectionY * 4 + y
                        val quartZ = chunkZ * 4 + z

                        val biome = resolver.getNoiseBiome(quartX, quartY, quartZ)
                        val opt = biome.unwrapKey()
                        val biomeName = if (opt.isPresent) opt.get().identifier().toString() else "unknown"

                        biomes[BiomeKey(sectionY, x, y, z)] = biomeName
                    }
                }
            }
        }

        val md = MessageDigest.getInstance("MD5")

        for (sectionY in minSectionY..maxSectionY) {
            md.update(sectionY.toByte())
            for (y in 0 until 4) {
                for (z in 0 until 4) {
                    for (x in 0 until 4) {
                        val biome = biomes[BiomeKey(sectionY, x, y, z)]!!
                        val name = if (biome.startsWith("minecraft:")) {
                            biome.substring("minecraft:".length)
                        } else {
                            biome
                        }
                        md.update(name.toByteArray(Charsets.UTF_8))
                    }
                }
            }
        }

        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
