package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.registry.BuiltinRegistries
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.biome.Biome
import net.minecraft.world.biome.source.MultiNoiseBiomeSource
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists
import net.minecraft.world.biome.source.util.MultiNoiseUtil.*
import net.minecraft.world.gen.chunk.Blender
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings
import net.minecraft.world.gen.chunk.ChunkNoiseSampler
import net.minecraft.world.gen.chunk.GenerationShapeConfig
import net.minecraft.world.gen.densityfunction.DensityFunction.EachApplier
import net.minecraft.world.gen.densityfunction.DensityFunction.NoisePos
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes
import net.minecraft.world.gen.noise.NoiseConfig
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredFunctions

/**
 * An extractor for MultiNoiseBiomeSourceParameterList that fully serializes NoiseHypercube and ParameterRange data.
 */
class MultiNoise : Extractor.Extractor {

    override fun fileName(): String {
        return "multi_noise_biome_tree.json"
    }

    fun extract_tree_node(node: Any?): JsonObject {
        val json = JsonObject()
        for (f: Field in node!!::class.java.fields) {
            if (f.name == "parameters") {
                f.trySetAccessible()
                val parameters = JsonArray()
                val ranges = f.get(node) as Array<ParameterRange>
                for (range in ranges) {
                    val parameter = JsonObject()
                    parameter.addProperty("min", range.min)
                    parameter.addProperty("max", range.max)
                    parameters.add(parameter)
                }
                json.add("parameters", parameters)
            }
            if (f.name == "subTree") {
                f.trySetAccessible()
                val subTree = JsonArray()
                val nodes = f.get(node) as Array<Any>
                for (childNode in nodes) {
                    subTree.add(extract_tree_node(childNode))
                }
                json.add("subTree", subTree)
                json.addProperty("_type", "branch")
            }
            if (f.name == "value") {
                f.trySetAccessible()
                val value = f.get(node) as RegistryEntry<Biome>
                json.addProperty("biome", value.idAsString)
                json.addProperty("_type", "leaf")
            }
        }
        return json
    }

    fun extract_search_tree(tree: Any?): JsonObject {
        var field: Field? = null
        for (f: Field in tree!!::class.java.declaredFields) {
            if (f.name == "firstNode") {
                f.trySetAccessible()
                field = f
            }
        }

        return extract_tree_node(field!!.get(tree))
    }

    // Only overworld and nether use multi noise sampler for biomes
    override fun extract(server: MinecraftServer): JsonElement {
        val registryManager: DynamicRegistryManager.Immutable = server.registryManager
        val multiNoiseRegistry: Registry<MultiNoiseBiomeSourceParameterList> =
            registryManager.getOrThrow(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)

        val overworldBiomeSource =
            MultiNoiseBiomeSource.create(multiNoiseRegistry.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD))

        var method: Method? = null
        for (m: Method in overworldBiomeSource::class.java.declaredMethods) {
            if (m.name == "getBiomeEntries") {
                m.trySetAccessible()
                method = m
                break
            }
        }

        val overworldEntries = method!!.invoke(overworldBiomeSource) as Entries<RegistryEntry<Biome>>

        var field: Field? = null
        for (f: Field in overworldEntries::class.java.declaredFields) {
            if (f.name == "tree") {
                f.trySetAccessible()
                field = f
            }
        }

        val overworld = extract_search_tree(field!!.get(overworldEntries))

        val netherBiomeSource =
            MultiNoiseBiomeSource.create(multiNoiseRegistry.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER))

        method = null
        for (m: Method in netherBiomeSource::class.java.declaredMethods) {
            if (m.name == "getBiomeEntries") {
                m.trySetAccessible()
                method = m
                break
            }
        }

        val netherEntries = method!!.invoke(netherBiomeSource) as Entries<RegistryEntry<Biome>>

        field = null
        for (f: Field in netherEntries::class.java.declaredFields) {
            if (f.name == "tree") {
                f.trySetAccessible()
                field = f
            }
        }

        val nether = extract_search_tree(field!!.get(netherEntries))

        val returnValue = JsonObject()
        returnValue.add("overworld", overworld)
        returnValue.add("nether", nether)
        return returnValue
    }

    inner class Sample : Extractor.Extractor {
        override fun fileName(): String {
            return "multi_noise_sample_no_blend_no_beard_0_0_0.json"
        }

        override fun extract(server: MinecraftServer): JsonElement {
            val rootJson = JsonArray()

            val seed = 0L
            val chunkPos = ChunkPos(0, 0)

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

            var method: KFunction<*>? = null
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
