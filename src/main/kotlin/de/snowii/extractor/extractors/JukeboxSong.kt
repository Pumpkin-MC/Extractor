package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer

class JukeboxSong : Extractor.Extractor {
    override fun fileName(): String {
        return "jukebox_song.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val finalJson = JsonObject()
        val registry =
            server.registryManager.getOrThrow(RegistryKeys.JUKEBOX_SONG)
        for (setting in registry) {
            finalJson.addProperty(
                registry.getId(setting)!!.path,
                registry.getRawId(setting)
            )
        }

        return finalJson
    }
}