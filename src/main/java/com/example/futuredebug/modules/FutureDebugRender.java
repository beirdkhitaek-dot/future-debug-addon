package com.example.futuredebug.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

public class FutureDebugRender extends FutureDebugBase {
    private static final int[][] SHAPES = {
        {5,3},{3,3},{3,3},{4,1},{5,5},{3,5},{6,2},
        {1,3},{5,2},{5,2},{1,2},{1,6},{4,4},{1,9}
    };

    public FutureDebugRender() {
        super("future-debug-render", "Future-style debug renderer using the same scanner logic.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        commonTick();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        Color color = fillColor.get();
        int alpha = fillAlpha.get();
        ChunkPos playerChunk = mc.player.chunkPosition();
        int renderedMarkers = 0;

        for (var entry : confirmedChunks.entrySet()) {
            if (renderedMarkers >= MAX_RENDERED_MARKERS) break;
            long packed = entry.getKey();
            long start = entry.getValue();
            long age = System.currentTimeMillis() - start;
            float fade = Math.min(1f, Math.max(0f, age / 1000f));
            float eased = 1f - (1f - fade) * (1f - fade) * (1f - fade);

            int[] shape = SHAPES[Math.floorMod((int)(packed ^ (packed >>> 32)), SHAPES.length)];
            int cx = ChunkPos.getX(packed);
            int cz = ChunkPos.getZ(packed);
            if (Math.abs(cx - playerChunk.x) > MAX_RENDER_DISTANCE_CHUNKS ||
                Math.abs(cz - playerChunk.z) > MAX_RENDER_DISTANCE_CHUNKS) continue;

            double x = cx * 16.0;
            double z = cz * 16.0;
            double w = shape[0] * 16.0;
            double d = shape[1] * 16.0;

            Color c = new Color(color.r, color.g, color.b,
                Math.max(0, Math.min(255, Math.round(alpha * eased))));

            event.renderer.box(
                new AABB(x, RENDER_Y, z, x + w, RENDER_Y + CHUNK_THICKNESS, z + d),
                c, c, ShapeMode.Both, 0
            );
            renderedMarkers++;
        }

        for (ChunkPos pos : baseHits) {
            if (renderedMarkers >= MAX_RENDERED_MARKERS) break;
            if (Math.abs(pos.x - playerChunk.x) > MAX_RENDER_DISTANCE_CHUNKS ||
                Math.abs(pos.z - playerChunk.z) > MAX_RENDER_DISTANCE_CHUNKS) continue;
            double x = pos.x * 16.0;
            double z = pos.z * 16.0;
            Color c = new Color(255, 40, 40, Math.min(255, alpha + 50));
            event.renderer.box(
                new AABB(x, RENDER_Y + 0.15, z, x + 16, RENDER_Y + 0.25, z + 16),
                c, c, ShapeMode.Both, 0
            );
            renderedMarkers++;
        }
    }
}
