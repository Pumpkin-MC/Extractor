package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import de.snowii.extractor.Extractor
import it.unimi.dsi.fastutil.doubles.DoubleList
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.registry.BuiltinRegistries
import net.minecraft.registry.RegistryKeys

import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Spline
import net.minecraft.util.math.noise.OctavePerlinNoiseSampler
import net.minecraft.util.math.noise.PerlinNoiseSampler
import net.minecraft.util.math.noise.SimplexNoiseSampler
import net.minecraft.world.gen.chunk.*
import net.minecraft.world.gen.densityfunction.DensityFunction
import net.minecraft.world.gen.densityfunction.DensityFunction.EachApplier
import net.minecraft.world.gen.densityfunction.DensityFunction.Noise
import net.minecraft.world.gen.densityfunction.DensityFunction.NoisePos
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes.RegistryEntryHolder
import net.minecraft.world.gen.noise.NoiseConfig
import net.minecraft.world.gen.noise.NoiseRouter
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredFunctions

class DensityFunctions : Extractor.Extractor {
    override fun fileName(): String = "density_function.json"

    private fun serializeSpline(spline: Spline<*,*>): JsonObject {
        val obj = JsonObject()

        when(spline) {
            is Spline.Implementation<*,*> -> {
                obj.add("_type", JsonPrimitive("density"))
                val functionWrapper = spline.locationFunction() as DensityFunctionTypes.Spline.DensityFunctionWrapper
                obj.add("locationFunction", serializeFunction(functionWrapper.function.value()))

                val locationArr = JsonArray()
                for (location in spline.locations()) {
                    locationArr.add(JsonPrimitive(location))
                }
                obj.add("locations", locationArr)

                val valueArr = JsonArray()
                for (value in spline.values()) {
                    valueArr.add(serializeSpline(value))
                }
                obj.add("values", valueArr)

                val derivativeArr = JsonArray()
                for (derivative in spline.derivatives()) {
                    derivativeArr.add(JsonPrimitive(derivative))
                }
                obj.add("derivatives", derivativeArr)
            }
            is Spline.FixedFloatFunction<*,*> -> {
                obj.add("_type", JsonPrimitive("fixed"))
                obj.add("value", JsonPrimitive(spline.value()))
            }
            else -> throw Exception("Unknown spline: $obj (${obj.javaClass})")
        }

        return obj
    }

    private fun serializePerlinNoise(sampler: PerlinNoiseSampler): JsonObject {
        val obj = JsonObject()

        val permutationField = sampler.javaClass.declaredFields.first { ele -> ele.name == "permutation" }
        permutationField.trySetAccessible()
        val permutation = permutationField.get(sampler) as ByteArray
        val permutationArr = JsonArray()
        for (byte in permutation) {
            permutationArr.add(byte)
        }

        obj.add("permutation", permutationArr)
        obj.add("originX", JsonPrimitive(sampler.originX))
        obj.add("originY", JsonPrimitive(sampler.originY))
        obj.add("originZ", JsonPrimitive(sampler.originZ))

        return obj
    }

    private fun serializeOctavePerlinNoise(sampler: OctavePerlinNoiseSampler): JsonObject {
        val obj = JsonObject()

        val octaveSamplersField = sampler.javaClass.declaredFields.first { ele -> ele.name == "octaveSamplers" }
        octaveSamplersField.trySetAccessible()
        val octaveSamplers = octaveSamplersField.get(sampler) as Array<*>
        val octaveSamplerArray = JsonArray()
        for (octaveSampler in octaveSamplers) {
            octaveSamplerArray.add(serializePerlinNoise(octaveSampler as PerlinNoiseSampler))
        }
        obj.add("octaveSamplers", octaveSamplerArray)

        val firstOctaveField = sampler.javaClass.declaredFields.first { ele -> ele.name == "firstOctave" }
        firstOctaveField.trySetAccessible()
        val firstOctave = firstOctaveField.get(sampler) as Int
        obj.add("firstOctave", JsonPrimitive(firstOctave))

        val amplitudesField = sampler.javaClass.declaredFields.first { ele -> ele.name == "amplitudes" }
        amplitudesField.trySetAccessible()
        val amplitudes = amplitudesField.get(sampler) as DoubleList
        val amplitudesArray = JsonArray()
        for (amplitude in amplitudes.stream()) {
            amplitudesArray.add(amplitude)
        }
        obj.add("amplitudes", amplitudesArray)

        val persistenceField = sampler.javaClass.declaredFields.first { ele -> ele.name == "persistence" }
        persistenceField.trySetAccessible()
        val persistence = persistenceField.get(sampler) as Double
        obj.add("persistence", JsonPrimitive(persistence))

        val lacunarityField = sampler.javaClass.declaredFields.first { ele -> ele.name == "lacunarity" }
        lacunarityField.trySetAccessible()
        val lacunarity = lacunarityField.get(sampler) as Double
        obj.add("lacunarity", JsonPrimitive(lacunarity))

        val maxValueField = sampler.javaClass.declaredFields.first { ele -> ele.name == "maxValue" }
        maxValueField.trySetAccessible()
        val maxValue = maxValueField.get(sampler) as Double
        obj.add("maxValue", JsonPrimitive(maxValue))

        return obj
    }

    private fun serializeValue(name: String, obj: Any): JsonElement {
        return when(obj) {
            is Int -> JsonPrimitive(obj)
            is Float -> JsonPrimitive(obj)
            is Double -> JsonPrimitive(obj)
            is Boolean -> JsonPrimitive(obj)
            is String -> JsonPrimitive(obj)
            is Char -> JsonPrimitive(obj)
            is Enum<*> -> JsonPrimitive(obj.name)
            is Noise -> JsonPrimitive(obj.noiseData.key.get().value.path)
            is Spline<*,*> -> serializeSpline(obj)
            is OctavePerlinNoiseSampler -> serializeOctavePerlinNoise(obj)
            is DensityFunction -> serializeFunction(obj)
            else -> throw Exception("Unknown value to serialize: $obj ($name)")
        }
    }

    private fun serializeFunction(function: DensityFunction): JsonObject {
        val obj = JsonObject()

        if (function is RegistryEntryHolder) {
            return serializeFunction(function.function.value())
        }

        obj.add("_class", JsonPrimitive(function.javaClass.simpleName))

        for (field in function.javaClass.declaredFields) {
            if (field.name.first().isUpperCase()) {
                // We only want to serialize the used values
                continue
            }

            field.trySetAccessible()
            val fieldValue = field.get(function)
            when (fieldValue) {
                // SimplexNoiseSampler is initialized with a random value during runtime
                is SimplexNoiseSampler -> continue
            }

            val serialized = serializeValue(field.name, fieldValue)
            obj.add(field.name, serialized)
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
}
