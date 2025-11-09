package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import de.snowii.extractor.Extractor
import net.minecraft.SharedConstants
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.registry.BuiltinRegistries
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.HeightLimitView
import net.minecraft.world.Heightmap
import net.minecraft.world.biome.source.BiomeAccess
import net.minecraft.world.biome.source.BiomeSource
import net.minecraft.world.biome.source.util.MultiNoiseUtil.MultiNoiseSampler
import net.minecraft.world.biome.source.util.MultiNoiseUtil.NoiseHypercube
import net.minecraft.world.chunk.Chunk
import net.minecraft.world.chunk.ChunkStatus
import net.minecraft.world.chunk.ProtoChunk
import net.minecraft.world.chunk.UpgradeData
import net.minecraft.world.gen.HeightContext
import net.minecraft.world.gen.WorldPresets
import net.minecraft.world.gen.chunk.*
import net.minecraft.world.gen.densityfunction.DensityFunction
import net.minecraft.world.gen.densityfunction.DensityFunction.*
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes.RegistryEntryHolder
import net.minecraft.world.gen.noise.NoiseConfig
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredFunctions
import kotlin.system.exitProcess

class ChunkDumpTests {

    companion object {
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

        private fun populateNoise(
            settings: ChunkGeneratorSettings,
            chunkNoiseSampler: ChunkNoiseSampler,
            shapeConfig: GenerationShapeConfig,
            chunk: Chunk,
        ): Chunk {
            var sampleBlockState: KFunction<BlockState?>? = null
            for (method: KFunction<*> in chunkNoiseSampler::class.declaredFunctions) {
                if (method.name == "sampleBlockState") {
                    sampleBlockState = method as KFunction<BlockState?>
                }
            }

            val heightmap = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG)
            val heightmap2 = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG)
            val chunkPos = chunk.pos
            val i = chunkPos.startX
            val j = chunkPos.startZ
            val aquiferSampler = chunkNoiseSampler.aquiferSampler
            chunkNoiseSampler.sampleStartDensity()
            val mutable = BlockPos.Mutable()
            val k = shapeConfig.horizontalCellBlockCount()
            val l = shapeConfig.verticalCellBlockCount()
            val m = 16 / k
            val n = 16 / k

            val cellHeight = shapeConfig.height() / l
            val minimumCellY = shapeConfig.minimumY() / l

            for (o in 0..<m) {
                chunkNoiseSampler.sampleEndDensity(o)

                for (p in 0..<n) {
                    var q = chunk.countVerticalSections() - 1
                    var chunkSection = chunk.getSection(q)

                    for (r in cellHeight - 1 downTo 0) {
                        chunkNoiseSampler.onSampledCellCorners(r, p)

                        for (s in l - 1 downTo 0) {
                            val t = (minimumCellY + r) * l + s
                            val u = t and 15
                            val v = chunk.getSectionIndex(t)
                            if (q != v) {
                                q = v
                                chunkSection = chunk.getSection(v)
                            }

                            val d = s.toDouble() / l
                            chunkNoiseSampler.interpolateY(t, d)

                            for (w in 0..<k) {
                                val x = i + o * k + w
                                val y = x and 15
                                val e = w.toDouble() / k
                                chunkNoiseSampler.interpolateX(x, e)

                                for (z in 0..<k) {
                                    val aa = j + p * k + z
                                    val ab = aa and 15
                                    val f = z.toDouble() / k
                                    chunkNoiseSampler.interpolateZ(aa, f)
                                    var blockState = sampleBlockState!!.call(chunkNoiseSampler)
                                    if (blockState == null) {
                                        blockState = settings.defaultBlock()
                                    }

                                    if (!blockState!!.isAir && !SharedConstants.isOutsideGenerationArea(
                                            chunk.pos
                                        )
                                    ) {
                                        chunkSection.setBlockState(y, u, ab, blockState, false)
                                        heightmap.trackUpdate(y, t, ab, blockState)
                                        heightmap2.trackUpdate(y, t, ab, blockState)
                                        if (aquiferSampler.needsFluidTick() && !blockState.fluidState.isEmpty) {
                                            mutable[x, t] = aa
                                            chunk.markBlockForPostProcessing(mutable)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                chunkNoiseSampler.swapBuffers()
            }

            chunkNoiseSampler.stopInterpolation()
            return chunk
        }


        // This is basically just what NoiseChunkGenerator is doing
        private fun dumpPopulateNoise(
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

        class WrapperRemoverVisitor(private val wrappersToKeep: Iterable<String>) : DensityFunctionVisitor {
            override fun apply(densityFunction: DensityFunction?): DensityFunction {
                when (densityFunction) {
                    is DensityFunctionTypes.Wrapper -> {
                        val name = densityFunction.type().toString()
                        if (wrappersToKeep.contains(name)) {
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

        class WrapperValidateVisitor(private val wrappersToKeep: Iterable<String>) : DensityFunctionVisitor {
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
            val noiseRouter = config.noiseRouter.apply(WrapperRemoverVisitor(wrappersToKeep))
            for (field in config.javaClass.declaredFields) {
                if (field.name.equals("noiseRouter")) {
                    field.trySetAccessible()
                    field.set(config, noiseRouter)
                    return
                }
            }
            throw Exception("Failed to replace router")
        }

        fun createMultiNoiseSampler(config: NoiseConfig, sampler: ChunkNoiseSampler): MultiNoiseSampler {
            var createMultiNoiseSampler: Method? = null
            for (m: Method in sampler.javaClass.declaredMethods) {
                if (m.name == "createMultiNoiseSampler") {
                    m.trySetAccessible()
                    createMultiNoiseSampler = m
                    break
                }
            }

            val noiseSampler = createMultiNoiseSampler!!.invoke(
                sampler,
                config.noiseRouter,
                listOf<NoiseHypercube>()
            ) as MultiNoiseSampler

            return noiseSampler
        }
    }

    internal class SurfaceDump(
        private val filename: String,
        private val seed: Long,
        private val chunkX: Int,
        private val chunkZ: Int,
    ) : Extractor.Extractor {
        override fun fileName(): String = this.filename

        override fun extract(server: MinecraftServer): JsonElement {
            val biomeRegistry = server.registryManager.getOrThrow(RegistryKeys.BIOME)

            val chunkPos = ChunkPos(this.chunkX, this.chunkZ)

            val lookup = BuiltinRegistries.createWrapperLookup()
            val wrapper = lookup.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
            val noiseParams = lookup.getOrThrow(RegistryKeys.NOISE_PARAMETERS)

            val ref = wrapper.getOrThrow(ChunkGeneratorSettings.OVERWORLD)
            val settings = ref.value()
            val config = NoiseConfig.create(settings, noiseParams, seed)

            val options = WorldPresets.getDefaultOverworldOptions(lookup)
            var biomeSource: BiomeSource? = null
            for (f: Field in options.chunkGenerator.javaClass.fields) {
                if (f.name == "biomeSource") {
                    biomeSource = f.get(options.chunkGenerator) as BiomeSource
                }
            }

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

            val chunk = ProtoChunk(
                chunkPos, UpgradeData.NO_UPGRADE_DATA,
                HeightLimitView.create(options.chunkGenerator.minimumY, options.chunkGenerator.worldHeight),
                server.overworld.palettesFactory, null
            )

            val biomeNoiseSampler = createMultiNoiseSampler(config, testSampler)
            chunk.populateBiomes(biomeSource!!, biomeNoiseSampler)
            chunk.status = ChunkStatus.BIOMES

            populateNoise(settings, testSampler, shape, chunk)
            chunk.status = ChunkStatus.NOISE

            val biomeMixer = BiomeAccess(chunk, BiomeAccess.hashSeed(seed))
            val heightContext = HeightContext(options.chunkGenerator, chunk)
            config.surfaceBuilder.buildSurface(
                config,
                biomeMixer,
                biomeRegistry,
                settings.usesLegacyRandom,
                heightContext,
                chunk,
                testSampler,
                settings.surfaceRule
            )
            chunk.status = ChunkStatus.SURFACE

            val result = IntArray(16 * 16 * chunk.height)
            for (x in 0..15) {
                for (y in chunk.bottomY..chunk.topYInclusive) {
                    for (z in 0..15) {
                        val pos = BlockPos(x, y, z)
                        val blockState = chunk.getBlockState(pos)
                        val index = getIndex(shape, x, y - chunk.bottomY, z)
                        result[index] = Block.getRawIdFromState(blockState)
                    }
                }
            }

            val topLevelJson = JsonArray()
            result.forEach { state ->
                topLevelJson.add(state)
            }
            return topLevelJson
        }
    }

    internal class NoiseDump(
        private val filename: String,
        private val seed: Long,
        private val chunkX: Int,
        private val chunkZ: Int,
        private val allowedWrappers: Iterable<String>
    ) : Extractor.Extractor {
        override fun fileName(): String = this.filename

        // Dumps a chunk to an array of block state ids
        override fun extract(server: MinecraftServer): JsonElement {
            val topLevelJson = JsonArray()
            val chunkPos = ChunkPos(this.chunkX, this.chunkZ)

            val lookup = BuiltinRegistries.createWrapperLookup()
            val wrapper = lookup.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
            val noiseParams = lookup.getOrThrow(RegistryKeys.NOISE_PARAMETERS)

            val ref = wrapper.getOrThrow(ChunkGeneratorSettings.OVERWORLD)
            val settings = ref.value()
            val config = NoiseConfig.create(settings, noiseParams, seed)
            // Always have cellcache wrappers
            removeWrappers(config, this.allowedWrappers)
            config.noiseRouter.apply(WrapperValidateVisitor(this.allowedWrappers))

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

            val data = dumpPopulateNoise(chunkPos.startX, chunkPos.startZ, testSampler, shape, settings)
            data?.forEach { state ->
                topLevelJson.add(state)
            }

            return topLevelJson
        }
    }
}