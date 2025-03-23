package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.registry.BuiltinRegistries
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.HeightLimitView
import net.minecraft.world.biome.source.BiomeCoords
import net.minecraft.world.biome.source.MultiNoiseBiomeSource
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists
import net.minecraft.world.biome.source.util.MultiNoiseUtil.MultiNoiseSampler
import net.minecraft.world.biome.source.util.MultiNoiseUtil.NoiseHypercube
import net.minecraft.world.chunk.ChunkStatus
import net.minecraft.world.chunk.ProtoChunk
import net.minecraft.world.chunk.UpgradeData
import net.minecraft.world.gen.WorldPresets
import net.minecraft.world.gen.chunk.*
import net.minecraft.world.gen.densityfunction.DensityFunction.EachApplier
import net.minecraft.world.gen.densityfunction.DensityFunction.NoisePos
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes
import net.minecraft.world.gen.noise.NoiseConfig
import java.lang.reflect.Method

class BiomeDumpTests: Extractor.Extractor {
    override fun fileName(): String = "biome_no_blend_no_beard_0.json"

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

        for (x in 0..0) {
            for (z in 0..0) {
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

                val minBiomeY = BiomeCoords.fromBlock(chunk.bottomY)
                val maxBiomeY = BiomeCoords.fromBlock(chunk.topYInclusive)
                val data = JsonArray()
                for (chunkX in 0..3) {
                    for (chunkZ in 0..3) {
                        for (chunkY in minBiomeY..maxBiomeY) {
                            val chunkData = JsonArray()
                            val biome = chunk.getBiomeForNoiseGen(chunkX, chunkY, chunkZ)
                            val id = server.registryManager.getOrThrow(RegistryKeys.BIOME).getRawId(biome.value())

                            if (chunkX == 0 && chunkZ == 0) {
                                val biomeName = server.registryManager.getOrThrow(RegistryKeys.BIOME).getId(biome.value())
                                println(chunkX.toString() + " " + chunkY.toString() +  " " + chunkZ.toString() + " " + biomeName)
                            }

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

    inner class MultiNoiseBiomeSourceTest: Extractor.Extractor {
        override fun fileName(): String = "multi_noise_biome_source.json"

        override fun extract(server: MinecraftServer): JsonElement {
            val registryManager: DynamicRegistryManager.Immutable = server.registryManager
            val multiNoiseRegistry: Registry<MultiNoiseBiomeSourceParameterList> =
                registryManager.getOrThrow(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)

            val overworldBiomeSource = MultiNoiseBiomeSource.create(multiNoiseRegistry.getOrThrow(
                MultiNoiseBiomeSourceParameterLists.OVERWORLD))

            val seed = 0L
            val chunkPos = ChunkPos(0,0)

            val lookup = BuiltinRegistries.createWrapperLookup()
            val wrapper = lookup.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
            val noiseParams = lookup.getOrThrow(RegistryKeys.NOISE_PARAMETERS)

            val ref = wrapper.getOrThrow(ChunkGeneratorSettings.OVERWORLD)
            val settings = ref.value()
            val config = NoiseConfig.create(settings, noiseParams, seed)

            // Overworld shape config
            val shape = GenerationShapeConfig(-64, 384, 1, 2)
            val testSampler =
                ChunkNoiseSampler(
                    16 / shape.horizontalCellBlockCount(), config, chunkPos.startX, chunkPos.startZ,
                    shape, object : DensityFunctionTypes.Beardifying {
                        override fun maxValue(): Double = 0.0
                        override fun minValue(): Double = 0.0
                        override fun sample(pos: NoisePos): Double = 0.0
                        override fun fill(densities: DoubleArray, applier: EachApplier) {
                            densities.fill(0.0)
                        }
                    }, settings, null, Blender.getNoBlending()
                )

            var createMultiNoiseSampler: Method? = null;
            for (m: Method in testSampler.javaClass.declaredMethods) {
                if (m.name == "createMultiNoiseSampler") {
                    m.trySetAccessible()
                    createMultiNoiseSampler = m
                    break
                }
            }

            val noiseSampler = createMultiNoiseSampler!!.invoke(testSampler, config.noiseRouter, listOf<NoiseHypercube>()) as MultiNoiseSampler

            val topLevelJson = JsonArray()
            for (x in -50..50) {
                for (y in -20..50) {
                    for (z in -50..50) {
                        val biome = overworldBiomeSource.getBiome(x, y, z, noiseSampler)
                        val id = server.registryManager.getOrThrow(RegistryKeys.BIOME).getRawId(biome.value())

                        val datum = JsonArray()
                        datum.add(x)
                        datum.add(y)
                        datum.add(z)
                        datum.add(id)

                        topLevelJson.add(datum)
                    }
                }
            }
            return topLevelJson
        }
    }
}
