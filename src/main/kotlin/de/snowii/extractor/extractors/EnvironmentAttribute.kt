package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.attribute.EnvironmentAttributes

class EnvironmentAttribute : Extractor.Extractor {
    override fun fileName(): String {
        return "environement_attribute.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val environnementAttribute = JsonObject()
        val ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE)
        val registry =
            server.registryAccess().lookupOrThrow(Registries.ENVIRONMENT_ATTRIBUTE)
        for (attribute in registry) {
            val sub = EnvironmentAttributes.CODEC
                .encodeStart(ops,attribute)
                .getOrThrow() as JsonObject;
            environnementAttribute.add(
                registry.getKey(attribute)!!.path,
                sub
            )
        }
        return environnementAttribute
    }
}