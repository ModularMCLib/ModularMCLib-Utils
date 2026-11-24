package com.modularmc.mmclutils.client.render.effect;

import org.jspecify.annotations.Nullable;

public record PulseStyle(float speed, float lag, int trail, float trailStep, float trailLife, float size, float alpha, @Nullable BurstStyle arrival) {}
