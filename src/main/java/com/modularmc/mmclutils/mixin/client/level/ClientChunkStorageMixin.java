package com.modularmc.mmclutils.mixin.client.level;

import com.modularmc.mmclutils.client.level.ClientChunkVisibility;

import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.concurrent.atomic.AtomicReferenceArray;

@Mixin(targets = "net.minecraft.client.multiplayer.ClientChunkCache$Storage")
public abstract class ClientChunkStorageMixin implements ClientChunkVisibility.View {

    @Shadow
    @Final
    private AtomicReferenceArray<LevelChunk> chunks;

    @Invoker("inRange")
    protected abstract boolean mmclutils$contains(int x, int z);

    @Override
    public AtomicReferenceArray<LevelChunk> mmclutils$chunks() {
        return chunks;
    }

    @Override
    public boolean mmclutils$inRange(int x, int z) {
        return mmclutils$contains(x, z);
    }
}
