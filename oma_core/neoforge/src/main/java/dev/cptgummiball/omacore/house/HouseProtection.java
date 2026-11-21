package dev.cptgummiball.omacore.house;

import dev.cptgummiball.omacore.core.house.Blueprint;
import dev.cptgummiball.omacore.core.house.HouseArea;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

/**
 * Grandma's house cannot be taken apart (owner decision 2026-11-20): its furniture is decoration only and must not
 * end up in a player's inventory. Protected are exactly the house's own blocks, the non-air cells of the blueprint
 * at the recorded origin in the overworld, so players can still place and remove their own things in the free space
 * inside.
 * <ul>
 *   <li>Breaking by players, including fake players of machines that fire the break event, is cancelled, with a
 *       short action-bar message. Creative-mode players are exempt so an owner can rebuild the house.</li>
 *   <li>Explosions leave the house's blocks out of their block list; everything around still breaks.</li>
 *   <li>Pistons that would push, pull or destroy a house block do not move.</li>
 *   <li>Mobs inside the grounds (house box plus margin) cannot grief blocks.</li>
 *   <li>Flower pots of the house keep their plant.</li>
 * </ul>
 * Not covered: fire burning wooden blocks (no per-position hook in NeoForge) and machines that remove blocks without
 * firing the break event.
 */
public final class HouseProtection {
    private final HouseManager house;

    public HouseProtection(HouseManager house) {
        this.house = house;
    }

    /** The house of this world: where it stands and the blueprint it was built from (older worlds keep the first). */
    private record Site(BlockPos origin, Blueprint blueprint) {
        boolean isHouseBlock(BlockPos pos) {
            return blueprint.isStructure(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ());
        }

        boolean anyHouseBlock(List<BlockPos> positions) {
            for (BlockPos pos : positions) {
                if (isHouseBlock(pos)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** The recorded house, if the level is the overworld of a world with Grandma's house. */
    private Optional<Site> site(Level level) {
        if (!(level instanceof ServerLevel server) || level.dimension() != Level.OVERWORLD) {
            return Optional.empty();
        }
        OmaWorldData data = OmaWorldData.get(server.getServer());
        return data.houseGenerated()
                ? Optional.of(new Site(data.houseOrigin(), house.blueprintFor(server.getServer())))
                : Optional.empty();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (!(event.getLevel() instanceof Level level) || player.isCreative()) {
            return;
        }
        site(level).filter(s -> s.isHouseBlock(event.getPos())).ifPresent(s -> {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("oma_core.house.protected"), true);
        });
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        site(event.getLevel()).ifPresent(s -> event.getAffectedBlocks().removeIf(s::isHouseBlock));
    }

    @SubscribeEvent
    public void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }
        Optional<Site> site = site(level);
        if (site.isEmpty()) {
            return;
        }
        PistonStructureResolver structure = event.getStructureHelper();
        if (structure == null || !structure.resolve()) {
            return; // vanilla will not move anything either
        }
        if (site.get().anyHouseBlock(structure.getToPush()) || site.get().anyHouseBlock(structure.getToDestroy())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMobGriefing(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        Optional<HouseArea> area = OmaWorldData.get(level.getServer()).area();
        BlockPos p = entity.blockPosition();
        if (area.isPresent() && area.get().contains(p.getX(), p.getY(), p.getZ())) {
            event.setCanGrief(false);
        }
    }

    @SubscribeEvent
    public void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative()) {
            return;
        }
        Level level = event.getLevel();
        if (!(level.getBlockState(event.getPos()).getBlock() instanceof FlowerPotBlock)) {
            return;
        }
        site(level).filter(s -> s.isHouseBlock(event.getPos())).ifPresent(s -> {
            event.setCanceled(true); // the server re-sends the block, so the client shows the plant again
            event.setCancellationResult(InteractionResult.SUCCESS);
        });
    }
}
