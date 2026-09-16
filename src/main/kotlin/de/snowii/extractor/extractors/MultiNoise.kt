package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Climate
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings
import net.minecraft.world.level.levelgen.RandomState

class MultiNoise : Extractor.Extractor {

    override fun fileName(): String = "multi_noise_biome_tree.json"

    fun extract_tree_node(node: Any?): JsonObject {
        val json = JsonObject()
        if (node == null) return json

        val clazz = node.javaClass

        // "parameterSpace" is declared on the abstract Node base class, so we walk up
        fun findField(c: Class<*>, name: String): java.lang.reflect.Field? {
            var cur: Class<*>? = c
            while (cur != null) {
                try { return cur.getDeclaredField(name).also { it.isAccessible = true } }
                catch (_: NoSuchFieldException) { cur = cur.superclass }
            }
            return null
        }

        // Extract parameterSpace — present on both Leaf and SubTree via Node base class
        val parameters = JsonArray()
        findField(clazz, "parameterSpace")?.let { f ->
            try {
                @Suppress("UNCHECKED_CAST")
                val ranges = f.get(node) as Array<Climate.Parameter>
                for (range in ranges) {
                    val parameter = JsonObject()
                    // min/max are quantized longs — convert back to float with unquantizeCoord
                    parameter.addProperty("min", range.min())
                    parameter.addProperty("max", range.max())
                    parameters.add(parameter)
                }
            } catch (e: Exception) { /* leave empty */ }
        }
        json.add("parameters", parameters)

        // Detect leaf vs branch by presence of "value" field
        val valueField = findField(clazz, "value")
        if (valueField != null) {
            // ---- LEAF NODE ----
            json.addProperty("_type", "leaf")
            try {
                @Suppress("UNCHECKED_CAST")
                val holder = valueField.get(node) as Holder<Biome>
                val key = holder.unwrapKey().orElse(null)
                json.addProperty("biome", key?.identifier()?.toString() ?: "unknown")
            } catch (e: Exception) {
                json.addProperty("biome", "unknown")
            }
        } else {
            // ---- BRANCH NODE (SubTree) ----
            json.addProperty("_type", "branch")
            val subTree = JsonArray()
            findField(clazz, "children")?.let { f ->
                try {
                    val nodes = f.get(node) as Array<*>
                    for (child in nodes) {
                        subTree.add(extract_tree_node(child))
                    }
                } catch (e: Exception) { /* leave empty */ }
            }
            json.add("subTree", subTree)
        }

        return json
    }

    fun extract_search_tree(parameterList: Climate.ParameterList<Holder<Biome>>): JsonObject {
        // Force RTree construction via a dummy lookup
        parameterList.findValue(Climate.target(0f, 0f, 0f, 0f, 0f, 0f))

        // Climate.ParameterList has field "index" of type RTree (see vanilla source)
        val indexField = parameterList.javaClass.getDeclaredField("index")
            .also { it.isAccessible = true }
        val tree = indexField.get(parameterList)

        // RTree has field "root" of type Node
        val rootField = tree.javaClass.getDeclaredField("root")
            .also { it.isAccessible = true }
        val root = rootField.get(tree)

        return extract_tree_node(root)
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val registryAccess = server.registryAccess()
        val multiNoiseRegistry = registryAccess.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
        val result = JsonObject()

        val overworld = multiNoiseRegistry.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD).value()
        result.add("overworld", extract_search_tree(overworld.parameters()))

        val nether = multiNoiseRegistry.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER).value()
        result.add("nether", extract_search_tree(nether.parameters()))

        return result
    }

    inner class Sample : Extractor.Extractor {
        override fun fileName(): String = "multi_noise_sample_no_blend_no_beard_0_0_0.json"

        override fun extract(server: MinecraftServer): JsonElement {
            val rootJson = JsonArray()
            val seed = 0L

            val registryAccess = server.registryAccess()
            val noiseSettings = registryAccess.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD)
            val noiseParams = registryAccess.lookupOrThrow(Registries.NOISE)

            val randomState = RandomState.create(noiseParams, seed, noiseSettings.value())
            val sampler = randomState.createClimateSampler(
                net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED
            )

            for (x in 0..15) {
                for (y in -64..319 step 4) {
                    for (z in 0..15) {
                        val res = sampler.sample(x, y, z)
                        val arr = JsonArray()
                        arr.add(x); arr.add(y); arr.add(z)
                        arr.add(res.temperature())
                        arr.add(res.humidity())
                        arr.add(res.continentalness())
                        arr.add(res.erosion())
                        arr.add(res.depth())
                        arr.add(res.weirdness())
                        rootJson.add(arr)
                    }
                }
            }
            return rootJson
        }
    }
}