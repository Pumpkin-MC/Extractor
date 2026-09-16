package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import de.snowii.extractor.Extractor
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.util.CubicSpline
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions as McDensityFunctions
import net.minecraft.world.level.levelgen.densityfunction.generator.ConstantFunction
import net.minecraft.world.level.levelgen.densityfunction.generator.EndIslandFunction
import net.minecraft.world.level.levelgen.densityfunction.generator.GradientFunction
import net.minecraft.world.level.levelgen.densityfunction.generator.NoiseFunction
import net.minecraft.world.level.levelgen.densityfunction.generator.ShiftNoiseFunction
import net.minecraft.world.level.levelgen.densityfunction.generator.SimpleDensityFunction
import net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction
import net.minecraft.world.level.levelgen.densityfunction.op.BlendDensityFunction
import net.minecraft.world.level.levelgen.densityfunction.op.CacheFunction
import net.minecraft.world.level.levelgen.densityfunction.op.ClampFunction
import net.minecraft.world.level.levelgen.densityfunction.op.InterpolatedFunction
import net.minecraft.world.level.levelgen.densityfunction.op.LerpFunction
import net.minecraft.world.level.levelgen.densityfunction.op.RangeChoiceFunction
import net.minecraft.world.level.levelgen.densityfunction.op.SplineFunction
import net.minecraft.world.level.levelgen.densityfunction.op.UnaryFunction

class DensityFunctions {

    private fun extractLocationFunction(spline: CubicSpline.Multipoint<*>): JsonElement {
        val allFields = buildList {
            var cls: Class<*>? = spline.javaClass
            while (cls != null) {
                addAll(cls.declaredFields)
                cls = cls.superclass
            }
        }

        for (field in allFields) {
            if (field.name.first().isUpperCase()) continue
            val type = field.type
            if (type.isPrimitive || type.isArray) continue
            if (type == String::class.java) continue

            field.isAccessible = true
            val v = try { field.get(spline) } catch (_: Exception) { continue } ?: continue

            if (v is DensityFunction) {
                return serializeFunction(v)
            }

            if (v is SplineFunction.Coordinate) {
                return serializeFunction(v.function())
            }

            try {
                val functionMethod = v.javaClass.getMethod("function")
                val result = functionMethod.invoke(v)
                if (result is DensityFunction) return serializeFunction(result)
                if (result is Holder<*>) {
                    val inner = result.value()
                    if (inner is DensityFunction) return serializeFunction(inner)
                }
            } catch (_: Exception) { /* method doesn't exist on this type */ }
        }

        throw IllegalStateException(
            "Could not extract locationFunction from ${spline.javaClass.name}. " +
                    "Fields: ${spline.javaClass.declaredFields.map { it.name + ":" + it.type.simpleName }}"
        )
    }

    private fun serializeSpline(spline: CubicSpline<*>): JsonObject {
        val obj = JsonObject()

        when (spline) {
            is CubicSpline.Multipoint<*> -> {
                obj.add("_type", JsonPrimitive("standard"))

                val value = JsonObject()
                value.add("locationFunction", extractLocationFunction(spline))

                val locationArr = JsonArray()
                spline.locations().forEach { locationArr.add(it) }
                value.add("locations", locationArr)

                val valueArr = JsonArray()
                spline.values().forEach { valueArr.add(serializeSpline(it)) }
                value.add("values", valueArr)

                val derivativeArr = JsonArray()
                spline.derivatives().forEach { derivativeArr.add(it) }
                value.add("derivatives", derivativeArr)

                obj.add("value", value)
            }

            is CubicSpline.Constant<*> -> {
                obj.add("_type", JsonPrimitive("fixed"))
                val value = JsonObject()
                value.add("value", JsonPrimitive(spline.value()))
                obj.add("value", value)
            }
        }

        return obj
    }

    private fun noiseHolderPath(holder: Holder<*>): String {
        val key = holder.unwrapKey().orElse(null)
        return key?.identifier()?.path ?: "inline"
    }

    private fun serializeFunction(function: DensityFunction): JsonObject {
        if (function is McDensityFunctions.HolderHolder) {
            return serializeFunction(function.function().value())
        }

        val obj = JsonObject()
        val simpleName = function.javaClass.simpleName

        // ── Marker / Wrapping ──────────────────────────────────────────────────
        if (function is BlendDensityFunction) {
            // BlendDensity was demoted from its own class to a Marker type in newer versions.
            // We intercept it here to maintain the legacy JSON structure.
            obj.add("_class", JsonPrimitive("BlendDensity"))
            val value = JsonObject()
            value.add("input", serializeFunction(function.input()))
            obj.add("value", value)
            return obj
        }
        if (function is InterpolatedFunction) {
            obj.add("_class", JsonPrimitive("Wrapping"))
            val value = JsonObject()
            value.add("type", JsonPrimitive("Interpolated"))
            value.add("wrapped", serializeFunction(function.input()))
            obj.add("value", value)
            return obj
        }
        if (function is CacheFunction) {
            obj.add("_class", JsonPrimitive("Wrapping"))
            val value = JsonObject()
            value.add("type", JsonPrimitive("CacheOnce"))
            value.add("wrapped", serializeFunction(function.input()))
            obj.add("value", value)
            return obj
        }

        // ── Spline ────────────────────────────────────────────────────────────
        if (function is SplineFunction) {
            obj.add("_class", JsonPrimitive("Spline"))
            val value = JsonObject()
            value.add("minValue", JsonPrimitive(function.range().min()))
            value.add("maxValue", JsonPrimitive(function.range().max()))
            value.add("spline", serializeSpline(function.spline()))
            obj.add("value", value)
            return obj
        }

        // ── Constant ──────────────────────────────────────────────────────────
        if (function is ConstantFunction) {
            obj.add("_class", JsonPrimitive("Constant"))
            val value = JsonObject()
            value.add("value", JsonPrimitive(function.value()))
            obj.add("value", value)
            return obj
        }

        // ── Stateless singletons ──────────────────────────────────────────────
        if (function === SimpleDensityFunction.BLEND_ALPHA) {
            obj.add("_class", JsonPrimitive("BlendAlpha"))
            return obj
        }
        if (function === SimpleDensityFunction.BLEND_OFFSET) {
            obj.add("_class", JsonPrimitive("BlendOffset"))
            return obj
        }
        if (function === SimpleDensityFunction.BEARDIFIER) {
            obj.add("_class", JsonPrimitive("Beardifier"))
            return obj
        }
        if (function is EndIslandFunction) {
            obj.add("_class", JsonPrimitive("EndIslands"))
            return obj
        }

        if (function is LerpFunction) {
            val sub = BinaryFunction(BinaryFunction.Type.SUB, function.second(), function.first())
            val mul = BinaryFunction(BinaryFunction.Type.MUL, function.alpha(), sub)
            return serializeFunction(BinaryFunction(BinaryFunction.Type.ADD, function.first(), mul))
        }

        // ── TwoArgumentSimpleFunction (Binary / Linear) ───────────────────────
        if (function is BinaryFunction) {
            val value = JsonObject()
            obj.add("_class", JsonPrimitive("BinaryOperation"))
            value.add("type", JsonPrimitive(function.type().name))
            value.add("argument1", serializeFunction(function.left()))
            value.add("argument2", serializeFunction(function.right()))
            value.add("minValue", JsonPrimitive(function.range().min()))
            value.add("maxValue", JsonPrimitive(function.range().max()))
            obj.add("value", value)
            return obj
        }

        // ── Mapped (UnaryOperation) ───────────────────────────────────────────
        if (function is UnaryFunction) {
            obj.add("_class", JsonPrimitive("UnaryOperation"))
            val value = JsonObject()
            value.add("type", JsonPrimitive(function.type().name))
            value.add("input", serializeFunction(function.input()))
            value.add("minValue", JsonPrimitive(function.range().min()))
            value.add("maxValue", JsonPrimitive(function.range().max()))
            obj.add("value", value)
            return obj
        }

        // ── Clamp ─────────────────────────────────────────────────────────────
        if (function is ClampFunction) {
            obj.add("_class", JsonPrimitive("Clamp"))
            val value = JsonObject()
            value.add("input", serializeFunction(function.input()))
            value.add("minValue", JsonPrimitive(function.min()))
            value.add("maxValue", JsonPrimitive(function.max()))
            obj.add("value", value)
            return obj
        }

        // ── RangeChoice ───────────────────────────────────────────────────────
        if (function is RangeChoiceFunction) {
            obj.add("_class", JsonPrimitive("RangeChoice"))
            val value = JsonObject()
            value.add("input", serializeFunction(function.input()))
            value.add("whenInRange", serializeFunction(function.whenInRange()))
            value.add("whenOutOfRange", serializeFunction(function.whenOutOfRange()))
            value.add("minInclusive", JsonPrimitive(function.minInclusive()))
            value.add("maxExclusive", JsonPrimitive(function.maxExclusive()))
            obj.add("value", value)
            return obj
        }

        // ── Noise ─────────────────────────────────────────────────────────────
        if (function is NoiseFunction) {
            obj.add("_class", JsonPrimitive("Noise"))
            val value = JsonObject()
            value.add("noise", JsonPrimitive(noiseHolderPath(function.noise())))
            value.add("xzScale", JsonPrimitive(function.xzScale()))
            value.add("yScale", JsonPrimitive(function.yScale()))
            if (function.shiftX() !== McDensityFunctions.zero() ||
                function.shiftY() !== McDensityFunctions.zero() ||
                function.shiftZ() !== McDensityFunctions.zero()
            ) {
                obj.add("_class", JsonPrimitive("ShiftedNoise"))
                value.add("shiftX", serializeFunction(function.shiftX()))
                value.add("shiftY", serializeFunction(function.shiftY()))
                value.add("shiftZ", serializeFunction(function.shiftZ()))
            }
            obj.add("value", value)
            return obj
        }

        // ── ShiftA / ShiftB / Shift ───────────────────────────────────────────
        if (function is ShiftNoiseFunction.ShiftA) {
            obj.add("_class", JsonPrimitive("ShiftA"))
            val value = JsonObject()
            value.add("offsetNoise", JsonPrimitive(noiseHolderPath(function.offsetNoise())))
            obj.add("value", value)
            return obj
        }
        if (function is ShiftNoiseFunction.ShiftB) {
            obj.add("_class", JsonPrimitive("ShiftB"))
            val value = JsonObject()
            value.add("offsetNoise", JsonPrimitive(noiseHolderPath(function.offsetNoise())))
            obj.add("value", value)
            return obj
        }
        if (function is ShiftNoiseFunction) {
            obj.add("_class", JsonPrimitive("Shift"))
            val value = JsonObject()
            value.add("offsetNoise", JsonPrimitive(noiseHolderPath(function.offsetNoise())))
            obj.add("value", value)
            return obj
        }

        // ── InterpolatedNoiseSampler (BlendedNoise / OldBlendedNoise) ────────
        if (simpleName == "OldBlendedNoise" || simpleName == "BlendedNoise") {
            obj.add("_class", JsonPrimitive("InterpolatedNoiseSampler"))
            val value = JsonObject()

            fun getDouble(fieldName: String): Double {
                var cls: Class<*>? = function.javaClass
                while (cls != null) {
                    try {
                        val f = cls.getDeclaredField(fieldName)
                        f.isAccessible = true
                        return f.get(function) as Double
                    } catch (_: NoSuchFieldException) {}
                    cls = cls.superclass
                }
                throw NoSuchFieldException("Field '$fieldName' not found on ${function.javaClass.name}")
            }

            val xzScale  = getDouble("xzScale")
            val yScale   = getDouble("yScale")
            val xzFactor = getDouble("xzFactor")
            val yFactor  = getDouble("yFactor")
            val smear    = getDouble("smearScaleMultiplier")

            value.add("scaledXzScale",        JsonPrimitive(xzScale * xzFactor / 80.0))
            value.add("scaledYScale",         JsonPrimitive(yScale  * yFactor  / 80.0))
            value.add("xzFactor",             JsonPrimitive(xzFactor))
            value.add("yFactor",              JsonPrimitive(yFactor))
            value.add("smearScaleMultiplier", JsonPrimitive(smear))
            value.add("maxValue",             JsonPrimitive(function.range().max()))

            obj.add("value", value)
            return obj
        }

        // ── YClampedGradient ──────────────────────────────────────────────────
        if (function is GradientFunction) {
            obj.add("_class", JsonPrimitive("YClampedGradient"))
            val value = JsonObject()
            value.add("fromY", JsonPrimitive(function.fromCoordinate()))
            value.add("toY", JsonPrimitive(function.toCoordinate()))
            value.add("fromValue", JsonPrimitive(function.fromValue()))
            value.add("toValue", JsonPrimitive(function.toValue()))
            obj.add("value", value)
            return obj
        }

        // ── IntervalSelect ────────────────────────────────────────────────────
        if (simpleName == "IntervalSelect" || simpleName == "IntervalSelectFunction") {
            obj.add("_class", JsonPrimitive("IntervalSelect"))
            val value = JsonObject()
            val cls = function.javaClass

            val inputF = cls.getDeclaredField("input")
            inputF.isAccessible = true
            value.add("input", serializeFunction(inputF.get(function) as DensityFunction))

            val thresholdsF = cls.getDeclaredField("thresholds")
            thresholdsF.isAccessible = true
            val thresholds = thresholdsF.get(function) as Iterable<*>
            val thresholdsArr = JsonArray()
            thresholds.forEach { thresholdsArr.add(JsonPrimitive((it as Number).toDouble())) }
            value.add("thresholds", thresholdsArr)

            val functionsF = cls.getDeclaredField("functions")
            functionsF.isAccessible = true
            val functionsList = functionsF.get(function) as List<*>
            val functionsArr = JsonArray()
            functionsList.forEach { functionsArr.add(serializeFunction(it as DensityFunction)) }
            value.add("functions", functionsArr)

            obj.add("value", value)
            return obj
        }

        // ── FindTopSurface ────────────────────────────────────────────────────
        if (simpleName == "FindTopSurface" || simpleName == "FindTopSurfaceFunction") {
            obj.add("_class", JsonPrimitive("FindTopSurface"))
            val value = JsonObject()
            val cls = function.javaClass
            for (fieldName in listOf("density", "upperBound", "lowerBound", "cellHeight")) {
                val f = cls.getDeclaredField(fieldName)
                f.isAccessible = true
                when (val v = f.get(function)) {
                    is DensityFunction -> value.add(fieldName, serializeFunction(v))
                    is Int             -> value.add(fieldName, JsonPrimitive(v))
                    is Double          -> value.add(fieldName, JsonPrimitive(v))
                    else               -> value.add(fieldName, JsonPrimitive(v.toString()))
                }
            }
            obj.add("value", value)
            return obj
        }

        throw IllegalArgumentException(
            "Unhandled DensityFunction type: ${function.javaClass.name}"
        )
    }

    inner class Tests : Extractor.Extractor {
        override fun fileName(): String = "density_function_tests.json"

        override fun isTest(): Boolean = true

        override fun extract(server: MinecraftServer): JsonElement {
            val topLevelJson = JsonObject()
            val registryAccess = server.registryAccess()

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

            val functionLookup = registryAccess.lookupOrThrow(Registries.DENSITY_FUNCTION)

            for (functionName in functionNames) {
                val functionKey = ResourceKey.create(
                    Registries.DENSITY_FUNCTION,
                    Identifier.withDefaultNamespace(functionName)
                )

                val holder = functionLookup.get(functionKey).orElse(null)
                if (holder != null) {
                    topLevelJson.add(functionName, serializeFunction(holder.value()))
                } else {
                    println("Warning: Density function $functionName not found in registry.")
                }
            }

            return topLevelJson
        }
    }
}