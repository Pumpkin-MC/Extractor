package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import de.snowii.extractor.Extractor
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.registry.BuiltinRegistries
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.gen.chunk.*
import net.minecraft.world.gen.densityfunction.DensityFunction
import net.minecraft.world.gen.densityfunction.DensityFunction.*
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes.RegistryEntryHolder
import net.minecraft.world.gen.noise.NoiseConfig
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredFunctions
import kotlin.system.exitProcess

class ChunkDumpTests : Extractor.Extractor {
    override fun fileName(): String = "chunk.json"

    private fun createFluidLevelSampler(settings: ChunkGeneratorSettings): AquiferSampler.FluidLevelSampler {
        val fluidLevel = AquiferSampler.FluidLevel(-54, Blocks.LAVA.defaultState)
        val i = settings.seaLevel()
        val fluidLevel2 = AquiferSampler.FluidLevel(i, settings.defaultFluid())
        return AquiferSampler.FluidLevelSampler { _, y, _ -> if (y < Math.min(-54, i)) fluidLevel else fluidLevel2 }
    }

    private fun getIndex(config: GenerationShapeConfig, x: Int, y: Int, z: Int): Int {
        if (x < 0 || y < 0 || z < 0) {
            println("Bad local pos")
            exitProcess(1)
        }
        return config.height() * 16 * x + 16 * y + z
    }

    // This is basically just what NoiseChunkGenerator is doing
    private fun populateNoise(
        startX: Int,
        startZ: Int,
        sampler: ChunkNoiseSampler,
        config: GenerationShapeConfig,
        settings: ChunkGeneratorSettings
    ): IntArray? {
        val result = IntArray(16 * 16 * config.height())

        for (method: KFunction<*> in sampler::class.declaredFunctions) {
            if (method.name.equals("sampleBlockState")) {
                sampler.sampleStartDensity()
                val k = config.horizontalCellBlockCount()
                val l = config.verticalCellBlockCount()

                val m = 16 / k
                val n = 16 / k

                val cellHeight = config.height() / l
                val minimumCellY = config.minimumY() / l

                for (o in 0..<m) {
                    sampler.sampleEndDensity(o)
                    for (p in 0..<n) {
                        for (r in (0..<cellHeight).reversed()) {
                            sampler.onSampledCellCorners(r, p)
                            for (s in (0..<l).reversed()) {
                                val t = (minimumCellY + r) * l + s
                                val d = s.toDouble() / l.toDouble()
                                sampler.interpolateY(t, d)
                                for (w in 0..<k) {
                                    val x = startX + o * k + w
                                    val y = x and 15
                                    val e = w.toDouble() / k.toDouble()
                                    sampler.interpolateX(x, e)
                                    for (z in 0..<k) {
                                        val aa = startZ + p * k + z
                                        val ab = aa and 15
                                        val f = z.toDouble() / k.toDouble()
                                        sampler.interpolateZ(aa, f)
                                        var blockState = method.call(sampler) as BlockState?
                                        if (blockState == null) {
                                            blockState = settings.defaultBlock()
                                        }
                                        val index = this.getIndex(config, y, t - config.minimumY(), ab)
                                        result[index] = Block.getRawIdFromState(blockState)
                                    }
                                }
                            }
                        }
                    }
                    sampler.swapBuffers()
                }
                sampler.stopInterpolation()
                return result
            }
        }
        System.err.println("No valid method found for block state sampler!")
        return null
    }

    inner class WrapperRemoverVisitor(private val wrappersToKeep: Iterable<String>): DensityFunctionVisitor {
        override fun apply(densityFunction: DensityFunction?): DensityFunction {
            when (densityFunction) {
                is DensityFunctionTypes.Wrapper -> {
                    val name = densityFunction.type().toString()
                    if (wrappersToKeep.contains(name)) {
                        println("Keeping " + name)
                        return densityFunction
                    }
                    return this.apply(densityFunction.wrapped())
                }
                is RegistryEntryHolder -> {
                    return this.apply(densityFunction.function.value())
                }
                else -> return densityFunction!!
            }
        }
    }

    inner class WrapperValidateVisitor(private val wrappersToKeep: Iterable<String>): DensityFunctionVisitor {
        override fun apply(densityFunction: DensityFunction?): DensityFunction {
            when (densityFunction) {
                is DensityFunctionTypes.Wrapper -> {
                    val name = densityFunction.type().toString()
                    if (wrappersToKeep.contains(name)) {
                        return densityFunction
                    }
                    throw Exception(name + "is still in the function!")
                }
                is RegistryEntryHolder -> {
                    return this.apply(densityFunction.function.value())
                }
                else -> return densityFunction!!
            }
        }
    }

    // Available:
    //Interpolated
    //CacheOnce
    //FlatCache
    //Cache2D
    //
    //CellCache is only added inside the ChunkSampler itself so it cannot be removed and will always be in the function
    private fun removeWrappers(config: NoiseConfig, wrappersToKeep: Iterable<String>) {
        val noiseRouter = config.noiseRouter.apply(this.WrapperRemoverVisitor(wrappersToKeep))
        for (field in config.javaClass.declaredFields) {
            if (field.name.equals("noiseRouter")) {
                field.trySetAccessible()
                field.set(config, noiseRouter)
                return
            }
        }
        throw Exception("Failed to replace router")
    }

    // Dumps a chunk to an array of block state ids
    override fun extract(server: MinecraftServer): JsonElement {
        val topLevelJson = JsonArray()
        val seed = 0L
        val chunkPos = ChunkPos(0, 0)

        val lookup = BuiltinRegistries.createWrapperLookup()
        val wrapper = lookup.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
        val noiseParams = lookup.getOrThrow(RegistryKeys.NOISE_PARAMETERS)

        val ref = wrapper.getOrThrow(ChunkGeneratorSettings.OVERWORLD)
        val settings = ref.value()
        val config = NoiseConfig.create(settings, noiseParams, seed)
        // Always have cellcache wrappers
        val allowed = arrayListOf("Interpolated")
        removeWrappers(config, allowed)
        config.noiseRouter.apply(WrapperValidateVisitor(allowed))

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
                }, settings, createFluidLevelSampler(settings), Blender.getNoBlending()
            )

        /*
        testSampler.sampleEndDensity(0)
        testSampler.onSampledCellCorners(0,0)
        var good = false
        for (field in testSampler.javaClass.declaredFields) {
            if (field.name.equals("caches")) {
                field.trySetAccessible()
                val caches = field.get(testSampler) as List<DensityFunction>;
                assert(caches.size == 1)
                val cellCache = caches[0]
                var good2 = false
                for (field in cellCache.javaClass.declaredFields) {
                    if (field.name.equals("cache")) {
                        field.trySetAccessible()
                        val cache = field.get(cellCache) as DoubleArray
                        cache.forEach { value ->
                            topLevelJson.add(value)
                        }
                        return topLevelJson

                        good2 = true
                        break
                    }
                }
                if (!good2) {
                    throw Exception("Failed to find cell cache's cache")
                }
                good = true
                break
            }
        }
        if (!good) {
            throw Exception("Failed to find cell caches")
        }
         */


        val data = populateNoise(chunkPos.startX, chunkPos.startZ, testSampler, shape, settings)
        data?.forEach { state ->
            topLevelJson.add(state)
        }

        return topLevelJson
    }
}
