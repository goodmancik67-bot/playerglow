package com.example.playerglow.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/** The old vanilla glow outline was removed (ESP is now a 3D box drawn in PlayerGlowClient). */
@Mixin(Entity.class)
public abstract class EntityMixin {
}
