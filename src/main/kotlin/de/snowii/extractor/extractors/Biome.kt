package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import java.lang.reflect.Field

class Biome : Extractor.Extractor {
    override fun fileName(): String {
        return "biome.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val biomesJson = JsonArray()
        val biomeRegistry =
            server.registryManager.getOrThrow(RegistryKeys.BIOME)
        for (biome in biomeRegistry) {
            val biomeData = JsonObject()
            biomeData.addProperty("name", biomeRegistry.getId(biome)!!.path)
            biomeData.addProperty("id", biomeRegistry.getRawId(biome))

            var weather: Field? = null;
            for (f: Field in biome.javaClass.fields) {
                if (f.name == "weather") {
                    f.trySetAccessible()
                    weather = f
                    break
                }
            }

            // The weather class is also private :(
            val weatherValue = weather!!.get(biome) as Any
            val weatherData = JsonObject()
            for (f: Field in weatherValue.javaClass.declaredFields) {
                f.trySetAccessible()
                when (f.name) {
                    "hasPrecipitation" -> weatherData.addProperty("has_precipitation", f.get(weatherValue) as Boolean)
                    "temperature" -> weatherData.addProperty("temperature", f.get(weatherValue) as Float)
                    "temperatureModifier" -> weatherData.addProperty("temperature_modifier", f.get(weatherValue).toString())
                    "downfall" -> weatherData.addProperty("downfall", f.get(weatherValue) as Float)
                    else -> {}
                }
            }
            biomeData.add("weather", weatherData)

            biomesJson.add(biomeData)
        }

        return biomesJson
    }
}