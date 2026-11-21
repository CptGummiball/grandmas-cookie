package dev.cptgummiball.omacore.house;

import dev.cptgummiball.omacore.core.cookie.CookieLedger;
import dev.cptgummiball.omacore.core.house.HouseArea;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-wide state of the pack, stored in the overworld's data folder ({@code data/oma_core_world.dat}),
 * so it travels with a singleplayer save as well as with a server world (ADR-0006). Versioned: unknown
 * newer schemas are refused instead of being overwritten.
 */
public final class OmaWorldData extends SavedData {
    public static final String NAME = "oma_core_world";
    /**
     * 1: house + Grandma; 2: + cookie deliveries per team (v1 data loads with an empty ledger);
     * 3: + Grandma's kitchen drawer position (older data gets it from the blueprint on the next start);
     * 4: + which blueprint the house was built from (older data: the first cottage, {@value #LEGACY_BLUEPRINT}).
     */
    public static final int SCHEMA = 4;
    /** Blueprint id of houses built before the id was recorded (oma_core/house/cottage.txt). */
    public static final String LEGACY_BLUEPRINT = "cottage";
    private static final SavedData.Factory<OmaWorldData> FACTORY = new SavedData.Factory<>(OmaWorldData::new, OmaWorldData::load);

    private boolean houseGenerated;
    private BlockPos houseOrigin = BlockPos.ZERO;
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    private BlockPos arrival = BlockPos.ZERO;
    private float arrivalYaw;
    private BlockPos grandmaPos = BlockPos.ZERO;
    private float grandmaYaw;
    private UUID grandmaUuid; // optional field: absent in data written before Grandma existed
    private final CookieLedger cookies = new CookieLedger();
    private int grandmaPresetVersion; // 0 = imported before versioning
    private BlockPos drawerPos; // optional: null until recorded (houses built before the drawer existed)
    private String houseBlueprint = LEGACY_BLUEPRINT;

    public static OmaWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static OmaWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        int schema = tag.getInt("schemaVersion");
        if (schema > SCHEMA) {
            throw new IllegalStateException("oma_core world data has schema " + schema + " but this oma_core only knows "
                    + SCHEMA + "; refusing to load it with an older mod version");
        }
        OmaWorldData d = new OmaWorldData();
        d.houseGenerated = tag.getBoolean("houseGenerated");
        d.houseOrigin = NbtUtils.readBlockPos(tag, "houseOrigin").orElse(BlockPos.ZERO);
        d.sizeX = tag.getInt("sizeX");
        d.sizeY = tag.getInt("sizeY");
        d.sizeZ = tag.getInt("sizeZ");
        d.arrival = NbtUtils.readBlockPos(tag, "arrival").orElse(BlockPos.ZERO);
        d.arrivalYaw = tag.getFloat("arrivalYaw");
        d.grandmaPos = NbtUtils.readBlockPos(tag, "grandmaPos").orElse(BlockPos.ZERO);
        d.grandmaYaw = tag.getFloat("grandmaYaw");
        d.grandmaUuid = tag.hasUUID("grandmaUuid") ? tag.getUUID("grandmaUuid") : null;
        d.grandmaPresetVersion = tag.getInt("grandmaPresetVersion");
        d.drawerPos = NbtUtils.readBlockPos(tag, "drawerPos").orElse(null);
        if (tag.contains("houseBlueprint", Tag.TAG_STRING)) {
            d.houseBlueprint = tag.getString("houseBlueprint");
        }
        for (Tag t : tag.getList("cookieDeliveries", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (c.hasUUID("team") && c.hasUUID("player")) {
                d.cookies.restore(c.getUUID("team"), new CookieLedger.Delivery(c.getUUID("player"), c.getLong("gameTime")));
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("schemaVersion", SCHEMA);
        tag.putBoolean("houseGenerated", houseGenerated);
        tag.put("houseOrigin", NbtUtils.writeBlockPos(houseOrigin));
        tag.putInt("sizeX", sizeX);
        tag.putInt("sizeY", sizeY);
        tag.putInt("sizeZ", sizeZ);
        tag.put("arrival", NbtUtils.writeBlockPos(arrival));
        tag.putFloat("arrivalYaw", arrivalYaw);
        tag.put("grandmaPos", NbtUtils.writeBlockPos(grandmaPos));
        tag.putFloat("grandmaYaw", grandmaYaw);
        if (grandmaUuid != null) {
            tag.putUUID("grandmaUuid", grandmaUuid);
        }
        tag.putInt("grandmaPresetVersion", grandmaPresetVersion);
        if (drawerPos != null) {
            tag.put("drawerPos", NbtUtils.writeBlockPos(drawerPos));
        }
        tag.putString("houseBlueprint", houseBlueprint);
        ListTag list = new ListTag();
        cookies.view().forEach((team, delivery) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("team", team);
            c.putUUID("player", delivery.player());
            c.putLong("gameTime", delivery.gameTime());
            list.add(c);
        });
        tag.put("cookieDeliveries", list);
        return tag;
    }

    void recordHouse(String blueprint, BlockPos origin, int sx, int sy, int sz, BlockPos arrivalPos, float arrivalFacing,
                     BlockPos grandma, float grandmaFacing) {
        houseGenerated = true;
        houseBlueprint = blueprint;
        houseOrigin = origin;
        sizeX = sx;
        sizeY = sy;
        sizeZ = sz;
        arrival = arrivalPos;
        arrivalYaw = arrivalFacing;
        grandmaPos = grandma;
        grandmaYaw = grandmaFacing;
        setDirty();
    }

    /** The blueprint id the house was built from ({@value #LEGACY_BLUEPRINT} for houses from before v2). */
    public String houseBlueprint() {
        return houseBlueprint;
    }

    public boolean houseGenerated() {
        return houseGenerated;
    }

    public BlockPos houseOrigin() {
        return houseOrigin;
    }

    public BlockPos arrival() {
        return arrival;
    }

    public float arrivalYaw() {
        return arrivalYaw;
    }

    public BlockPos grandmaPos() {
        return grandmaPos;
    }

    public float grandmaYaw() {
        return grandmaYaw;
    }

    /** Which team already delivered Grandma's cookie (read-only view for checks). */
    public CookieLedger cookies() {
        return cookies;
    }

    /** Compare-and-set of a team's delivery; marks the data dirty when it records one. */
    public boolean tryDeliverCookie(UUID team, UUID player, long gameTime) {
        boolean recorded = cookies.tryDeliver(team, player, gameTime);
        if (recorded) {
            setDirty();
        }
        return recorded;
    }

    /** Version of the Grandma preset her Easy NPC was last imported from (see GrandmaKeeper.PRESET_VERSION). */
    public int grandmaPresetVersion() {
        return grandmaPresetVersion;
    }

    public void setGrandmaPresetVersion(int version) {
        grandmaPresetVersion = version;
        setDirty();
    }

    /** Grandma's stuck kitchen drawer (C00_Q05), once recorded. */
    public Optional<BlockPos> drawerPos() {
        return Optional.ofNullable(drawerPos);
    }

    void setDrawerPos(BlockPos pos) {
        drawerPos = pos;
        setDirty();
    }

    /** UUID of the one Grandma entity, or null before she was spawned. */
    public UUID grandmaUuid() {
        return grandmaUuid;
    }

    public void setGrandmaUuid(UUID uuid) {
        grandmaUuid = uuid;
        setDirty();
    }

    /** The house box plus margin in the overworld, if the house exists. */
    public Optional<HouseArea> area() {
        if (!houseGenerated) {
            return Optional.empty();
        }
        return Optional.of(HouseArea.of(houseOrigin.getX(), houseOrigin.getY(), houseOrigin.getZ(), sizeX, sizeY, sizeZ));
    }
}
