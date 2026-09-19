package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.server.MinecraftServer
import net.minecraft.util.StringRepresentable
import net.minecraft.world.level.block.state.properties.*
import java.lang.reflect.Modifier

class Properties : Extractor.Extractor {
    override fun fileName(): String {
        return "properties.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val topLevelJson = JsonArray()

        // CRITICAL: You want BlockStateProperties, not Item.Properties
        val propertiesClass = BlockStateProperties::class.java

        for (field in propertiesClass.declaredFields) {
            // Only grab "public static final Property" fields
            if (Modifier.isStatic(field.modifiers) && Property::class.java.isAssignableFrom(field.type)) {

                field.isAccessible = true // Fixes the IllegalAccessException
                val maybeProperty = field.get(null) as? Property<*> ?: continue

                val propertyJson = JsonObject()

                // Metadata for mapping
                propertyJson.addProperty("hash_key", PropertyKey.of(maybeProperty))
                propertyJson.addProperty("enum_name", field.name.lowercase())
                propertyJson.addProperty("serialized_name", maybeProperty.name)

                when (maybeProperty) {
                    is BooleanProperty -> {
                        propertyJson.addProperty("type", "boolean")
                    }

                    is IntegerProperty -> {
                        propertyJson.addProperty("type", "int")

                        // Reflection to get min/max from IntegerProperty
                        // In 1.21.4, these might be named 'min'/'max' or 'f_61623_'/'f_61624_'
                        val minField = IntegerProperty::class.java.declaredFields.find { it.name == "min" || it.name.contains("min") }
                        val maxField = IntegerProperty::class.java.declaredFields.find { it.name == "max" || it.name.contains("max") }

                        minField?.isAccessible = true
                        maxField?.isAccessible = true

                        propertyJson.addProperty("min", minField?.get(maybeProperty) as Int)
                        propertyJson.addProperty("max", maxField?.get(maybeProperty) as Int)
                    }

                    is EnumProperty<*> -> {
                        propertyJson.addProperty("type", "enum")
                        val enumArr = JsonArray()

                        // 1. Cast to a type that implements the required 1.21.4 interfaces
                        @Suppress("UNCHECKED_CAST")
                        val typedProp = maybeProperty as EnumProperty<out StringRepresentable>

                        for (value in typedProp.possibleValues) {
                            if (value is StringRepresentable) {
                                enumArr.add(value.serializedName.lowercase())
                            } else {
                                // Fallback for non-enum StringRepresentables if any exist
                                enumArr.add(value.toString().lowercase())
                            }
                        }
                        propertyJson.add("values", enumArr)
                    }

                    else -> {
                        // For DirectionProperty or other specialized types
                        propertyJson.addProperty("type", "other")
                    }
                }

                topLevelJson.add(propertyJson)
            }
        }
        return topLevelJson
    }

    private fun <T> getNameUnsafe(property: EnumProperty<T>, value: Any): String
            where T : Enum<T>, T : StringRepresentable {
        @Suppress("UNCHECKED_CAST")
        return property.getName(value as T)
    }
}