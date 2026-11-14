package com.windykindy49.windyorigins.mixin;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Water that vanishes for a moment beside freshly frozen ice.
 *
 * The game re-sorts a chunk section's translucent faces on a worker thread, from the mesh the
 * section had when the sort was asked for. When the section is rebuilt in the meantime (and a
 * Frostborn walking on water has it rebuilt every few ticks), that order can arrive after the
 * new mesh and is then drawn against it. Ice has more faces than the water it replaced, so the
 * old order is too short and the last faces of the section, a strip of water along its edge,
 * are not drawn until the next sort.
 *
 * An order made for a mesh of another size is simply not taken: the one that came with the mesh
 * stays in use.
 */
@Mixin(VertexBuffer.class)
public abstract class TranslucentSortMixin {
    /** How many vertices the mesh in this buffer has; -1 until one has been uploaded. */
    @Unique
    private int windyorigins$vertices = -1;

    @Inject(method = "upload", at = @At("HEAD"), cancellable = true, require = 0)
    private void windyorigins$dropStaleOrder(BufferBuilder.BuiltBuffer buffer, CallbackInfo ci) {
        BufferBuilder.DrawParameters parameters = buffer.getParameters();
        if (!parameters.indexOnly()) {
            this.windyorigins$vertices = parameters.vertexCount();
        } else if (this.windyorigins$vertices >= 0 && parameters.vertexCount() != this.windyorigins$vertices) {
            buffer.release();
            ci.cancel();
        }
    }
}
