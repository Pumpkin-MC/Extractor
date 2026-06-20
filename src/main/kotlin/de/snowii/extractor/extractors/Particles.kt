package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import java.lang.reflect.ParameterizedType


class Particles : Extractor.Extractor {
    override fun fileName(): String {
        return "particles.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val particlesJson = JsonObject()

        for (particle in BuiltInRegistries.PARTICLE_TYPE) {
            val fields = JsonArray()

            // Extract the fields of the particle type, using the class of the particle type
            // This is done using the constructor of the particle type and reading the parameters
            // SimpleParticleType does not have any fields, so we skip particles of this type
            if (particle !is SimpleParticleType) {
                val id = BuiltInRegistries.PARTICLE_TYPE.getKey(particle)!!.path
                val particleType = ParticleTypes::class.java.getDeclaredField(id.uppercase()).genericType

                if (particleType is ParameterizedType) {
                    // The registry items are ParticleType<T>, we need to get the type T using reflection
                    val parameterizedType = particleType as ParameterizedType?
                    val typeArguments = parameterizedType!!.actualTypeArguments
                    val typeArg = typeArguments[0]

                    if (typeArg is Class<*>) {
                        // Based on the type T, we convert it back to a class and get the parameters using reflection
                        val typeClass = typeArg as Class<*>?

                        for (field in typeClass?.constructors?.first()?.parameters!!) {
                            val name = field.name

                            // Parameter 'type' is in every constructor, we do not need it for our purposes
                            if (name != "type") {
                                fields.add(name)
                            }
                        }
                    }
                }
            }

            particlesJson.add(
                BuiltInRegistries.PARTICLE_TYPE.getKey(particle)!!.path, fields
            )
        }

        return particlesJson
    }
}
