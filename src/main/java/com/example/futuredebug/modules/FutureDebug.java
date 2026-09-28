package com.example.futuredebug.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.AABB;

public class FutureDebug extends FutureDebugBase {
    private static final int[][] SHAPES = {
        {5,9},{6,3},{6,3},{7,6},{7,7},{5,5},{6,2},{1,9},{8,5},
        {8,8},{10,6},{13,6},{13,7},{9,9},{4,4}
    };

    public FutureDebug() {
        super("future-debug", "Future-style debug scanner with geode and below-zero chest markers.");
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

        for (var entry : confirmedChunks.entrySet()) {
            long packed = entry.getKey();
            long start = entry.getValue();
            int cx = net.minecraft.world.level.ChunkPos.getX(packed);
            int cz = net.minecraft.world.level.ChunkPos.getZ(packed);

            float progress = Math.min(1f, Math.max(0f,
                (System.currentTimeMillis() - start) / 1000f));

            // Render the same thin, animated top-plane style.
            int[] shape = SHAPES[Math.floorMod((int)(packed ^ (packed >>> 32)), SHAPES.length)];
            double w = shape[0] * 16.0;
            double d = shape[1] * 16.0;
            double x = cx * 16.0;
            double z = cz * 16.0;
            double fade = 1.0 - Math.pow(1.0 - progress, 3.0);

            Color c = new Color(color.r, color.g, color.b,
                Math.max(0, Math.min(255, Math.round(alpha * (float) fade))));

            AABB box = new AABB(x, RENDER_Y, z, x + w, RENDER_Y + CHUNK_THICKNESS, z + d);
            event.renderer.box(box, c, c, ShapeMode.Both, 0);
        }

        for (var pos : baseHits) {
            double x = pos.x * 16.0;
            double z = pos.z * 16.0;
            Color c = new Color(255, 40, 40, Math.min(255, alpha + 50));
            event.renderer.box(
                new AABB(x, RENDER_Y + 0.15, z, x + 16, RENDER_Y + 0.25, z + 16),
                c, c, ShapeMode.Both, 0
            );
        }
    }
}
