package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
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
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState
import net.minecraft.world.level.levelgen.*
import net.minecraft.world.level.levelgen.blending.Blender
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions as McDensityFunctions
import net.minecraft.world.level.levelgen.densityfunction.op.CacheFunction
import net.minecraft.world.level.levelgen.densityfunction.op.InterpolatedFunction
import java.lang.reflect.Constructor
import java.util.function.Predicate
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredFunctions
import kotlin.system.exitProcess
import net.minecraft.core.Holder
import net.minecraft.core.QuartPos
import net.minecraft.core.SectionPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.BiomeResolver
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureCheck
import net.minecraft.world.level.levelgen.structure.StructureStart
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.ticks.LevelTickAccess
import net.minecraft.world.ticks.WorldGenTickAccess

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
            chunkGenerator: ChunkGenerator,
            randomState: RandomState,
            heightAccessor: LevelHeightAccessor,
        ): ProtoChunk {
            val heightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG)
            val heightmap2 = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG)
            val chunkPos = chunk.pos
            val i = chunkPos.minBlockX
            val j = chunkPos.minBlockZ
            val aquiferSampler = chunkNoiseSampler.aquifer()
            val samplers = chunkNoiseSampler.cachingSamplers()
            val finalDensity = randomState.router().finalDensity()
            val mutable = BlockPos.MutableBlockPos()
            val k = 4
            val l = 8
            val m = 16 / k
            val n = 16 / k

            val cellHeight = shapeConfig.height() / l
            val minimumCellY = Math.floorDiv(shapeConfig.minY(), l)

            for (o in 0..<m) {
                for (p in 0..<n) {
                    var q = chunk.sectionsCount - 1
                    var chunkSection = chunk.getSection(q)

                    for (r in cellHeight - 1 downTo 0) {
                        for (s in l - 1 downTo 0) {
                            val t = (minimumCellY + r) * l + s
                            val u = t and 15
                            val v = chunk.getSectionIndex(t)
                            if (q != v) {
                                q = v
                                chunkSection = chunk.getSection(v)
                            }

                            for (w in 0..<k) {
                                val x = i + o * k + w
                                val y = x and 15

                                for (z in 0..<k) {
                                    val aa = j + p * k + z
                                    val ab = aa and 15
                                    val density = samplers.sampleValue(finalDensity, x, t, aa).toDouble()
                                    var blockState = aquiferSampler.computeSubstance(x, t, aa, density)
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
            }

            return chunk
        }

        private fun dumpPopulateNoise(
            startX: Int,
            startZ: Int,
            sampler: NoiseChunk,
            config: NoiseSettings,
            settings: NoiseGeneratorSettings,
            chunkGenerator: ChunkGenerator,
            randomState: RandomState,
            heightAccessor: LevelHeightAccessor,
        ): IntArray? {
            val result = IntArray(16 * 16 * config.height())
            val k = 4
            val l = 8

            val m = 16 / k
            val n = 16 / k

            val cellHeight = config.height() / l
            val minimumCellY = Math.floorDiv(config.minY(), l)
            val aquiferSampler = sampler.aquifer()
            val samplers = sampler.cachingSamplers()
            val finalDensity = randomState.router().finalDensity()

            for (o in 0..<m) {
                for (p in 0..<n) {
                    for (r in (0..<cellHeight).reversed()) {
                        for (s in (0..<l).reversed()) {
                            val t = (minimumCellY + r) * l + s
                            for (w in 0..<k) {
                                val x = startX + o * k + w
                                val y = x and 15
                                for (z in 0..<k) {
                                    val aa = startZ + p * k + z
                                    val ab = aa and 15
                                    val density = samplers.sampleValue(finalDensity, x, t, aa).toDouble()
                                    var blockState = aquiferSampler.computeSubstance(x, t, aa, density)
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
            }
            return result
        }

        class WrapperRemoverVisitor(private val wrappersToKeep: Iterable<String>) : DfRewriteRule {
            override fun rewrite(densityFunction: DensityFunction): DensityFunction {
                when (densityFunction) {
                    is InterpolatedFunction -> {
                        val name = "Interpolated"
                        if (wrappersToKeep.contains(name)) {
                            return densityFunction.rewriteChildren(this)
                        }
                        return this.rewrite(densityFunction.input())
                    }

                    is CacheFunction -> {
                        val name = "CacheOnce"
                        if (wrappersToKeep.contains(name) ||
                            wrappersToKeep.contains("FlatCache") ||
                            wrappersToKeep.contains("Cache2D") ||
                            wrappersToKeep.contains("CellCache")
                        ) {
                            return densityFunction.rewriteChildren(this)
                        }
                        return this.rewrite(densityFunction.input())
                    }

                    is McDensityFunctions.HolderHolder -> {
                        return this.rewrite(densityFunction.function().value())
                    }

                    else -> return densityFunction.rewriteChildren(this)
                }
            }
        }

        class WrapperValidateVisitor(private val wrappersToKeep: Iterable<String>) : DfRewriteRule {
            override fun rewrite(densityFunction: DensityFunction): DensityFunction {
                when (densityFunction) {
                    is InterpolatedFunction -> {
                        val name = "Interpolated"
                        if (wrappersToKeep.contains(name)) {
                            return densityFunction.rewriteChildren(this)
                        }
                        throw Exception(name + "is still in the function!")
                    }

                    is CacheFunction -> {
                        val name = "CacheOnce"
                        if (wrappersToKeep.contains(name) ||
                            wrappersToKeep.contains("FlatCache") ||
                            wrappersToKeep.contains("Cache2D") ||
                            wrappersToKeep.contains("CellCache")
                        ) {
                            return densityFunction.rewriteChildren(this)
                        }
                        throw Exception(name + "is still in the function!")
                    }

                    is McDensityFunctions.HolderHolder -> {
                        return this.rewrite(densityFunction.function().value())
                    }

                    else -> return densityFunction.rewriteChildren(this)
                }
            }
        }

        private fun RandomState.router(): NoiseRouter {
            val field = javaClass.getDeclaredField("router")
            field.isAccessible = true
            return field.get(this) as NoiseRouter
        }

        private fun mapRouter(router: NoiseRouter, rule: DfRewriteRule): NoiseRouter {
            return NoiseRouter(
                rule.rewrite(router.temperature()),
                rule.rewrite(router.vegetation()),
                rule.rewrite(router.continents()),
                rule.rewrite(router.erosion()),
                rule.rewrite(router.depth()),
                rule.rewrite(router.ridges()),
                rule.rewrite(router.chunkSurfaceLevel()),
                rule.rewrite(router.finalDensity()),
            )
        }

        private fun removeWrappers(config: RandomState, wrappersToKeep: Iterable<String>) {
            val noiseRouter = mapRouter(config.router(), WrapperRemoverVisitor(wrappersToKeep))
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
            return config.router().createClimateSampler(sampler.cachingSamplers())
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
            val config = RandomState.create(noiseParams, seed, settings)

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
                NoiseChunk(
                    config,
                    object : Beardifier(emptyList(), emptyList(), null) {
                        override fun sampleValue(context: SamplerContext, x: Int, y: Int, z: Int): Float = 0.0f
                    },
                    settings,
                    createFluidLevelSampler(settings),
                    Blender.empty(),
                    DensityVolume(16, chunk.height, 16, chunk.pos.minBlockX, chunk.minY, chunk.pos.minBlockZ)
                )

            val biomeNoiseSampler = createMultiNoiseSampler(config, testSampler)
            val biomeResolver = biomeSource.createResolver(biomeNoiseSampler)
            chunk.fillBiomesFromNoise(biomeResolver)
            chunk.persistedStatus = ChunkStatus.BIOMES

            populateNoise(settings, testSampler, shape, chunk, chunkGenerator, config, levelHeightAccessor)
            chunk.persistedStatus = ChunkStatus.TERRAIN

            val biomeMixer = BiomeManager(biomeResolver, BiomeManager.obfuscateSeed(seed))
            val heightContext = WorldGenerationContext(chunkGenerator, serverLevel)
            config.surfaceSystem().buildSurface(
                config,
                biomeMixer,
                heightContext,
                chunk,
                testSampler,
                settings.materialRule().value(),
                biomeSource.possibleBiomes()
            )
            chunk.persistedStatus = ChunkStatus.TERRAIN

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

    internal class CarverDump(
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
            val config = RandomState.create(noiseParams, seed, settings)

            val chunkGenerator = serverLevel.chunkSource.generator as NoiseBasedChunkGenerator
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
                NoiseChunk(
                    config,
                    object : Beardifier(emptyList(), emptyList(), null) {
                        override fun sampleValue(context: SamplerContext, x: Int, y: Int, z: Int): Float = 0.0f
                    },
                    settings,
                    createFluidLevelSampler(settings),
                    Blender.empty(),
                    DensityVolume(16, chunk.height, 16, chunk.pos.minBlockX, chunk.minY, chunk.pos.minBlockZ)
                )

            val biomeNoiseSampler = createMultiNoiseSampler(config, testSampler)
            val biomeResolver = biomeSource.createResolver(biomeNoiseSampler)
            chunk.fillBiomesFromNoise(biomeResolver)
            chunk.persistedStatus = ChunkStatus.BIOMES

            populateNoise(settings, testSampler, shape, chunk, chunkGenerator, config, levelHeightAccessor)
            chunk.persistedStatus = ChunkStatus.TERRAIN

            val biomeMixer = BiomeManager(biomeResolver, BiomeManager.obfuscateSeed(seed))
            val heightContext = WorldGenerationContext(chunkGenerator, serverLevel)
            config.surfaceSystem().buildSurface(
                config,
                biomeMixer,
                heightContext,
                chunk,
                testSampler,
                settings.materialRule().value(),
                biomeSource.possibleBiomes()
            )

            val generateCarversMethod = chunkGenerator.javaClass.declaredMethods.first { it.name == "generateCarvers" }
            generateCarversMethod.isAccessible = true
            generateCarversMethod.invoke(
                chunkGenerator,
                chunk,
                Blender.empty(),
                testSampler,
                config,
                biomeMixer,
                null,
                settings.materialRule().value()
            )

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
            val config = RandomState.create(noiseParams, seed, settings)

            removeWrappers(config, this.allowedWrappers)
            mapRouter(config.router(), WrapperValidateVisitor(this.allowedWrappers))

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
                NoiseChunk(
                    config,
                    object : Beardifier(emptyList(), emptyList(), null) {
                        override fun sampleValue(context: SamplerContext, x: Int, y: Int, z: Int): Float = 0.0f
                    },
                    settings,
                    createFluidLevelSampler(settings),
                    Blender.empty(),
                    DensityVolume(16, chunk.height, 16, chunk.pos.minBlockX, chunk.minY, chunk.pos.minBlockZ)
                )

            val data = dumpPopulateNoise(
                chunkPos.minBlockX,
                chunkPos.minBlockZ,
                testSampler,
                shape,
                settings,
                chunkGenerator,
                config,
                levelHeightAccessor
            )
            data?.forEach { state ->
                topLevelJson.add(state)
            }

            return topLevelJson
        }
    }

    internal class GridNoiseDump(
        private val filename: String = "grid_8x8_noise.json",
        private val seed: Long = 0,
        private val startChunkX: Int = 0,
        private val startChunkZ: Int = 0,
        private val sizeX: Int = 8,
        private val sizeZ: Int = 8,
        private val allowedWrappers: Iterable<String> = arrayListOf("Interpolated", "CacheOnce", "FlatCache", "Cache2D"),
        private val dimension: String = "overworld"
    ) : Extractor.Extractor {
        override fun fileName(): String = this.filename
        override fun isTest(): Boolean = true

        override fun extract(server: MinecraftServer): JsonElement {
            val topLevelJson = JsonArray()

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
            val config = RandomState.create(noiseParams, seed, settings)

            removeWrappers(config, this.allowedWrappers)
            mapRouter(config.router(), WrapperValidateVisitor(this.allowedWrappers))

            val shape = settings.noiseSettings()
            val chunkGenerator = serverLevel.chunkSource.generator
            val levelHeightAccessor = LevelHeightAccessor.create(
                chunkGenerator.minY,
                chunkGenerator.genDepth
            )

            for (cx in startChunkX until (startChunkX + sizeX)) {
                for (cz in startChunkZ until (startChunkZ + sizeZ)) {
                    val chunkPos = ChunkPos(cx, cz)
                    val chunk = createProtoChunk(
                        chunkPos,
                        UpgradeData.EMPTY,
                        levelHeightAccessor,
                        server
                    )

                    val testSampler =
                        NoiseChunk(
                            config,
                            object : Beardifier(emptyList(), emptyList(), null) {
                                override fun sampleValue(context: SamplerContext, x: Int, y: Int, z: Int): Float = 0.0f
                            },
                            settings,
                            createFluidLevelSampler(settings),
                            Blender.empty(),
                            DensityVolume(16, chunk.height, 16, chunk.pos.minBlockX, chunk.minY, chunk.pos.minBlockZ)
                        )

                    val data = dumpPopulateNoise(
                        chunkPos.minBlockX,
                        chunkPos.minBlockZ,
                        testSampler,
                        shape,
                        settings,
                        chunkGenerator,
                        config,
                        levelHeightAccessor
                    )

                    val chunkObj = JsonObject()
                    chunkObj.addProperty("x", cx)
                    chunkObj.addProperty("z", cz)
                    val blocksArr = JsonArray()
                    data?.forEach { state -> blocksArr.add(state) }
                    chunkObj.add("blocks", blocksArr)
                    topLevelJson.add(chunkObj)
                }
            }

            return topLevelJson
        }
    }

    internal class GridSurfaceDump(
        private val filename: String = "grid_8x8_surface.json",
        private val seed: Long = 0,
        private val startChunkX: Int = 0,
        private val startChunkZ: Int = 0,
        private val sizeX: Int = 8,
        private val sizeZ: Int = 8,
        private val dimension: String = "overworld"
    ) : Extractor.Extractor {
        override fun fileName(): String = this.filename
        override fun isTest(): Boolean = true

        override fun extract(server: MinecraftServer): JsonElement {
            val topLevelJson = JsonArray()

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
            val config = RandomState.create(noiseParams, seed, settings)

            val chunkGenerator = serverLevel.chunkSource.generator
            val biomeSource = chunkGenerator.biomeSource

            val shape = settings.noiseSettings()
            val levelHeightAccessor = LevelHeightAccessor.create(
                chunkGenerator.minY,
                chunkGenerator.genDepth
            )

            for (cx in startChunkX until (startChunkX + sizeX)) {
                for (cz in startChunkZ until (startChunkZ + sizeZ)) {
                    val chunkPos = ChunkPos(cx, cz)
                    val chunk = createProtoChunk(
                        chunkPos,
                        UpgradeData.EMPTY,
                        levelHeightAccessor,
                        server
                    )

                    val testSampler =
                        NoiseChunk(
                            config,
                            object : Beardifier(emptyList(), emptyList(), null) {
                                override fun sampleValue(context: SamplerContext, x: Int, y: Int, z: Int): Float = 0.0f
                            },
                            settings,
                            createFluidLevelSampler(settings),
                            Blender.empty(),
                            DensityVolume(16, chunk.height, 16, chunk.pos.minBlockX, chunk.minY, chunk.pos.minBlockZ)
                        )

                    val biomeNoiseSampler = createMultiNoiseSampler(config, testSampler)
                    val biomeResolver = biomeSource.createResolver(biomeNoiseSampler)
                    chunk.fillBiomesFromNoise(biomeResolver)
                    chunk.persistedStatus = ChunkStatus.BIOMES

                    populateNoise(settings, testSampler, shape, chunk, chunkGenerator, config, levelHeightAccessor)
                    chunk.persistedStatus = ChunkStatus.TERRAIN

                    val biomeMixer = BiomeManager(biomeResolver, BiomeManager.obfuscateSeed(seed))
                    val heightContext = WorldGenerationContext(chunkGenerator, serverLevel)
                    config.surfaceSystem().buildSurface(
                        config,
                        biomeMixer,
                        heightContext,
                        chunk,
                        testSampler,
                        settings.materialRule().value(),
                        biomeSource.possibleBiomes()
                    )
                    chunk.persistedStatus = ChunkStatus.TERRAIN

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

                    val chunkObj = JsonObject()
                    chunkObj.addProperty("x", cx)
                    chunkObj.addProperty("z", cz)
                    val blocksArr = JsonArray()
                    result.forEach { state -> blocksArr.add(state) }
                    chunkObj.add("blocks", blocksArr)
                    topLevelJson.add(chunkObj)
                }
            }

            return topLevelJson
        }
    }

    internal class GridCarverDump(
        private val filename: String = "grid_8x8_carvers.json",
        private val seed: Long = 0,
        private val startChunkX: Int = 0,
        private val startChunkZ: Int = 0,
        private val sizeX: Int = 8,
        private val sizeZ: Int = 8,
        private val dimension: String = "overworld"
    ) : Extractor.Extractor {
        override fun fileName(): String = this.filename
        override fun isTest(): Boolean = true

        override fun extract(server: MinecraftServer): JsonElement {
            val topLevelJson = JsonArray()

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
            val config = RandomState.create(noiseParams, seed, settings)

            val chunkGenerator = serverLevel.chunkSource.generator as NoiseBasedChunkGenerator
            val biomeSource = chunkGenerator.biomeSource

            val shape = settings.noiseSettings()
            val levelHeightAccessor = LevelHeightAccessor.create(
                chunkGenerator.minY,
                chunkGenerator.genDepth
            )

            val generateCarversMethod = chunkGenerator.javaClass.declaredMethods.first { it.name == "generateCarvers" }
            generateCarversMethod.isAccessible = true

            for (cx in startChunkX until (startChunkX + sizeX)) {
                for (cz in startChunkZ until (startChunkZ + sizeZ)) {
                    val chunkPos = ChunkPos(cx, cz)
                    val chunk = createProtoChunk(
                        chunkPos,
                        UpgradeData.EMPTY,
                        levelHeightAccessor,
                        server
                    )

                    val testSampler =
                        NoiseChunk(
                            config,
                            object : Beardifier(emptyList(), emptyList(), null) {
                                override fun sampleValue(context: SamplerContext, x: Int, y: Int, z: Int): Float = 0.0f
                            },
                            settings,
                            createFluidLevelSampler(settings),
                            Blender.empty(),
                            DensityVolume(16, chunk.height, 16, chunk.pos.minBlockX, chunk.minY, chunk.pos.minBlockZ)
                        )

                    val biomeNoiseSampler = createMultiNoiseSampler(config, testSampler)
                    val biomeResolver = biomeSource.createResolver(biomeNoiseSampler)
                    chunk.fillBiomesFromNoise(biomeResolver)
                    chunk.persistedStatus = ChunkStatus.BIOMES

                    populateNoise(settings, testSampler, shape, chunk, chunkGenerator, config, levelHeightAccessor)
                    chunk.persistedStatus = ChunkStatus.TERRAIN

                    val biomeMixer = BiomeManager(biomeResolver, BiomeManager.obfuscateSeed(seed))
                    val heightContext = WorldGenerationContext(chunkGenerator, serverLevel)
                    config.surfaceSystem().buildSurface(
                        config,
                        biomeMixer,
                        heightContext,
                        chunk,
                        testSampler,
                        settings.materialRule().value(),
                        biomeSource.possibleBiomes()
                    )

                    generateCarversMethod.invoke(
                        chunkGenerator,
                        chunk,
                        Blender.empty(),
                        testSampler,
                        config,
                        biomeMixer,
                        null,
                        settings.materialRule().value()
                    )

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

                    val chunkObj = JsonObject()
                    chunkObj.addProperty("x", cx)
                    chunkObj.addProperty("z", cz)
                    val blocksArr = JsonArray()
                    result.forEach { state -> blocksArr.add(state) }
                    chunkObj.add("blocks", blocksArr)
                    topLevelJson.add(chunkObj)
                }
            }

            return topLevelJson
        }
    }

    class GridWorldGenLevel(
        private val delegate: ServerLevel,
        private val chunks: Map<ChunkPos, ProtoChunk>,
        private val worldSeed: Long
    ) : WorldGenLevel by delegate, BiomeResolver {
        private val internalBiomeManager by lazy { BiomeManager(this, BiomeManager.obfuscateSeed(worldSeed)) }
        private val blockTicksAccess = WorldGenTickAccess<Block> { pos -> getChunk(pos).blockTicks }
        private val fluidTicksAccess = WorldGenTickAccess<Fluid> { pos -> getChunk(pos).fluidTicks }

        override fun getSeed(): Long = worldSeed
        override fun getLevel(): ServerLevel = delegate
        override fun getBiomeManager(): BiomeManager = internalBiomeManager
        override fun getBlockTicks(): LevelTickAccess<Block> = blockTicksAccess
        override fun getFluidTicks(): LevelTickAccess<Fluid> = fluidTicksAccess

        override fun getChunk(chunkX: Int, chunkZ: Int, targetStatus: ChunkStatus, loadOrGenerate: Boolean): ChunkAccess? {
            return chunks[ChunkPos(chunkX, chunkZ)]
        }

        override fun getChunk(chunkX: Int, chunkZ: Int): ChunkAccess {
            return chunks[ChunkPos(chunkX, chunkZ)] ?: throw IllegalArgumentException("Missing chunk ($chunkX, $chunkZ)")
        }

        override fun getChunk(pos: BlockPos): ChunkAccess {
            val cx = SectionPos.blockToSectionCoord(pos.x)
            val cz = SectionPos.blockToSectionCoord(pos.z)
            return getChunk(cx, cz)
        }

        override fun hasChunk(chunkX: Int, chunkZ: Int): Boolean {
            return chunks.containsKey(ChunkPos(chunkX, chunkZ))
        }

        override fun getBlockState(pos: BlockPos): BlockState {
            val cx = SectionPos.blockToSectionCoord(pos.x)
            val cz = SectionPos.blockToSectionCoord(pos.z)
            val chunk = chunks[ChunkPos(cx, cz)]
            return chunk?.getBlockState(pos) ?: Blocks.AIR.defaultBlockState()
        }

        override fun getFluidState(pos: BlockPos): FluidState {
            val cx = SectionPos.blockToSectionCoord(pos.x)
            val cz = SectionPos.blockToSectionCoord(pos.z)
            val chunk = chunks[ChunkPos(cx, cz)]
            return chunk?.getFluidState(pos) ?: Fluids.EMPTY.defaultFluidState()
        }

        override fun setBlock(pos: BlockPos, state: BlockState, flags: Int, updateLimit: Int): Boolean {
            val cx = SectionPos.blockToSectionCoord(pos.x)
            val cz = SectionPos.blockToSectionCoord(pos.z)
            val chunk = chunks[ChunkPos(cx, cz)] ?: return false
            chunk.setBlockState(pos, state)
            return true
        }

        override fun getHeight(type: Heightmap.Types, x: Int, z: Int): Int {
            val cx = SectionPos.blockToSectionCoord(x)
            val cz = SectionPos.blockToSectionCoord(z)
            val chunk = chunks[ChunkPos(cx, cz)]
            return chunk?.getHeight(type, x, z) ?: delegate.minY
        }

        override fun getHeightmapPos(type: Heightmap.Types, pos: BlockPos): BlockPos {
            return BlockPos(pos.x, getHeight(type, pos.x, pos.z), pos.z)
        }

        override fun getBiome(pos: BlockPos): Holder<Biome> {
            return biomeManager.getBiome(pos)
        }

        override fun getNoiseBiome(x: Int, y: Int, z: Int): Holder<Biome> {
            val cx = QuartPos.toSection(x)
            val cz = QuartPos.toSection(z)
            val chunk = chunks[ChunkPos(cx, cz)]
            return chunk?.getNoiseBiome(x, y, z) ?: delegate.getNoiseBiome(x, y, z)
        }

        override fun getBlockEntity(pos: BlockPos): BlockEntity? {
            val cx = SectionPos.blockToSectionCoord(pos.x)
            val cz = SectionPos.blockToSectionCoord(pos.z)
            val chunk = chunks[ChunkPos(cx, cz)]
            return chunk?.getBlockEntity(pos)
        }

        override fun ensureCanWrite(pos: BlockPos): Boolean = true
    }

    class GridFeaturesDump(
        private val seed: Long = 0,
        private val startChunkX: Int = 0,
        private val startChunkZ: Int = 0,
        private val sizeX: Int = 8,
        private val sizeZ: Int = 8,
        private val dimension: String = "overworld"
    ) : Extractor.Extractor {
        override fun fileName(): String {
            val dimPrefix = if (dimension == "overworld") "" else "${dimension}_"
            return "${dimPrefix}grid_${sizeX}x${sizeZ}_features.json"
        }

        override fun isTest(): Boolean = true

        override fun extract(server: MinecraftServer): JsonElement {
            val topLevelJson = JsonArray()

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
            val config = RandomState.create(noiseParams, seed, settings)

            val chunkGenerator = serverLevel.chunkSource.generator as NoiseBasedChunkGenerator
            val biomeSource = chunkGenerator.biomeSource

            val shape = settings.noiseSettings()
            val levelHeightAccessor = LevelHeightAccessor.create(
                chunkGenerator.minY,
                chunkGenerator.genDepth
            )

            val generateCarversMethod = chunkGenerator.javaClass.declaredMethods.first { it.name == "generateCarvers" }
            generateCarversMethod.isAccessible = true

            val margin = 1
            val minX = startChunkX - margin
            val maxX = startChunkX + sizeX + margin - 1
            val minZ = startChunkZ - margin
            val maxZ = startChunkZ + sizeZ + margin - 1

            val chunkMap = HashMap<ChunkPos, ProtoChunk>()

            for (cx in minX..maxX) {
                for (cz in minZ..maxZ) {
                    val chunkPos = ChunkPos(cx, cz)
                    val chunk = createProtoChunk(
                        chunkPos,
                        UpgradeData.EMPTY,
                        levelHeightAccessor,
                        server
                    )

                    val testSampler =
                        NoiseChunk(
                            config,
                            object : Beardifier(emptyList(), emptyList(), null) {
                                override fun sampleValue(context: SamplerContext, x: Int, y: Int, z: Int): Float = 0.0f
                            },
                            settings,
                            createFluidLevelSampler(settings),
                            Blender.empty(),
                            DensityVolume(16, chunk.height, 16, chunk.pos.minBlockX, chunk.minY, chunk.pos.minBlockZ)
                        )

                    val biomeNoiseSampler = createMultiNoiseSampler(config, testSampler)
                    val biomeResolver = biomeSource.createResolver(biomeNoiseSampler)
                    chunk.fillBiomesFromNoise(biomeResolver)
                    chunk.persistedStatus = ChunkStatus.BIOMES

                    populateNoise(settings, testSampler, shape, chunk, chunkGenerator, config, levelHeightAccessor)
                    chunk.persistedStatus = ChunkStatus.TERRAIN

                    val biomeMixer = BiomeManager(biomeResolver, BiomeManager.obfuscateSeed(seed))
                    val heightContext = WorldGenerationContext(chunkGenerator, serverLevel)
                    config.surfaceSystem().buildSurface(
                        config,
                        biomeMixer,
                        heightContext,
                        chunk,
                        testSampler,
                        settings.materialRule().value(),
                        biomeSource.possibleBiomes()
                    )

                    generateCarversMethod.invoke(
                        chunkGenerator,
                        chunk,
                        Blender.empty(),
                        testSampler,
                        config,
                        biomeMixer,
                        null,
                        settings.materialRule().value()
                    )

                    Heightmap.primeHeightmaps(chunk, ChunkStatus.FINAL_HEIGHTMAPS)
                    chunkMap[chunkPos] = chunk
                }
            }

            val worldGenLevel = GridWorldGenLevel(serverLevel, chunkMap, seed)
            val structureCheckField = StructureManager::class.java.declaredFields.first { it.type == StructureCheck::class.java }
            structureCheckField.isAccessible = true
            val structureCheck = structureCheckField.get(serverLevel.structureManager()) as StructureCheck
            val structureManager = object : StructureManager(
                worldGenLevel,
                WorldOptions(seed, false, false),
                structureCheck
            ) {
                override fun shouldGenerateStructures(): Boolean = false
                override fun startsForStructure(sectionX: Int, sectionZ: Int, matcher: Predicate<Structure>): List<StructureStart> = emptyList()
            }

            for (cx in startChunkX until (startChunkX + sizeX)) {
                for (cz in startChunkZ until (startChunkZ + sizeZ)) {
                    val chunk = chunkMap[ChunkPos(cx, cz)]!!
                    chunk.persistedStatus = ChunkStatus.FEATURES
                    chunkGenerator.applyBiomeDecoration(worldGenLevel, chunk, structureManager)
                }
            }

            for (cx in startChunkX until (startChunkX + sizeX)) {
                for (cz in startChunkZ until (startChunkZ + sizeZ)) {
                    val chunk = chunkMap[ChunkPos(cx, cz)]!!
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

                    val chunkObj = JsonObject()
                    chunkObj.addProperty("x", cx)
                    chunkObj.addProperty("z", cz)
                    val blocksArr = JsonArray()
                    result.forEach { state -> blocksArr.add(state) }
                    chunkObj.add("blocks", blocksArr)
                    topLevelJson.add(chunkObj)
                }
            }

            return topLevelJson
        }
    }

    class GridStructuresDump(
        private val seed: Long = 0,
        private val startChunkX: Int = 0,
        private val startChunkZ: Int = 0,
        private val sizeX: Int = 8,
        private val sizeZ: Int = 8,
        private val dimension: String = "overworld"
    ) : Extractor.Extractor {
        override fun fileName(): String {
            return "grid_${sizeX}x${sizeZ}_structures.json"
        }

        override fun isTest(): Boolean = true

        override fun extract(server: MinecraftServer): JsonElement {
            val topLevelJson = JsonArray()

            val serverLevel = when (dimension) {
                "nether" -> server.getLevel(Level.NETHER)!!
                "end" -> server.getLevel(Level.END)!!
                else -> server.overworld()
            }

            val chunkGenerator = serverLevel.chunkSource.generator as NoiseBasedChunkGenerator
            val levelHeightAccessor = LevelHeightAccessor.create(
                chunkGenerator.minY,
                chunkGenerator.genDepth
            )
            val dimKey = when (dimension) {
                "nether" -> NoiseGeneratorSettings.NETHER
                "end" -> NoiseGeneratorSettings.END
                else -> NoiseGeneratorSettings.OVERWORLD
            }
            val ref = server.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(dimKey)
            val settings = ref.value()
            val noiseParams = server.registryAccess().lookupOrThrow(Registries.NOISE)
            val config = RandomState.create(noiseParams, seed, settings)
            val structureSets = server.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
            val generatorState = ChunkGeneratorStructureState.createForNormal(
                config,
                seed,
                ChunkPos(0, 0),
                chunkGenerator.biomeSource,
                structureSets
            )
            generatorState.ensureStructuresGenerated()
            val structureManager = serverLevel.structureManager()
            val structureTemplateManager = server.structureTemplateManager
            val structuresRegistry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE)

            val margin = 8
            val minX = startChunkX - margin
            val maxX = startChunkX + sizeX + margin - 1
            val minZ = startChunkZ - margin
            val maxZ = startChunkZ + sizeZ + margin - 1

            val chunkMap = HashMap<ChunkPos, ProtoChunk>()

            for (cx in minX..maxX) {
                for (cz in minZ..maxZ) {
                    val chunkPos = ChunkPos(cx, cz)
                    val chunk = createProtoChunk(
                        chunkPos,
                        UpgradeData.EMPTY,
                        levelHeightAccessor,
                        server
                    )

                    chunkGenerator.createStructures(
                        server.registryAccess(),
                        generatorState,
                        structureManager,
                        chunk,
                        structureTemplateManager,
                        serverLevel.dimension()
                    )
                    chunk.persistedStatus = ChunkStatus.STRUCTURE_STARTS
                    chunkMap[chunkPos] = chunk
                }
            }

            val worldGenLevel = GridWorldGenLevel(serverLevel, chunkMap, seed)

            for (cx in startChunkX until (startChunkX + sizeX)) {
                for (cz in startChunkZ until (startChunkZ + sizeZ)) {
                    val chunk = chunkMap[ChunkPos(cx, cz)]!!
                    chunkGenerator.createReferences(worldGenLevel, structureManager, chunk)
                    chunk.persistedStatus = ChunkStatus.STRUCTURE_REFERENCES

                    val chunkObj = JsonObject()
                    chunkObj.addProperty("x", cx)
                    chunkObj.addProperty("z", cz)

                    val startsArr = JsonArray()
                    for ((struct, start) in chunk.allStarts) {
                        if (start.isValid) {
                            val startObj = JsonObject()
                            val structKey = structuresRegistry.getKey(struct)?.toString() ?: "unknown"
                            startObj.addProperty("id", structKey)
                            startObj.addProperty("chunk_x", start.chunkPos.x)
                            startObj.addProperty("chunk_z", start.chunkPos.z)
                            val bb = start.boundingBox
                            val bbArr = JsonArray()
                            bbArr.add(bb.minX())
                            bbArr.add(bb.minY())
                            bbArr.add(bb.minZ())
                            bbArr.add(bb.maxX())
                            bbArr.add(bb.maxY())
                            bbArr.add(bb.maxZ())
                            startObj.add("bb", bbArr)

                            val piecesArr = JsonArray()
                            for (piece in start.pieces) {
                                val pieceObj = JsonObject()
                                val pBb = piece.boundingBox
                                val pBbArr = JsonArray()
                                pBbArr.add(pBb.minX())
                                pBbArr.add(pBb.minY())
                                pBbArr.add(pBb.minZ())
                                pBbArr.add(pBb.maxX())
                                pBbArr.add(pBb.maxY())
                                pBbArr.add(pBb.maxZ())
                                pieceObj.add("bb", pBbArr)
                                val pieceType = BuiltInRegistries.STRUCTURE_PIECE.getKey(piece.type)?.toString() ?: "unknown"
                                pieceObj.addProperty("type", pieceType)
                                piecesArr.add(pieceObj)
                            }
                            startObj.add("pieces", piecesArr)
                            startsArr.add(startObj)
                        }
                    }
                    chunkObj.add("starts", startsArr)

                    val refsArr = JsonArray()
                    for ((struct, refSet) in chunk.allReferences) {
                        val refObj = JsonObject()
                        val structKey = structuresRegistry.getKey(struct)?.toString() ?: "unknown"
                        refObj.addProperty("id", structKey)
                        val coordsArr = JsonArray()
                        for (packed in refSet) {
                            val refCoord = JsonObject()
                            refCoord.addProperty("chunk_x", ChunkPos.getX(packed))
                            refCoord.addProperty("chunk_z", ChunkPos.getZ(packed))
                            coordsArr.add(refCoord)
                        }
                        refObj.add("chunks", coordsArr)
                        refsArr.add(refObj)
                    }
                    chunkObj.add("references", refsArr)

                    topLevelJson.add(chunkObj)
                }
            }

            return topLevelJson
        }
    }

    class AreaStructuresDump(
        private val seed: Long = 0,
        private val minChunkX: Int = -16,
        private val maxChunkX: Int = 16,
        private val minChunkZ: Int = -16,
        private val maxChunkZ: Int = 16,
        private val dimension: String = "overworld"
    ) : Extractor.Extractor {
        override fun fileName(): String {
            return "area_structures.json"
        }

        override fun isTest(): Boolean = true

        override fun extract(server: MinecraftServer): JsonElement {
            val topLevelJson = JsonArray()

            val serverLevel = when (dimension) {
                "nether" -> server.getLevel(Level.NETHER)!!
                "end" -> server.getLevel(Level.END)!!
                else -> server.overworld()
            }

            val chunkGenerator = serverLevel.chunkSource.generator as NoiseBasedChunkGenerator
            val levelHeightAccessor = LevelHeightAccessor.create(
                chunkGenerator.minY,
                chunkGenerator.genDepth
            )
            val dimKey = when (dimension) {
                "nether" -> NoiseGeneratorSettings.NETHER
                "end" -> NoiseGeneratorSettings.END
                else -> NoiseGeneratorSettings.OVERWORLD
            }
            val ref = server.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(dimKey)
            val settings = ref.value()
            val noiseParams = server.registryAccess().lookupOrThrow(Registries.NOISE)
            val config = RandomState.create(noiseParams, seed, settings)
            val structureSets = server.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
            val generatorState = ChunkGeneratorStructureState.createForNormal(
                config,
                seed,
                ChunkPos(0, 0),
                chunkGenerator.biomeSource,
                structureSets
            )
            generatorState.ensureStructuresGenerated()
            val structureManager = serverLevel.structureManager()
            val structureTemplateManager = server.structureTemplateManager
            val structuresRegistry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE)

            for (cx in minChunkX..maxChunkX) {
                for (cz in minChunkZ..maxChunkZ) {
                    val chunkPos = ChunkPos(cx, cz)
                    val chunk = createProtoChunk(
                        chunkPos,
                        UpgradeData.EMPTY,
                        levelHeightAccessor,
                        server
                    )

                    chunkGenerator.createStructures(
                        server.registryAccess(),
                        generatorState,
                        structureManager,
                        chunk,
                        structureTemplateManager,
                        serverLevel.dimension()
                    )

                    val validStarts = chunk.allStarts.values.filter { it.isValid }
                    if (validStarts.isNotEmpty()) {
                        val chunkObj = JsonObject()
                        chunkObj.addProperty("chunk_x", cx)
                        chunkObj.addProperty("chunk_z", cz)

                        val startsArr = JsonArray()
                        for (start in validStarts) {
                            val startObj = JsonObject()
                            val structKey = structuresRegistry.getKey(start.structure)?.toString() ?: "unknown"
                            startObj.addProperty("id", structKey)
                            startObj.addProperty("chunk_x", start.chunkPos.x)
                            startObj.addProperty("chunk_z", start.chunkPos.z)
                            val bb = start.boundingBox
                            val bbArr = JsonArray()
                            bbArr.add(bb.minX())
                            bbArr.add(bb.minY())
                            bbArr.add(bb.minZ())
                            bbArr.add(bb.maxX())
                            bbArr.add(bb.maxY())
                            bbArr.add(bb.maxZ())
                            startObj.add("bb", bbArr)

                            val piecesArr = JsonArray()
                            for (piece in start.pieces) {
                                val pieceObj = JsonObject()
                                val pBb = piece.boundingBox
                                val pBbArr = JsonArray()
                                pBbArr.add(pBb.minX())
                                pBbArr.add(pBb.minY())
                                pBbArr.add(pBb.minZ())
                                pBbArr.add(pBb.maxX())
                                pBbArr.add(pBb.maxY())
                                pBbArr.add(pBb.maxZ())
                                pieceObj.add("bb", pBbArr)
                                val pieceType = BuiltInRegistries.STRUCTURE_PIECE.getKey(piece.type)?.toString() ?: "unknown"
                                pieceObj.addProperty("type", pieceType)
                                piecesArr.add(pieceObj)
                            }
                            startObj.add("pieces", piecesArr)
                            startsArr.add(startObj)
                        }
                        chunkObj.add("starts", startsArr)
                        topLevelJson.add(chunkObj)
                    }
                }
            }

            return topLevelJson
        }
    }
}
