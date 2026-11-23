package com.modularmc.mmclutils.client.render.effect;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record RingStyle(float spriteScale, int spriteStrength, float hazeWidth, int hazeStrength, float coreWidth, int coreStrength) {}
