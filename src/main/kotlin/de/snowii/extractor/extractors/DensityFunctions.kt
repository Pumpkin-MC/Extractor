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
import net.minecraft.world.level.levelgen.DensityFunction
import net.minecraft.world.level.levelgen.DensityFunctions
import net.minecraft.world.level.levelgen.NoiseRouter

class DensityFunctions : Extractor.Extractor {
    override fun fileName(): String = "density_function.json"

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

            if (v is DensityFunctions.Spline.Coordinate) {
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

    private fun noiseHolderPath(holder: DensityFunction.NoiseHolder): String {
        val key = holder.noiseData().unwrapKey().orElse(null)
        return key?.identifier()?.path ?: "inline"
    }

    private fun serializeFunction(function: DensityFunction): JsonObject {
        if (function is DensityFunctions.HolderHolder) {
            return serializeFunction(function.function().value())
        }

        val obj = JsonObject()
        val simpleName = function.javaClass.simpleName

        // ── Marker / Wrapping ──────────────────────────────────────────────────
        if (function is DensityFunctions.Marker) {
            // BlendDensity was demoted from its own class to a Marker type in newer versions.
            // We intercept it here to maintain the legacy JSON structure.
            if (function.type().serializedName == "blend_density") {
                obj.add("_class", JsonPrimitive("BlendDensity"))
                val value = JsonObject()
                value.add("input", serializeFunction(function.wrapped()))
                obj.add("value", value)
                return obj
            }

            val rustTypeName = when (function.type().serializedName) {
                "flat_cache"        -> "FlatCache"
                "cache_2d"          -> "Cache2D"
                "interpolated"      -> "Interpolated"
                "cache_once"        -> "CacheOnce"
                "cache_all_in_cell" -> "CellCache"
                else -> function.type().serializedName
            }
            obj.add("_class", JsonPrimitive("Wrapping"))
            val value = JsonObject()
            value.add("type", JsonPrimitive(rustTypeName))
            value.add("wrapped", serializeFunction(function.wrapped()))
            obj.add("value", value)
            return obj
        }

        // ── Spline ────────────────────────────────────────────────────────────
        if (function is DensityFunctions.Spline) {
            obj.add("_class", JsonPrimitive("Spline"))
            val value = JsonObject()
            value.add("minValue", JsonPrimitive(function.minValue()))
            value.add("maxValue", JsonPrimitive(function.maxValue()))
            value.add("spline", serializeSpline(function.spline()))
            obj.add("value", value)
            return obj
        }

        // ── Constant ──────────────────────────────────────────────────────────
        if (function is DensityFunctions.Constant) {
            obj.add("_class", JsonPrimitive("Constant"))
            val value = JsonObject()
            value.add("value", JsonPrimitive(function.value()))
            obj.add("value", value)
            return obj
        }

        // ── Stateless singletons ──────────────────────────────────────────────
        if (function is DensityFunctions.BlendAlpha) {
            obj.add("_class", JsonPrimitive("BlendAlpha"))
            return obj
        }
        if (function is DensityFunctions.BlendOffset) {
            obj.add("_class", JsonPrimitive("BlendOffset"))
            return obj
        }
        if (function is DensityFunctions.BeardifierOrMarker) {
            obj.add("_class", JsonPrimitive("Beardifier"))
            return obj
        }
        if (function is DensityFunctions.EndIslandDensityFunction) {
            obj.add("_class", JsonPrimitive("EndIslands"))
            return obj
        }

        // ── TwoArgumentSimpleFunction (Binary / Linear) ───────────────────────
        if (function is DensityFunctions.TwoArgumentSimpleFunction) {
            val value = JsonObject()

            val mulOrAddClass = try {
                Class.forName("net.minecraft.world.level.levelgen.DensityFunctions\$MulOrAdd")
            } catch (_: ClassNotFoundException) { null }

            if (mulOrAddClass != null && mulOrAddClass.isInstance(function)) {
                obj.add("_class", JsonPrimitive("LinearOperation"))

                val specificTypeField = mulOrAddClass.getDeclaredField("specificType")
                specificTypeField.isAccessible = true
                val specificType = specificTypeField.get(function) as Enum<*>
                value.add("specificType", JsonPrimitive(specificType.name))

                val inputField = mulOrAddClass.getDeclaredField("input")
                inputField.isAccessible = true
                value.add("input", serializeFunction(inputField.get(function) as DensityFunction))

                val argumentField = mulOrAddClass.getDeclaredField("argument")
                argumentField.isAccessible = true
                value.add("argument", JsonPrimitive(argumentField.get(function) as Double))

                val minValueField = mulOrAddClass.getDeclaredField("minValue")
                minValueField.isAccessible = true
                value.add("minValue", JsonPrimitive(minValueField.get(function) as Double))

                val maxValueField = mulOrAddClass.getDeclaredField("maxValue")
                maxValueField.isAccessible = true
                value.add("maxValue", JsonPrimitive(maxValueField.get(function) as Double))

            } else {
                obj.add("_class", JsonPrimitive("BinaryOperation"))

                val typeEnum = function.type() as Enum<*>
                value.add("type", JsonPrimitive(typeEnum.name))

                value.add("argument1", serializeFunction(function.argument1()))
                value.add("argument2", serializeFunction(function.argument2()))

                val ap2Class = function.javaClass
                val minF = ap2Class.getDeclaredField("minValue")
                minF.isAccessible = true
                value.add("minValue", JsonPrimitive(minF.get(function) as Double))

                val maxF = ap2Class.getDeclaredField("maxValue")
                maxF.isAccessible = true
                value.add("maxValue", JsonPrimitive(maxF.get(function) as Double))
            }

            obj.add("value", value)
            return obj
        }

        // ── Mapped (UnaryOperation) ───────────────────────────────────────────
        if (function is DensityFunctions.Mapped) {
            obj.add("_class", JsonPrimitive("UnaryOperation"))
            val value = JsonObject()
            value.add("type", JsonPrimitive(function.type().serializedName.uppercase()))
            value.add("input", serializeFunction(function.input()))
            value.add("minValue", JsonPrimitive(function.minValue()))
            value.add("maxValue", JsonPrimitive(function.maxValue()))
            obj.add("value", value)
            return obj
        }

        // ── Clamp ─────────────────────────────────────────────────────────────
        if (function is DensityFunctions.Clamp) {
            obj.add("_class", JsonPrimitive("Clamp"))
            val value = JsonObject()
            value.add("input", serializeFunction(function.input()))
            value.add("minValue", JsonPrimitive(function.minValue()))
            value.add("maxValue", JsonPrimitive(function.maxValue()))
            obj.add("value", value)
            return obj
        }

        // ── RangeChoice ───────────────────────────────────────────────────────
        if (function is DensityFunctions.RangeChoice) {
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
        if (function is DensityFunctions.Noise) {
            obj.add("_class", JsonPrimitive("Noise"))
            val value = JsonObject()
            value.add("noise", JsonPrimitive(noiseHolderPath(function.noise())))
            value.add("xzScale", JsonPrimitive(function.xzScale()))
            value.add("yScale", JsonPrimitive(function.yScale()))
            obj.add("value", value)
            return obj
        }

        // ── ShiftA / ShiftB / Shift ───────────────────────────────────────────
        if (function is DensityFunctions.ShiftA) {
            obj.add("_class", JsonPrimitive("ShiftA"))
            val value = JsonObject()
            value.add("offsetNoise", JsonPrimitive(noiseHolderPath(function.offsetNoise())))
            obj.add("value", value)
            return obj
        }
        if (function is DensityFunctions.ShiftB) {
            obj.add("_class", JsonPrimitive("ShiftB"))
            val value = JsonObject()
            value.add("offsetNoise", JsonPrimitive(noiseHolderPath(function.offsetNoise())))
            obj.add("value", value)
            return obj
        }
        if (simpleName == "Shift") {
            obj.add("_class", JsonPrimitive("Shift"))
            val value = JsonObject()
            val offsetF = function.javaClass.getDeclaredField("offsetNoise")
            offsetF.isAccessible = true
            val offsetNoise = offsetF.get(function) as DensityFunction.NoiseHolder
            value.add("offsetNoise", JsonPrimitive(noiseHolderPath(offsetNoise)))
            obj.add("value", value)
            return obj
        }

        // ── ShiftedNoise ──────────────────────────────────────────────────────
        if (function is DensityFunctions.ShiftedNoise) {
            obj.add("_class", JsonPrimitive("ShiftedNoise"))
            val value = JsonObject()
            value.add("shiftX", serializeFunction(function.shiftX()))
            value.add("shiftY", serializeFunction(function.shiftY()))
            value.add("shiftZ", serializeFunction(function.shiftZ()))
            value.add("xzScale", JsonPrimitive(function.xzScale()))
            value.add("yScale", JsonPrimitive(function.yScale()))
            value.add("noise", JsonPrimitive(noiseHolderPath(function.noise())))
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
            value.add("maxValue",             JsonPrimitive(function.maxValue()))

            obj.add("value", value)
            return obj
        }

        // ── YClampedGradient ──────────────────────────────────────────────────
        if (function is DensityFunctions.YClampedGradient) {
            obj.add("_class", JsonPrimitive("YClampedGradient"))
            val value = JsonObject()
            value.add("fromY", JsonPrimitive(function.fromY()))
            value.add("toY", JsonPrimitive(function.toY()))
            value.add("fromValue", JsonPrimitive(function.fromValue()))
            value.add("toValue", JsonPrimitive(function.toValue()))
            obj.add("value", value)
            return obj
        }

        // ── IntervalSelect ────────────────────────────────────────────────────
        if (simpleName == "IntervalSelect") {
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
        if (simpleName == "FindTopSurface") {
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

    private fun serializeRouter(router: NoiseRouter): JsonObject {
        val obj = JsonObject()

        fun add(jsonKey: String, fn: DensityFunction) =
            obj.add(jsonKey, serializeFunction(fn))

        add("barrierNoise",                 router.barrierNoise())
        add("fluidLevelFloodednessNoise",   router.fluidLevelFloodednessNoise())
        add("fluidLevelSpreadNoise",        router.fluidLevelSpreadNoise())
        add("lavaNoise",                    router.lavaNoise())
        add("temperature",                  router.temperature())
        add("vegetation",                   router.vegetation())
        add("continents",                   router.continents())
        add("erosion",                      router.erosion())
        add("depth",                        router.depth())
        add("ridges",                       router.ridges())
        add("preliminarySurfaceLevel",      router.preliminarySurfaceLevel())
        add("finalDensity",                 router.finalDensity())
        add("veinToggle",                   router.veinToggle())
        add("veinRidged",                   router.veinRidged())
        add("veinGap",                      router.veinGap())

        return obj
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val topLevelJson = JsonObject()
        val registry = server.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS)

        registry.listElements().forEach { entry ->
            val settings = entry.value()
            val path = entry.key().identifier().path
            topLevelJson.add(path, serializeRouter(settings.noiseRouter()))
        }
        return topLevelJson
    }

    inner class Tests : Extractor.Extractor {
        override fun fileName(): String = "density_function_tests.json"

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