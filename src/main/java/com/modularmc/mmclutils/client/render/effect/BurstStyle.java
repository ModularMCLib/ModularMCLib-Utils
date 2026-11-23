package com.modularmc.mmclutils.client.render.effect;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record BurstStyle(int count, int life, float radius, float spreadXZ, float spreadY, float rise, float size, float alpha) {}
