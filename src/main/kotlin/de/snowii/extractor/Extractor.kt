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
            WorldEvent(),
            ScoreboardDisplaySlot(),
            Particles(),
            EntityAttributes(),
            ChunkStatus(),
            MessageType(),
            SoundCategory(),
            EntityPose(),
            GameEvent(),
            SyncedRegistries(),
            Packets(),
            Screens(),
            Tags(),
            NoiseParameters(),
            Entities(),
            Items(),
            Blocks(),
            Tests(),
            MultiNoise(),
            Translations(),
            DamageTypes(),
        )

        val outputDirectory: Path
        try {
            outputDirectory = Files.createDirectories(Paths.get("pumpkin_extractor_output"))
        } catch (e: IOException) {
            logger.info("Failed to create output directory.", e)
            return
        }

        val gson = GsonBuilder().disableHtmlEscaping().create()

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
