package dev.cptgummiball.omacore.house;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import dev.cptgummiball.omacore.core.house.Blueprint;
import dev.cptgummiball.omacore.core.house.BlueprintException;
import dev.cptgummiball.omacore.core.house.BlueprintParser;
import dev.cptgummiball.omacore.core.house.SitePicker;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * Builds Grandma's cottage once per world (OMA_CORE_SPEC "World / house lifecycle").
 * <ul>
 *   <li>Fresh world: on first server start the flattest dry spot near the world spawn is chosen, the
 *       cottage is placed, the world spawn moves inside it and {@code spawnRadius} becomes 0.</li>
 *   <li>Existing world (game time already running): nothing is placed automatically, so player
 *       buildings are never overwritten; an operator can run {@code /oma house place}.</li>
 * </ul>
 */
public final class HouseManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** New worlds get the current cottage; worlds built earlier keep their blueprint for protection and the drawer. */
    static final String CURRENT_BLUEPRINT = "cottage_v2";
    /** A world counts as fresh while its game time is below this (spawn preparation does not tick time). */
    private static final long FRESH_WORLD_TICKS = 100;
    private static final int SEARCH_RADIUS = 24;
    private static final int SEARCH_STEP = 8;
    private static final int MAX_FOUNDATION_DEPTH = 8;
    /** Blueprint marker of Grandma's stuck kitchen drawer. */
    static final String DRAWER = "drawer";

    private final Blueprint cottage;
    private final Map<String, Blueprint> blueprints;

    private HouseManager(Map<String, Blueprint> blueprints) {
        this.blueprints = Map.copyOf(blueprints);
        this.cottage = blueprints.get(CURRENT_BLUEPRINT);
    }

    public static HouseManager load() {
        Map<String, Blueprint> all = new HashMap<>();
        for (String id : List.of(OmaWorldData.LEGACY_BLUEPRINT, CURRENT_BLUEPRINT)) {
            String path = "/oma_core/house/" + id + ".txt";
            try (InputStream in = HouseManager.class.getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException("missing " + path + " in the oma_core jar");
                }
                all.put(id, BlueprintParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            } catch (IOException | BlueprintException e) {
                throw new IllegalStateException("invalid cottage blueprint " + path + ": " + e.getMessage(), e);
            }
        }
        return new HouseManager(all);
    }

    /** The blueprint new houses are built from. */
    public Blueprint cottage() {
        return cottage;
    }

    /** The blueprint this world's house was built from (the current one when there is no house yet). */
    public Blueprint blueprintFor(MinecraftServer server) {
        OmaWorldData data = OmaWorldData.get(server);
        if (!data.houseGenerated()) {
            return cottage;
        }
        Blueprint b = blueprints.get(data.houseBlueprint());
        if (b == null) {
            LOGGER.warn("[oma_core] Grandma's house: unknown blueprint '{}' in the world data; using '{}'",
                    data.houseBlueprint(), OmaWorldData.LEGACY_BLUEPRINT);
            return blueprints.get(OmaWorldData.LEGACY_BLUEPRINT);
        }
        return b;
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        OmaWorldData data = OmaWorldData.get(server);
        if (data.houseGenerated()) {
            LOGGER.info("[oma_core] Grandma's house: present at {} (arrival {}, blueprint {})", data.houseOrigin(),
                    data.arrival(), data.houseBlueprint());
            ensureDrawer(server);
            return;
        }
        ServerLevel overworld = server.overworld();
        if (overworld.getGameTime() > FRESH_WORLD_TICKS) {
            LOGGER.warn("[oma_core] Grandma's house: none in this existing world; not placing it automatically "
                    + "(player buildings are never overwritten). An operator can run /oma house place.");
            return;
        }
        BlockPos origin = chooseSite(overworld, overworld.getSharedSpawnPos());
        place(server, origin);
    }

    /** Places the cottage with its south-west-bottom corner (blueprint 0,0,0) at {@code origin}. */
    public void place(MinecraftServer server, BlockPos origin) {
        ServerLevel level = server.overworld();
        Map<String, BlockState> states = resolve(level.holderLookup(Registries.BLOCK));
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE; // no neighbour updates while building
        List<BlockPos> placed = new ArrayList<>();
        for (int y = 0; y < cottage.sizeY(); y++) {
            for (int z = 0; z < cottage.sizeZ(); z++) {
                for (int x = 0; x < cottage.sizeX(); x++) {
                    Optional<String> state = cottage.stateAt(x, y, z);
                    if (state.isPresent()) {
                        BlockPos p = origin.offset(x, y, z);
                        level.setBlock(p, states.get(state.get()), flags);
                        if (!state.get().equals(Blueprint.AIR_STATE)) {
                            placed.add(p);
                        }
                    }
                }
            }
        }
        connectShapes(level, placed, flags);
        BlockState fill = Blocks.COBBLESTONE.defaultBlockState();
        for (int z = 0; z < cottage.sizeZ(); z++) {
            for (int x = 0; x < cottage.sizeX(); x++) {
                if (!cottage.isFoundation(x, z)) {
                    continue;
                }
                for (int d = 1; d <= MAX_FOUNDATION_DEPTH; d++) {
                    BlockPos below = origin.offset(x, -d, z);
                    BlockState s = level.getBlockState(below);
                    if (!(s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty())) {
                        break;
                    }
                    level.setBlock(below, fill, flags);
                }
            }
        }
        Blueprint.Marker arrival = cottage.marker("arrival").orElseThrow();
        Blueprint.Marker grandma = cottage.marker("grandma").orElseThrow();
        BlockPos arrivalPos = origin.offset(arrival.x(), arrival.y(), arrival.z());
        BlockPos grandmaPos = origin.offset(grandma.x(), grandma.y(), grandma.z());
        OmaWorldData data = OmaWorldData.get(server);
        data.recordHouse(CURRENT_BLUEPRINT, origin, cottage.sizeX(), cottage.sizeY(), cottage.sizeZ(),
                arrivalPos, arrival.yaw(), grandmaPos, grandma.yaw());
        cottage.marker(DRAWER).ifPresent(m -> data.setDrawerPos(origin.offset(m.x(), m.y(), m.z())));
        level.setDefaultSpawnPos(arrivalPos, arrival.yaw());
        level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, server);
        LOGGER.info("[oma_core] Grandma's house: generated at {} (arrival {}, Grandma's spot {}); world spawn moved inside, spawnRadius=0",
                origin, arrivalPos, grandmaPos);
    }

    /**
     * Grandma's stuck kitchen drawer (C00_Q05): records its position for houses built before it existed and puts the
     * drawer block back when its spot is empty air (a broken drawer). Never replaces any other block.
     *
     * @return true when a drawer block was placed
     */
    public boolean ensureDrawer(MinecraftServer server) {
        OmaWorldData data = OmaWorldData.get(server);
        Blueprint built = blueprintFor(server);
        Optional<Blueprint.Marker> marker = built.marker(DRAWER);
        if (!data.houseGenerated() || marker.isEmpty()) {
            return false;
        }
        Blueprint.Marker m = marker.get();
        BlockPos pos = data.drawerPos().orElseGet(() -> {
            BlockPos p = data.houseOrigin().offset(m.x(), m.y(), m.z());
            data.setDrawerPos(p);
            LOGGER.info("[oma_core] Grandma's drawer: recorded at {} (house built before the drawer existed)", p);
            return p;
        });
        ServerLevel level = server.overworld();
        Optional<String> entry = built.stateAt(m.x(), m.y(), m.z());
        if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir() || entry.isEmpty()
                || entry.get().equals(Blueprint.AIR_STATE)) {
            return false;
        }
        level.setBlock(pos, firstKnown(level.holderLookup(Registries.BLOCK), entry.get()), Block.UPDATE_ALL);
        LOGGER.info("[oma_core] Grandma's drawer: placed at {}", pos);
        return true;
    }

    /**
     * Blocks were set without neighbour updates, so shapes that depend on neighbours (glass panes, fences, walls, stair
     * corners, furniture connections) still show their default. Recompute each from its finished neighbours, as vanilla
     * does after placing a structure; two passes settle chains. A block that cannot stand where the blueprint puts it
     * (e.g. a lantern without support) is reported, so a design error shows in the log.
     */
    private static void connectShapes(ServerLevel level, List<BlockPos> placed, int flags) {
        int changed = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (BlockPos p : placed) {
                BlockState state = level.getBlockState(p);
                BlockState shaped = Block.updateFromNeighbourShapes(state, level, p);
                if (shaped != state) {
                    if (shaped.isAir()) {
                        LOGGER.warn("[oma_core] Grandma's house: {} cannot stand at {} (blueprint error); left in place",
                                state, p);
                        continue;
                    }
                    level.setBlock(p, shaped, flags);
                    changed++;
                }
            }
        }
        LOGGER.info("[oma_core] Grandma's house: {} block shapes connected to their neighbours", changed);
    }

    private Map<String, BlockState> resolve(HolderLookup<Block> blocks) {
        Map<String, BlockState> out = new HashMap<>();
        out.put(Blueprint.AIR_STATE, Blocks.AIR.defaultBlockState());
        for (String s : cottage.palette().values()) {
            out.put(s, firstKnown(blocks, s));
        }
        return out;
    }

    /** The first alternative of a palette entry ({@code a|b|c}) that this game knows, e.g. a mod block then vanilla. */
    private static BlockState firstKnown(HolderLookup<Block> blocks, String entry) {
        CommandSyntaxException last = null;
        List<String> alternatives = Blueprint.alternatives(entry);
        for (String alternative : alternatives) {
            try {
                BlockState state = BlockStateParser.parseForBlock(blocks, alternative, false).blockState();
                if (last != null) { // a fallback: the preferred mod block is missing or its state is invalid
                    LOGGER.warn("[oma_core] cottage blueprint: using fallback '{}' instead of '{}' ({})", alternative,
                            alternatives.get(0), last.getMessage());
                }
                return state;
            } catch (CommandSyntaxException e) {
                last = e;
            }
        }
        throw new IllegalStateException("cottage blueprint: no known block state in '" + entry + "'"
                + (last == null ? "" : ": " + last.getMessage()), last);
    }

    /** Bounded search around the spawn (no unbounded scans): 7 x 7 candidates, 5 height samples each. */
    BlockPos chooseSite(ServerLevel level, BlockPos spawn) {
        int sx = cottage.sizeX();
        int sz = cottage.sizeZ();
        List<SitePicker.Candidate> candidates = new ArrayList<>();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx += SEARCH_STEP) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz += SEARCH_STEP) {
                int ox = spawn.getX() + dx - sx / 2;
                int oz = spawn.getZ() + dz - sz / 2;
                int[][] points = {{ox, oz}, {ox + sx - 1, oz}, {ox, oz + sz - 1}, {ox + sx - 1, oz + sz - 1}, {ox + sx / 2, oz + sz / 2}};
                int[] surface = new int[points.length];
                boolean water = false;
                for (int i = 0; i < points.length; i++) {
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, points[i][0], points[i][1]) - 1;
                    surface[i] = y;
                    water |= !level.getBlockState(new BlockPos(points[i][0], y, points[i][1])).getFluidState().isEmpty();
                }
                candidates.add(new SitePicker.Candidate(dx, dz, surface, water));
            }
        }
        Optional<SitePicker.Candidate> best = SitePicker.pick(candidates);
        if (best.isEmpty()) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()) - 1;
            LOGGER.warn("[oma_core] Grandma's house: no dry spot within {} blocks of spawn; building at the spawn", SEARCH_RADIUS);
            return new BlockPos(spawn.getX() - sx / 2, y, spawn.getZ() - sz / 2);
        }
        SitePicker.Candidate c = best.get();
        LOGGER.info("[oma_core] Grandma's house: site offset ({}, {}) from spawn, ground spread {} blocks",
                c.dx(), c.dz(), c.spread());
        return new BlockPos(spawn.getX() + c.dx() - sx / 2, c.floorY(), spawn.getZ() + c.dz() - sz / 2);
    }
}
