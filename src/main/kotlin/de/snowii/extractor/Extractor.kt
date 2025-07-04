package de.snowii.extractor

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import de.snowii.extractor.extractors.*
import de.snowii.extractor.extractors.non_registry.*
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.FileWriter
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.measureTimeMillis


class Extractor : ModInitializer {
    private val modID: String = "pumpkin_extractor"
    private val logger: Logger = LoggerFactory.getLogger(modID)

    override fun onInitialize() {
        logger.info("Starting Pumpkin Extractor")

        val extractors = arrayOf(
            Sounds(),
            Recipes(),
            Biome(),
            BiomeMixerTest(),
            WorldEvent(),
            Carver(),
            Enchantments(),
            ScoreboardDisplaySlot(),
            Particles(),
            EntityAttributes(),
            ChunkStatus(),
            EntityStatuses(),
            MessageType(),
            StatusEffects(),
            SoundCategory(),
            EntityPose(),
            GameEvent(),
            GameRules(),
            SpawnEgg(),
            SyncedRegistries(),
            ChunkGenSetting(),
            Packets(),
            Screens(),
            PlacedFeatures(),
            Tags(),
            NoiseParameters(),
            Entities(),
            ConfiguredFeatures(),
            Items(),
            Blocks(),
            MultiNoise(),
            MultiNoise().Sample(),
            ChunkGenSetting(),
            Translations(),
            DensityFunctions(),
            DensityFunctions().Tests(),
            DamageTypes(),
            Fluids(),
            Properties(),
            ComposterIncreaseChance(),
            FlowerPotTransformation(),
            Fuels(),
            ChunkDumpTests.NoiseDump(
                "no_blend_no_beard_0_0.chunk",
                0,
                0,
                0,
                arrayListOf("Interpolated", "CacheOnce", "FlatCache", "Cache2D")
            ),
            ChunkDumpTests.NoiseDump(
                "no_blend_no_beard_7_4.chunk",
                0,
                7,
                4,
                arrayListOf("Interpolated", "CacheOnce", "FlatCache", "Cache2D")
            ),
            ChunkDumpTests.NoiseDump(
                "no_blend_no_beard_only_cell_cache_0_0.chunk",
                0,
                0,
                0,
                ArrayList()
            ),
            ChunkDumpTests.NoiseDump(
                "no_blend_no_beard_only_cell_cache_flat_cache_0_0.chunk",
                0,
                0,
                0,
                arrayListOf("FlatCache")
            ),
            ChunkDumpTests.NoiseDump(
                "no_blend_no_beard_only_cell_cache_interpolated_0_0.chunk",
                0,
                0,
                0,
                arrayListOf("Interpolated")
            ),
            ChunkDumpTests.NoiseDump(
                "no_blend_no_beard_only_cell_cache_once_cache_0_0.chunk",
                0,
                0,
                0,
                arrayListOf("CacheOnce")
            ),
            ChunkDumpTests.NoiseDump(
                "no_blend_no_beard_-595_544.chunk",
                0,
                -595,
                544,
                arrayListOf("Interpolated", "CacheOnce", "FlatCache", "Cache2D")
            ),
            ChunkDumpTests.NoiseDump(
                "no_blend_no_beard_-119_183.chunk",
                0,
                -119,
                183,
                arrayListOf("Interpolated", "CacheOnce", "FlatCache", "Cache2D")
            ),
            ChunkDumpTests.NoiseDump(
                "no_blend_no_beard_13579_-6_11.chunk",
                13579,
                -6,
                11,
                arrayListOf("Interpolated", "CacheOnce", "FlatCache", "Cache2D")
            ),
            ChunkDumpTests.NoiseDump(
                "no_blend_no_beard_13579_-2_15.chunk",
                13579,
                -2,
                15,
                arrayListOf("Interpolated", "CacheOnce", "FlatCache", "Cache2D")
            ),
            BiomeDumpTests(),
            BiomeDumpTests().MultiNoiseBiomeSourceTest(),
            ChunkDumpTests.SurfaceDump("no_blend_no_beard_surface_0_0.chunk", 0, 0, 0),
            ChunkDumpTests.SurfaceDump("no_blend_no_beard_surface_badlands_-595_544.chunk", 0, -595, 544),
            ChunkDumpTests.SurfaceDump("no_blend_no_beard_surface_frozen_ocean_-119_183.chunk", 0, -119, 183),
            ChunkDumpTests.SurfaceDump("no_blend_no_beard_surface_13579_-6_11.chunk", 13579, -6, 11),
            ChunkDumpTests.SurfaceDump("no_blend_no_beard_surface_13579_-2_15.chunk", 13579, -2, 15),
            ChunkDumpTests.SurfaceDump("no_blend_no_beard_surface_13579_-7_9.chunk", 13579, -7, 9)
        )

        val outputDirectory: Path
        try {
            outputDirectory = Files.createDirectories(Paths.get("pumpkin_extractor_output"))
        } catch (e: IOException) {
            logger.info("Failed to create output directory.", e)
            return
        }

        val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

        ServerLifecycleEvents.SERVER_STARTED.register(ServerLifecycleEvents.ServerStarted { server: MinecraftServer ->
            val timeInMillis = measureTimeMillis {
                for (ext in extractors) {
                    try {
                        val out = outputDirectory.resolve(ext.fileName())
                        val fileWriter = FileWriter(out.toFile(), StandardCharsets.UTF_8)
                        gson.toJson(ext.extract(server), fileWriter)
                        fileWriter.close()
                        logger.info("Wrote " + out.toAbsolutePath())
                    } catch (e: java.lang.Exception) {
                        logger.error(("Extractor for \"" + ext.fileName()) + "\" failed.", e)
                    }
                }
            }
            logger.info("Done, took ${timeInMillis}ms")
        })
    }

    interface Extractor {
        fun fileName(): String

        @Throws(Exception::class)
        fun extract(server: MinecraftServer): JsonElement
    }
}
