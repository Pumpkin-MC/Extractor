package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import de.snowii.extractor.IExtractor
import net.minecraft.entity.EntityPose
import net.minecraft.server.MinecraftServer

class EntityPose : IExtractor {
    override fun fileName(): String {
        return "entity_pose.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val poseesJson = JsonArray()
        for (pose in EntityPose.entries) {
            poseesJson.add(
                pose.name,
            )
        }

        return poseesJson
    }
}