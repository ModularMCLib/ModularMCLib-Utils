package com.modularmc.mmclutils.client.render.effect;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public record PulseStyle(float speed, float lag, int trail, float trailStep, float trailLife, float size, float alpha, @Nullable BurstStyle arrival) {}
