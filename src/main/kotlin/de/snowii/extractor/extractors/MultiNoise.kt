package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.registry.*
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.biome.Biome
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList.Preset
import net.minecraft.world.biome.source.util.MultiNoiseUtil
import net.minecraft.world.biome.source.util.MultiNoiseUtil.MultiNoiseSampler
import net.minecraft.world.biome.source.util.MultiNoiseUtil.NoiseHypercube
import net.minecraft.world.gen.chunk.*
import net.minecraft.world.gen.densityfunction.DensityFunction.EachApplier
import net.minecraft.world.gen.densityfunction.DensityFunction.NoisePos
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes
import net.minecraft.world.gen.noise.NoiseConfig
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredFunctions

/**
 * An extractor for MultiNoiseBiomeSourceParameterList that fully serializes NoiseHypercube and ParameterRange data.
 */
class MultiNoise : Extractor.Extractor {

    override fun fileName(): String {
        return "multi_noise.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val registryManager: DynamicRegistryManager.Immutable = server.registryManager
        val multiNoiseRegistry: Registry<MultiNoiseBiomeSourceParameterList> =
            registryManager.getOrThrow(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)

        val BIOME_KEY_CODEC = RegistryKey.createCodec(RegistryKeys.BIOME).fieldOf("biome")
        val BIOME_ENTRY_CODEC: Codec<MultiNoiseUtil.Entries<RegistryKey<Biome>>> =
            (MultiNoiseUtil.Entries.createCodec<RegistryKey<Biome>>(BIOME_KEY_CODEC)
                .fieldOf("biomes")).codec()

        val rootJson = JsonObject()
        MultiNoiseBiomeSourceParameterList.getPresetToEntriesMap()
            .forEach { (preset: Preset, entries: MultiNoiseUtil.Entries<RegistryKey<Biome>>) ->
                rootJson.add(
                    preset.id.path, BIOME_ENTRY_CODEC.encodeStart(
                        RegistryOps.of(JsonOps.INSTANCE, server.registryManager),
                        entries
                    ).getOrThrow()
                )
            }


        return rootJson
    }

    inner class Sample: Extractor.Extractor {
        override fun fileName(): String {
            return "multi_noise_sample_no_blend_no_beard_0_0_0.json"
        }

        override fun extract(server: MinecraftServer): JsonElement {
            val rootJson = JsonArray()

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

            var method: KFunction<*>? = null;
            for (m: KFunction<*> in testSampler::class.declaredFunctions) {
                if (m.name == "createMultiNoiseSampler") {
                    method = m
                    break
                }
            }
            val sampler = method!!.call(testSampler, config.noiseRouter, listOf<NoiseHypercube>()) as MultiNoiseSampler
            for (x in 0..15) {
                for (y in -64..319) {
                    for (z in 0..15) {
                        val result = sampler.sample(x, y, z)

                        val valueArr = JsonArray()
                        valueArr.add(x)
                        valueArr.add(y)
                        valueArr.add(z)

                        valueArr.add(result.temperatureNoise)
                        valueArr.add(result.humidityNoise)
                        valueArr.add(result.continentalnessNoise)
                        valueArr.add(result.erosionNoise)
                        valueArr.add(result.depth)
                        valueArr.add(result.weirdnessNoise)

                        rootJson.add(valueArr)
                    }
                }
            }

            return rootJson
        }
    }
}
