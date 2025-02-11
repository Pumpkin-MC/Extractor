package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import de.snowii.extractor.Extractor
import net.minecraft.registry.BuiltinRegistries
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.util.math.Spline
import net.minecraft.util.math.noise.OctavePerlinNoiseSampler
import net.minecraft.util.math.noise.SimplexNoiseSampler
import net.minecraft.world.gen.densityfunction.DensityFunction
import net.minecraft.world.gen.densityfunction.DensityFunction.Noise
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes.RegistryEntryHolder
import net.minecraft.world.gen.noise.NoiseRouter

class DensityFunctions : Extractor.Extractor {
    override fun fileName(): String = "density_function.json"

    private fun serializeSpline(spline: Spline<*, *>): JsonObject {
        val obj = JsonObject()

        when (spline) {
            is Spline.Implementation<*, *> -> {
                obj.add("_type", JsonPrimitive("standard"))

                val value = JsonObject()
                val functionWrapper = spline.locationFunction() as DensityFunctionTypes.Spline.DensityFunctionWrapper
                value.add("locationFunction", serializeFunction(functionWrapper.function.value()))

                val locationArr = JsonArray()
                for (location in spline.locations()) {
                    locationArr.add(JsonPrimitive(location))
                }
                value.add("locations", locationArr)

                val valueArr = JsonArray()
                for (splineValue in spline.values()) {
                    valueArr.add(serializeSpline(splineValue))
                }
                value.add("values", valueArr)

                val derivativeArr = JsonArray()
                for (derivative in spline.derivatives()) {
                    derivativeArr.add(JsonPrimitive(derivative))
                }
                value.add("derivatives", derivativeArr)

                obj.add("value", value)
            }

            is Spline.FixedFloatFunction<*, *> -> {
                obj.add("_type", JsonPrimitive("fixed"))

                val value = JsonObject()
                value.add("value", JsonPrimitive(spline.value()))

                obj.add("value", value)
            }

            else -> throw Exception("Unknown spline: $obj (${obj.javaClass})")
        }

        return obj
    }

    private fun serializeValue(name: String, obj: Any, parent: String): JsonElement {
        return when (obj) {
            is DensityFunction -> serializeFunction(obj)
            is Noise -> JsonPrimitive(obj.noiseData.key.get().value.path)
            is Spline<*, *> -> serializeSpline(obj)
            is Int -> JsonPrimitive(obj)
            is Float -> {
                /*
                if (obj.isNaN()) {
                    throw Exception("Bad float ($name) from $parent")
                }
                 */
                JsonPrimitive(obj)
            }

            is Double -> {
                /*
                if (obj.isNaN()) {
                    throw Exception("Bad double ($name) from $parent")
                }
                 */
                JsonPrimitive(obj)
            }

            is Boolean -> JsonPrimitive(obj)
            is String -> JsonPrimitive(obj)
            is Char -> JsonPrimitive(obj)
            is Enum<*> -> JsonPrimitive(obj.name)
            else -> throw Exception("Unknown value to serialize: $obj ($name) from $parent")
        }
    }

    private fun serializeFunction(function: DensityFunction): JsonObject {
        val obj = JsonObject()

        if (function is RegistryEntryHolder) {
            return serializeFunction(function.function.value())
        }

        obj.add("_class", JsonPrimitive(function.javaClass.simpleName))

        val value = JsonObject()
        for (field in function.javaClass.declaredFields) {
            if (field.name.first().isUpperCase()) {
                // We only want to serialize the used values
                continue
            }

            // These are constant
            if (function.javaClass.simpleName == "BlendDensity") {
                if (field.name == "maxValue") {
                    continue
                }
                if (field.name == "minValue") {
                    continue
                }
            }

            if (function is DensityFunctionTypes.Spline) {
                value.add("minValue", JsonPrimitive(function.minValue()))
                value.add("maxValue", JsonPrimitive(function.maxValue()))
            }

            // These aren't used
            if (field.name.startsWith("field_")) {
                continue
            }

            field.trySetAccessible()
            val fieldValue = field.get(function)
            when (fieldValue) {
                // SimplexNoiseSampler is initialized with a random value during runtime
                is SimplexNoiseSampler -> continue
                // OctavePerlinNoiseSampler is initialized with a random value during runtime
                is OctavePerlinNoiseSampler -> continue
            }

            val serialized = serializeValue(field.name, fieldValue, function.javaClass.simpleName)
            value.add(field.name, serialized)
        }

        if (!value.isEmpty) {
            obj.add("value", value)
        }

        return obj
    }

    private fun serializeRouter(router: NoiseRouter): JsonObject {
        val obj = JsonObject()

        for (field in router.javaClass.declaredFields) {
            if (field.name.first().isUpperCase()) {
                // CODEC is not a value we want
                continue
            }

            field.trySetAccessible()
            val function = field.get(router)
            val serialized = serializeFunction(function as DensityFunction)
            obj.add(field.name, serialized)
        }

        return obj
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val topLevelJson = JsonObject()

        val lookup = BuiltinRegistries.createWrapperLookup()
        val wrapper = lookup.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS)

        wrapper.streamKeys().forEach { key ->
            val entry = wrapper.getOrThrow(key)
            val settings = entry.value()

            val obj = serializeRouter(settings.noiseRouter)
            topLevelJson.add(key.value.path, obj)
        }
        return topLevelJson
    }

    // Dump the building blocks of the density functions to validate proper results
    inner class Tests : Extractor.Extractor {
        override fun fileName(): String = "density_function_tests.json"

        override fun extract(server: MinecraftServer): JsonElement {
            val topLevelJson = JsonObject()

            val functionNames = arrayOf(
                "overworld/base_3d_noise",
                "overworld/caves/entrances",
                "overworld/caves/noodle",
                "overworld/caves/pillars",
                "overworld/caves/spaghetti_2d",
                "overworld/caves/spaghetti_2d_thickness_modulator",
                "overworld/caves/spaghetti_roughness_function",
                "overworld/offset",
                "overworld/depth",
                "overworld/factor",
                "overworld/sloped_cheese"
            )

            val lookup = BuiltinRegistries.createWrapperLookup()
            val functionLookup = lookup.getOrThrow(RegistryKeys.DENSITY_FUNCTION)
            for (functionName in functionNames) {
                val functionKey =
                    RegistryKey.of(
                        RegistryKeys.DENSITY_FUNCTION,
                        Identifier.ofVanilla(functionName)
                    )
                val function = functionLookup.getOrThrow(functionKey).value()
                topLevelJson.add(functionName, serializeFunction(function))
            }

            return topLevelJson
        }
    }
}
