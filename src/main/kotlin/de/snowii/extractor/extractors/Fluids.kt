package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.IExtractor
import net.minecraft.block.Block
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer

class Fluids : IExtractor {
    override fun fileName(): String {
        return "fluids.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val topLevelJson = JsonArray()

        for (fluid in Registries.FLUID) {
            val fluidJson = JsonObject()
            fluidJson.addProperty("id", Registries.FLUID.getRawId(fluid))
            fluidJson.addProperty("name", Registries.FLUID.getId(fluid).path)

            val propsJson = JsonArray()
            for (prop in fluid.stateManager.properties) {
                val propJson = JsonObject()

                propJson.addProperty("name", prop.name)

                val valuesJson = JsonArray()
                for (value in prop.values) {
                    valuesJson.add(value.toString().lowercase())
                }
                propJson.add("values", valuesJson)

                propsJson.add(propJson)
            }
            fluidJson.add("properties", propsJson)

            val statesJson = JsonArray()
            for ((index, state) in fluid.stateManager.states.withIndex()) {
                val stateJson = JsonObject()
                stateJson.addProperty("height", state.height)
                stateJson.addProperty("level", state.level)
                stateJson.addProperty("is_empty", state.isEmpty)
                stateJson.addProperty("blast_resistance", state.blastResistance)
                stateJson.addProperty("block_state_id", Block.getRawIdFromState(state.blockState))
                stateJson.addProperty("is_still", state.isStill)
                // TODO: Particle effects

                if (fluid.defaultState == state) {
                    fluidJson.addProperty("default_state_index", index)
                }

                statesJson.add(stateJson)
            }
            fluidJson.add("states", statesJson)

            topLevelJson.add(fluidJson)
        }

        return topLevelJson
    }
}