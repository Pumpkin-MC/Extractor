package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.server.MinecraftServer
import net.minecraft.state.property.BooleanProperty
import net.minecraft.state.property.EnumProperty
import net.minecraft.state.property.IntProperty
import net.minecraft.state.property.Property
import java.lang.reflect.Modifier

class Properties : Extractor.Extractor {
    override fun fileName(): String {
        return "properties.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val topLevelJson = JsonArray()

        for (field in net.minecraft.state.property.Properties::class.java.declaredFields) {
            if (Modifier.isStatic(field.modifiers)) {
                val maybeProperty = field.get(null)
                if (maybeProperty is Property<*>) {
                    val property = JsonObject()
                    // The key used by Blocks.json to map to a property
                    property.addProperty("hash_key", maybeProperty.hashCode())
                    // The unique enum name
                    property.addProperty("enum_name", field.name.lowercase())
                    // What the enum is serialized as, may overlap with others
                    property.addProperty("serialized_name", maybeProperty.name.lowercase())

                    when (maybeProperty) {
                        is BooleanProperty -> {
                            property.addProperty("type", "boolean")
                        }

                        is IntProperty -> {
                            var min: Int? = null
                            var max: Int? = null
                            for (intField in IntProperty::class.java.declaredFields) {
                                intField.trySetAccessible()
                                if (intField.name == "min") {
                                    min = intField.get(maybeProperty) as Int
                                } else if (intField.name == "max") {
                                    max = intField.get(maybeProperty) as Int
                                }
                            }
                            property.addProperty("type", "int")
                            property.addProperty("min", min!!)
                            property.addProperty("max", max!!)
                        }

                        is EnumProperty<*> -> {
                            property.addProperty("type", "enum")
                            val enumArr = JsonArray()
                            for (value in maybeProperty.values) {
                                enumArr.add(value.toString().lowercase())
                            }
                            property.add("values", enumArr)
                        }

                        else -> throw Exception("Unhandled property type: " + maybeProperty.javaClass.toString())
                    }

                    topLevelJson.add(property)
                }
            }
        }
        return topLevelJson
    }
}