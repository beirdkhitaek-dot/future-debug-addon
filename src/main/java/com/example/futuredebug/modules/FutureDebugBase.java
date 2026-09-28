package com.example.futuredebug.modules;

import com.example.futuredebug.FutureDebugAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class FutureDebugBase extends Module {
    protected static final int BASE_CHEST_THRESHOLD = 10;
    protected static final double CHUNK_THICKNESS = 0.1;
    protected static final double RENDER_Y = 63.0;
    protected static final int MARKER_SPACING_CHUNKS = 7;
    protected static final int MAX_RENDERED_MARKERS = 64;
    protected static final int MAX_RENDER_DISTANCE_CHUNKS = 16;

    protected final SettingGroup sgGeneral = settings.getDefaultGroup();
    protected final SettingGroup sgRender = settings.createGroup("Render");

    protected final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius").description("Chunk radius multiplier used by the original scanner.")
        .defaultValue(1).min(1).max(5).build());

    protected final Setting<Boolean> smartCheck = sgGeneral.add(new BoolSetting.Builder()
        .name("smart-check").description("Use the stricter geode shape/density checks.")
        .defaultValue(true).build());

    protected final Setting<Integer> sensitivity = sgGeneral.add(new IntSetting.Builder()
        .name("sensitivity").description("Detection sensitivity.")
        .defaultValue(5).min(1).max(10).build());

    protected final Setting<SettingColor> fillColor = sgRender.add(new ColorSetting.Builder()
        .name("fill-color").description("Marker color.")
        .defaultValue(new SettingColor(180, 60, 60, 40)).build());

    protected final Setting<Integer> fillAlpha = sgRender.add(new IntSetting.Builder()
        .name("fill-alpha").description("Marker opacity.")
        .defaultValue(40).min(0).max(255).build());

    protected final ConcurrentHashMap<Long, Long> confirmedChunks = new ConcurrentHashMap<>();
    protected final Set<ChunkPos> baseHits = ConcurrentHashMap.newKeySet();
    protected final AtomicBoolean scanning = new AtomicBoolean(false);

    protected ExecutorService scanExec;
    protected int tickCount;

    protected FutureDebugBase(String name, String description) {
        super(FutureDebugAddon.CATEGORY, name, description);
    }

    @Override
    public void onActivate() {
        confirmedChunks.clear();
        baseHits.clear();
        tickCount = 0;
        scanning.set(false);
    }

    @Override
    public void onDeactivate() {
        confirmedChunks.clear();
        baseHits.clear();
        scanning.set(false);
        if (scanExec != null) scanExec.shutdownNow();
    }

    protected int minAmethyst() {
        return (int) (40.0 - (sensitivity.get() - 1) * 3.5555555556);
    }

    protected float minDensity() {
        return (float) (0.01 - (sensitivity.get() - 1) * 0.00088888896);
    }

    protected int minCalciteBasalt() {
        return (int) (15.0 - (sensitivity.get() - 1) * 1.3333333333);
    }

    protected float maxAirRatio() {
        return 0.35f + (sensitivity.get() - 1) * 0.05f;
    }

    protected float maxAvgDist() {
        return 7.0f + (sensitivity.get() - 1) * 0.5555556f;
    }

    protected boolean isCountableAmethyst(BlockState state) {
        return state.is(Blocks.AMETHYST_BLOCK)
            || state.is(Blocks.BUDDING_AMETHYST)
            || state.is(Blocks.SMALL_AMETHYST_BUD)
            || state.is(Blocks.MEDIUM_AMETHYST_BUD)
            || state.is(Blocks.LARGE_AMETHYST_BUD);
    }

    protected boolean hasChestsBelowZero(LevelChunk chunk) {
        int count = 0;
        LevelChunkSection[] sections = chunk.getSections();

        for (int si = 0; si < sections.length; si++) {
            int sectionY = chunk.getSectionYFromSectionIndex(si);
            int bottomY = sectionY * 16;
            if (bottomY >= 0) break;

            LevelChunkSection section = sections[si];
            if (section == null || section.hasOnlyAir()) continue;

            int maxLocalY = Math.min(15, -bottomY - 1);
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y <= maxLocalY; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) {
                            if (++count >= BASE_CHEST_THRESHOLD) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    protected boolean isUnknownGeode(LevelChunk chunk) {
        int amethyst = 0, calcite = 0, basalt = 0;
        int sumX = 0, sumY = 0, sumZ = 0;
        List<int[]> points = new ArrayList<>();

        LevelChunkSection[] sections = chunk.getSections();
        for (int si = 0; si < sections.length; si++) {
            int sectionY = chunk.getSectionYFromSectionIndex(si);
            int baseY = sectionY * 16;
            if (baseY > 64 || baseY + 15 < -64) continue;

            LevelChunkSection section = sections[si];
            if (section == null || section.hasOnlyAir()) continue;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    int worldY = baseY + y;
                    if (worldY < -64 || worldY > 64) continue;

                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);

                        // The original rejects a chunk containing exposed air-pocket blocks
                        // in the scanned volume.
                        if (state.is(Blocks.WATER) || state.is(Blocks.LAVA)) return false;

                        if (isCountableAmethyst(state)) {
                            amethyst++;
                            sumX += x;
                            sumY += worldY;
                            sumZ += z;
                            points.add(new int[]{x, worldY, z});
                        } else if (state.is(Blocks.CALCITE)) {
                            calcite++;
                        } else if (state.is(Blocks.BASALT) || state.is(Blocks.SMOOTH_BASALT)) {
                            basalt++;
                        }
                    }
                }
            }
        }

        if (amethyst == 0) return false;
        if (!smartCheck.get()) return amethyst >= minAmethyst();

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        for (int[] p : points) {
            minX = Math.min(minX, p[1]);
            maxX = Math.max(maxX, p[1]);
        }

        float density = amethyst / (256.0f * Math.max(1, maxX - minX + 1));
        float avgX = sumX / (float) amethyst;
        float avgY = sumY / (float) amethyst;
        float avgZ = sumZ / (float) amethyst;

        float avgDist = 0;
        for (int[] p : points) {
            float dx = p[0] - avgX;
            float dy = p[1] - avgY;
            float dz = p[2] - avgZ;
            avgDist += Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        avgDist /= amethyst;

        int neighborNonAir = 0;
        int solidNeighbor = 0;
        for (int[] p : points) {
            int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                int nz = p[2] + d[2];
                if (nx < 0 || nx > 15 || nz < 0 || nz > 15 || ny < -64 || ny > 64) continue;
                BlockState s = chunk.getBlockState(new BlockPos(chunk.getPos().getMinBlockX() + nx, ny,
                    chunk.getPos().getMinBlockZ() + nz));
                if (!s.isAir()) {
                    neighborNonAir++;
                    if (s.is(Blocks.CALCITE) || s.is(Blocks.BASALT) || s.is(Blocks.SMOOTH_BASALT))
                        solidNeighbor++;
                }
            }
        }

        float calciteBasaltRatio = neighborNonAir == 0 ? 0 : solidNeighbor / (float) neighborNonAir;
        float airRatio = 1.0f - calciteBasaltRatio;

        return amethyst >= minAmethyst()
            && density >= minDensity()
            && calcite + basalt >= minCalciteBasalt()
            && avgDist <= maxAvgDist()
            && airRatio <= maxAirRatio();
    }

    protected boolean canAddMarker(ChunkPos pos) {
        for (long packed : confirmedChunks.keySet()) {
            int x = ChunkPos.getX(packed);
            int z = ChunkPos.getZ(packed);
            if (Math.abs(x - pos.x) <= MARKER_SPACING_CHUNKS &&
                Math.abs(z - pos.z) <= MARKER_SPACING_CHUNKS) return false;
        }
        return true;
    }

    protected void scheduleScan() {
        if (mc.level == null || mc.player == null) return;
        int radius = scanRadius.get() * 5;
        if (scanExec == null || scanExec.isShutdown()) {
            scanExec = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "futuredebug-scan");
                t.setDaemon(true);
                return t;
            });
        }

        List<ChunkPos> positions = new ArrayList<>();
        List<LevelChunk> chunks = new ArrayList<>();
        ChunkPos center = mc.player.chunkPosition();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);
                LevelChunk chunk = mc.level.getChunkSource().getChunkNow(pos.x, pos.z);
                if (chunk != null) {
                    positions.add(pos);
                    chunks.add(chunk);
                }
            }
        }

        long now = System.currentTimeMillis();
        scanExec.submit(() -> {
            try {
                for (int i = 0; i < positions.size(); i++) {
                    ChunkPos pos = positions.get(i);
                    LevelChunk chunk = chunks.get(i);
                    long packed = pos.toLong();

                    if (!confirmedChunks.containsKey(packed) && canAddMarker(pos) && isUnknownGeode(chunk)) {
                        confirmedChunks.put(packed, now);
                    }
                    if (hasChestsBelowZero(chunk)) baseHits.add(pos);
                }
            } finally {
                scanning.set(false);
            }
        });
    }

    protected void commonTick() {
        if (mc.level == null || mc.player == null) return;

        ChunkPos center = mc.player.chunkPosition();
        baseHits.removeIf(pos -> Math.abs(pos.x - center.x) > scanRadius.get() * 5 ||
            Math.abs(pos.z - center.z) > scanRadius.get() * 5);

        if (++tickCount % 5 != 0) return;
        if (!scanning.compareAndSet(false, true)) return;
        scheduleScan();
    }
}
