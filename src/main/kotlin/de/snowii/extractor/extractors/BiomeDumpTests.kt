package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.core.QuartPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.LevelHeightAccessor
import net.minecraft.world.level.biome.Climate
import net.minecraft.world.level.biome.MultiNoiseBiomeSource
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists
import net.minecraft.world.level.chunk.ProtoChunk
import net.minecraft.world.level.chunk.UpgradeData
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.levelgen.*
import net.minecraft.world.level.levelgen.blending.Blender
import java.lang.reflect.Constructor

class BiomeDumpTests : Extractor.Extractor {
    override fun fileName(): String = "biome_no_blend_no_beard_0.json"

    override fun isTest(): Boolean = true

    companion object {
        private fun createFluidLevelSampler(settings: NoiseGeneratorSettings): Aquifer.FluidPicker {
            val fluidLevel = Aquifer.FluidStatus(-54, net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState())
            val i = settings.seaLevel()
            val fluidLevel2 = Aquifer.FluidStatus(i, settings.defaultFluid())
            return Aquifer.FluidPicker { _, y, _ -> if (y < Math.min(-54, i)) fluidLevel else fluidLevel2 }
        }

        fun createMultiNoiseSampler(config: RandomState, sampler: NoiseChunk): Climate.Sampler {
            return sampler.cachedClimateSampler(config.router(), listOf())
        }

        private fun createProtoChunk(
            chunkPos: ChunkPos,
            upgradeData: UpgradeData,
            levelHeightAccessor: LevelHeightAccessor,
            server: MinecraftServer
        ): ProtoChunk {
            val overworldLevel = server.overworld()
            var ctor5: Constructor<*>? = null
            for (ctor in ProtoChunk::class.java.declaredConstructors) {
                if (ctor.parameterCount == 5 && ctor.parameterTypes[0] == ChunkPos::class.java && ctor.parameterTypes[1] == UpgradeData::class.java && ctor.parameterTypes[2] == LevelHeightAccessor::class.java) {
                    ctor.isAccessible = true
                    ctor5 = ctor
                    break
                }
            }
            if (ctor5 == null) {
                throw IllegalStateException("ProtoChunk 5-arg constructor not found")
            }

            val arg4Type = ctor5.parameterTypes[3]
            var arg4Value: Any? = null

            // Walk up hierarchy of overworldLevel if needed
            var cls: Class<*>? = overworldLevel.javaClass
            while (cls != null && arg4Value == null) {
                for (field in cls.declaredFields) {
                    if (arg4Type.isAssignableFrom(field.type)) {
                        field.isAccessible = true
                        arg4Value = field.get(overworldLevel)
                        if (arg4Value != null) break
                    }
                }
                if (arg4Value != null) break
                for (method in cls.declaredMethods) {
                    if (method.parameterCount == 0 && arg4Type.isAssignableFrom(method.returnType)) {
                        method.isAccessible = true
                        arg4Value = method.invoke(overworldLevel)
                        if (arg4Value != null) break
                    }
                }
                cls = cls.superclass
            }

            if (arg4Value == null) {
                throw IllegalStateException("Could not extract ${arg4Type.name} from ServerLevel")
            }

            return ctor5.newInstance(chunkPos, upgradeData, levelHeightAccessor, arg4Value, null) as ProtoChunk
        }
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val topLevelJson = JsonArray()
        val seed = 0L

        val biomeRegistry = server.registryAccess().lookupOrThrow(Registries.BIOME)

        val ref = server.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS)
            .getOrThrow(NoiseGeneratorSettings.OVERWORLD)
        val settings = ref.value()

        val noiseParams = server.registryAccess().lookupOrThrow(Registries.NOISE)
        val config = RandomState.create(settings, noiseParams, seed)

        val chunkGenerator = server.overworld().chunkSource.generator
        val biomeSource = chunkGenerator.biomeSource

        val levelHeightAccessor = LevelHeightAccessor.create(
            chunkGenerator.minY,
            chunkGenerator.genDepth
        )

        for (x in 5..5) {
            for (z in 5..5) {
                val biomeData = JsonObject()
                biomeData.addProperty("x", x)
                biomeData.addProperty("z", z)

                val chunkPos = ChunkPos(x, z)
                val chunk = createProtoChunk(
                    chunkPos,
                    UpgradeData.EMPTY,
                    levelHeightAccessor,
                    server
                )

                val testSampler =
                    NoiseChunk.forChunk(
                        chunk, config, object : DensityFunctions.BeardifierOrMarker {
                            override fun maxValue(): Double = 0.0
                            override fun minValue(): Double = 0.0
                            override fun compute(pos: DensityFunction.FunctionContext): Double = 0.0
                            override fun fillArray(densities: DoubleArray, contextProvider: DensityFunction.ContextProvider) {
                                densities.fill(0.0)
                            }
                        }, settings, createFluidLevelSampler(settings), Blender.empty()
                    )
                val testNoiseSampler = createMultiNoiseSampler(config, testSampler)

                // We don't have retro gen and we don't want structures
                chunk.fillBiomesFromNoise(biomeSource, testNoiseSampler)
                chunk.persistedStatus = ChunkStatus.BIOMES

                val minBiomeY = QuartPos.fromBlock(chunk.minY)
                val maxBiomeY = QuartPos.fromBlock(chunk.maxY)

                val data = JsonArray()
                for (biomeX in 0..3) {
                    for (biomeZ in 0..3) {
                        for (biomeY in minBiomeY..maxBiomeY) {
                            val chunkData = JsonArray()

                            val biome = chunk.getNoiseBiome(biomeX, biomeY, biomeZ)
                            val id = biomeRegistry.getId(biome.value())

                            chunkData.add(biomeX)
                            chunkData.add(biomeY)
                            chunkData.add(biomeZ)
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

    inner class MultiNoiseBiomeSourceTest : Extractor.Extractor {
        override fun fileName(): String = "multi_noise_biome_source_test.json"

        override fun isTest(): Boolean = true

        override fun extract(server: MinecraftServer): JsonElement {
            val registryAccess = server.registryAccess()
            val multiNoiseRegistry = registryAccess.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)

            val overworldBiomeSource = MultiNoiseBiomeSource.createFromPreset(
                multiNoiseRegistry.getOrThrow(
                    MultiNoiseBiomeSourceParameterLists.OVERWORLD
                )
            )

            val seed = 0L
            val chunkPos = ChunkPos(0, 0)

            val ref = registryAccess.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD)
            val settings = ref.value()

            val noiseParams = registryAccess.lookupOrThrow(Registries.NOISE)
            val config = RandomState.create(settings, noiseParams, seed)

            val chunkGenerator = server.overworld().chunkSource.generator
            val levelHeightAccessor = LevelHeightAccessor.create(
                chunkGenerator.minY,
                chunkGenerator.genDepth
            )

            val chunk = createProtoChunk(
                chunkPos,
                UpgradeData.EMPTY,
                levelHeightAccessor,
                server
            )

            val testSampler =
                NoiseChunk.forChunk(
                    chunk, config, object : DensityFunctions.BeardifierOrMarker {
                        override fun maxValue(): Double = 0.0
                        override fun minValue(): Double = 0.0
                        override fun compute(pos: DensityFunction.FunctionContext): Double = 0.0
                        override fun fillArray(densities: DoubleArray, contextProvider: DensityFunction.ContextProvider) {
                            densities.fill(0.0)
                        }
                    }, settings, createFluidLevelSampler(settings), Blender.empty()
                )

            val noiseSampler = createMultiNoiseSampler(config, testSampler)

            val topLevelJson = JsonArray()
            for (x in -50..50) {
                for (y in -20..50) {
                    for (z in -50..50) {
                        val biome = overworldBiomeSource.getNoiseBiome(x, y, z, noiseSampler)
                        val id = registryAccess.lookupOrThrow(Registries.BIOME).getId(biome.value())

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
