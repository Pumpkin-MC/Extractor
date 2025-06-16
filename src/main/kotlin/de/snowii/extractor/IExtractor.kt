package de.snowii.extractor

import com.google.gson.JsonElement
import net.minecraft.server.MinecraftServer

interface IExtractor {
    fun fileName(): String

    @Throws(Exception::class)
    fun extract(server: MinecraftServer): JsonElement
}
