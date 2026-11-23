package com.modularmc.mmclutils.client.render.effect;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record FlowStyle(int period, int travel, float size, float sizeJitter, float jitter, float alpha) {}
