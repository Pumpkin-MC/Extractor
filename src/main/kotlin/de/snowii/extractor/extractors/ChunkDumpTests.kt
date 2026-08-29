package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import de.snowii.extractor.Extractor
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelHeightAccessor
import net.minecraft.world.level.biome.BiomeManager
import net.minecraft.world.level.biome.MultiNoiseBiomeSource
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ProtoChunk
import net.minecraft.world.level.chunk.UpgradeData
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.levelgen.*
import net.minecraft.world.level.levelgen.blending.Blender
import java.lang.reflect.Constructor
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredFunctions
import kotlin.system.exitProcess

class ChunkDumpTests {

    companion object {
        private fun createFluidLevelSampler(settings: NoiseGeneratorSettings): Aquifer.FluidPicker {
            val fluidLevel = Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState())
            val i = settings.seaLevel()
            val fluidLevel2 = Aquifer.FluidStatus(i, settings.defaultFluid())
            return Aquifer.FluidPicker { _, y, _ -> if (y < Math.min(-54, i)) fluidLevel else fluidLevel2 }
        }

        private fun getIndex(config: NoiseSettings, x: Int, y: Int, z: Int): Int {
            if (x < 0 || y < 0 || z < 0) {
                println("Bad local pos")
                exitProcess(1)
            }
            return config.height() * 16 * x + 16 * y + z
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

        private fun populateNoise(
            settings: NoiseGeneratorSettings,
            chunkNoiseSampler: NoiseChunk,
            shapeConfig: NoiseSettings,
            chunk: ProtoChunk,
        ): ProtoChunk {
            val heightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG)
            val heightmap2 = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG)
            val chunkPos = chunk.pos
            val i = chunkPos.minBlockX
            val j = chunkPos.minBlockZ
            val aquiferSampler = chunkNoiseSampler.aquifer()
            chunkNoiseSampler.initializeForFirstCellX()
            val mutable = BlockPos.MutableBlockPos()
            val k = shapeConfig.cellWidth
            val l = shapeConfig.cellHeight
            val m = 16 / k
            val n = 16 / k

            val cellHeight = shapeConfig.height() / l
            val minimumCellY = Math.floorDiv(shapeConfig.minY(), l)

            for (o in 0..<m) {
                chunkNoiseSampler.advanceCellX(o)

                for (p in 0..<n) {
                    var q = chunk.sectionsCount - 1
                    var chunkSection = chunk.getSection(q)

                    for (r in cellHeight - 1 downTo 0) {
                        chunkNoiseSampler.selectCellYZ(r, p)

                        for (s in l - 1 downTo 0) {
                            val t = (minimumCellY + r) * l + s
                            val u = t and 15
                            val v = chunk.getSectionIndex(t)
                            if (q != v) {
                                q = v
                                chunkSection = chunk.getSection(v)
                            }

                            val d = s.toDouble() / l
                            chunkNoiseSampler.updateForY(t, d)

                            for (w in 0..<k) {
                                val x = i + o * k + w
                                val y = x and 15
                                val e = w.toDouble() / k
                                chunkNoiseSampler.updateForX(x, e)

                                for (z in 0..<k) {
                                    val aa = j + p * k + z
                                    val ab = aa and 15
                                    val f = z.toDouble() / k
                                    chunkNoiseSampler.updateForZ(aa, f)
                                    var blockState = chunkNoiseSampler.getInterpolatedState()
                                    if (blockState == null) {
                                        blockState = settings.defaultBlock()
                                    }

                                    if (!blockState.isAir) {
                                        chunkSection.setBlockState(y, u, ab, blockState, false)
                                        heightmap.update(y, t, ab, blockState)
                                        heightmap2.update(y, t, ab, blockState)
                                        if (aquiferSampler.shouldScheduleFluidUpdate() && !blockState.fluidState.isEmpty) {
                                            mutable.set(y, t, ab); chunk.markPosForPostProcessing(mutable)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                chunkNoiseSampler.swapSlices()
            }

            chunkNoiseSampler.stopInterpolation()
            return chunk
        }

        private fun dumpPopulateNoise(
            startX: Int,
            startZ: Int,
            sampler: NoiseChunk,
            config: NoiseSettings,
            settings: NoiseGeneratorSettings
        ): IntArray? {
            val result = IntArray(16 * 16 * config.height())

            sampler.initializeForFirstCellX()
            val k = config.cellWidth
            val l = config.cellHeight

            val m = 16 / k
            val n = 16 / k

            val cellHeight = config.height() / l
            val minimumCellY = Math.floorDiv(config.minY(), l)

            for (o in 0..<m) {
                sampler.advanceCellX(o)
                for (p in 0..<n) {
                    for (r in (0..<cellHeight).reversed()) {
                        sampler.selectCellYZ(r, p)
                        for (s in (0..<l).reversed()) {
                            val t = (minimumCellY + r) * l + s
                            val d = s.toDouble() / l.toDouble()
                            sampler.updateForY(t, d)
                            for (w in 0..<k) {
                                val x = startX + o * k + w
                                val y = x and 15
                                val e = w.toDouble() / k.toDouble()
                                sampler.updateForX(x, e)
                                for (z in 0..<k) {
                                    val aa = startZ + p * k + z
                                    val ab = aa and 15
                                    val f = z.toDouble() / k.toDouble()
                                    sampler.updateForZ(aa, f)
                                    var blockState = sampler.getInterpolatedState()
                                    if (blockState == null) {
                                        blockState = settings.defaultBlock()
                                    }
                                    val index = this.getIndex(config, y, t - config.minY(), ab)
                                    result[index] = Block.getId(blockState)
                                }
                            }
                        }
                    }
                }
                sampler.swapSlices()
            }
            sampler.stopInterpolation()
            return result
        }

        class WrapperRemoverVisitor(private val wrappersToKeep: Iterable<String>) : DensityFunction.Visitor {
            override fun apply(densityFunction: DensityFunction): DensityFunction {
                when (densityFunction) {
                    is DensityFunctions.Marker -> {
                        val name = densityFunction.type().toString()
                        if (wrappersToKeep.contains(name)) {
                            return densityFunction
                        }
                        return this.apply(densityFunction.wrapped())
                    }

                    is DensityFunctions.HolderHolder -> {
                        return this.apply(densityFunction.function().value())
                    }

                    else -> return densityFunction
                }
            }
        }

        class WrapperValidateVisitor(private val wrappersToKeep: Iterable<String>) : DensityFunction.Visitor {
            override fun apply(densityFunction: DensityFunction): DensityFunction {
                when (densityFunction) {
                    is DensityFunctions.Marker -> {
                        val name = densityFunction.type().toString()
                        if (wrappersToKeep.contains(name)) {
                            return densityFunction
                        }
                        throw Exception(name + "is still in the function!")
                    }

                    is DensityFunctions.HolderHolder -> {
                        return this.apply(densityFunction.function().value())
                    }

                    else -> return densityFunction
                }
            }
        }

        private fun removeWrappers(config: RandomState, wrappersToKeep: Iterable<String>) {
            val noiseRouter = config.router().mapAll(WrapperRemoverVisitor(wrappersToKeep))
            for (field in config.javaClass.declaredFields) {
                if (field.name == "router") {
                    field.isAccessible = true
                    field.set(config, noiseRouter)
                    return
                }
            }
            throw Exception("Failed to replace router")
        }

        fun createMultiNoiseSampler(config: RandomState, sampler: NoiseChunk): net.minecraft.world.level.biome.Climate.Sampler {
            return sampler.cachedClimateSampler(config.router(), listOf())
        }
    }

    internal class SurfaceDump(
        private val filename: String,
        private val seed: Long,
        private val chunkX: Int,
        private val chunkZ: Int,
        private val dimension: String = "overworld"
    ) : Extractor.Extractor {
        override fun fileName(): String = this.filename

        override fun isTest(): Boolean = true

        override fun extract(server: MinecraftServer): JsonElement {
            val chunkPos = ChunkPos(this.chunkX, this.chunkZ)

            val dimKey = when (dimension) {
                "nether" -> NoiseGeneratorSettings.NETHER
                "end" -> NoiseGeneratorSettings.END
                else -> NoiseGeneratorSettings.OVERWORLD
            }
            val serverLevel = when (dimension) {
                "nether" -> server.getLevel(Level.NETHER)!!
                "end" -> server.getLevel(Level.END)!!
                else -> server.overworld()
            }

            val ref = server.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(dimKey)
            val settings = ref.value()

            val noiseParams = server.registryAccess().lookupOrThrow(Registries.NOISE)
            val config = RandomState.create(settings, noiseParams, seed)

            val chunkGenerator = serverLevel.chunkSource.generator
            val biomeSource = chunkGenerator.biomeSource

            val shape = settings.noiseSettings()
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

            val biomeNoiseSampler = createMultiNoiseSampler(config, testSampler)
            chunk.fillBiomesFromNoise(biomeSource, biomeNoiseSampler)
            chunk.persistedStatus = ChunkStatus.BIOMES

            populateNoise(settings, testSampler, shape, chunk)
            chunk.persistedStatus = ChunkStatus.NOISE

            val biomeMixer = BiomeManager({ x, y, z -> biomeSource.getNoiseBiome(x, y, z, biomeNoiseSampler) }, BiomeManager.obfuscateSeed(seed))
            val heightContext = WorldGenerationContext(chunkGenerator, serverLevel)
            config.surfaceSystem().buildSurface(
                config,
                biomeMixer,
                settings.useLegacyRandomSource(),
                heightContext,
                chunk,
                testSampler,
                settings.surfaceRule(),
                biomeSource.possibleBiomes()
            )
            chunk.persistedStatus = ChunkStatus.SURFACE

            val result = IntArray(16 * 16 * chunk.height)
            for (x in 0..15) {
                for (y in chunk.minY..chunk.maxY) {
                    for (z in 0..15) {
                        val pos = BlockPos(x, y, z)
                        val blockState = chunk.getBlockState(pos)
                        val index = getIndex(shape, x, y - chunk.minY, z)
                        result[index] = Block.getId(blockState)
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
        private val allowedWrappers: Iterable<String>,
        private val dimension: String = "overworld"
    ) : Extractor.Extractor {
        override fun fileName(): String = "noise_$filename"

        override fun isTest(): Boolean = true

        override fun extract(server: MinecraftServer): JsonElement {
            val topLevelJson = JsonArray()
            val chunkPos = ChunkPos(this.chunkX, this.chunkZ)

            val dimKey = when (dimension) {
                "nether" -> NoiseGeneratorSettings.NETHER
                "end" -> NoiseGeneratorSettings.END
                else -> NoiseGeneratorSettings.OVERWORLD
            }
            val serverLevel = when (dimension) {
                "nether" -> server.getLevel(Level.NETHER)!!
                "end" -> server.getLevel(Level.END)!!
                else -> server.overworld()
            }

            val ref = server.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(dimKey)
            val settings = ref.value()

            val noiseParams = server.registryAccess().lookupOrThrow(Registries.NOISE)
            val config = RandomState.create(settings, noiseParams, seed)

            removeWrappers(config, this.allowedWrappers)
            config.router().mapAll(WrapperValidateVisitor(this.allowedWrappers))

            val shape = settings.noiseSettings()
            val chunkGenerator = serverLevel.chunkSource.generator
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

            val data = dumpPopulateNoise(chunkPos.minBlockX, chunkPos.minBlockZ, testSampler, shape, settings)
            data?.forEach { state ->
                topLevelJson.add(state)
            }

            return topLevelJson
        }
    }
}
