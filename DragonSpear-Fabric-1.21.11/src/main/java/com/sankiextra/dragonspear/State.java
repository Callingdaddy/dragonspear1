package com.sankiextra.dragonspear;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Global switches, death backups and the data.json file. */
public final class State {
    private State() {}

    public static volatile boolean hardcore = false;
    public static volatile boolean pvp = true;
    public static volatile boolean chat = true;
    public static volatile boolean timeFrozen = false;
    /** "", "clear", "rain" or "thunder" */
    public static volatile String weatherLock = "";

    /** Inventory of each player at their last death (kept in memory only, like the Paper plugin). */
    public static final Map<UUID, Backup> BACKUPS = new ConcurrentHashMap<>();

    public record Backup(ItemStack[] items, int level, float progress, int total, long time) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("dragonspear").resolve("data.json");
    }

    // ---------------------------------------------------------------- backups

    public static Backup snapshot(ServerPlayer p) {
        Inventory inv = p.getInventory();
        ItemStack[] items = new ItemStack[inv.getContainerSize()];
        for (int i = 0; i < items.length; i++) {
            items[i] = inv.getItem(i).copy();
        }
        return new Backup(items, p.experienceLevel, p.experienceProgress, p.totalExperience, System.currentTimeMillis());
    }

    public static void restore(ServerPlayer p, Backup b) {
        Inventory inv = p.getInventory();
        inv.clearContent();
        for (int i = 0; i < b.items().length && i < inv.getContainerSize(); i++) {
            inv.setItem(i, b.items()[i].copy());
        }
        inv.setChanged();
        p.experienceLevel = b.level();
        p.experienceProgress = b.progress();
        p.totalExperience = b.total();
    }

    /** Removes the items and XP orbs a player just dropped by dying (used by Keep Inventory). */
    public static void removeDeathDrops(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level)) return;
        for (ItemEntity e : level.getEntities(EntityTypeTest.forClass(ItemEntity.class),
                x -> x.tickCount == 0 && x.distanceToSqr(p) < 100.0)) {
            e.discard();
        }
        for (ExperienceOrb e : level.getEntities(EntityTypeTest.forClass(ExperienceOrb.class),
                x -> x.tickCount == 0 && x.distanceToSqr(p) < 100.0)) {
            e.discard();
        }
    }

    // ---------------------------------------------------------------- data.json

    public static synchronized void save() {
        MinecraftServer server = Util.server;
        if (server == null) return;
        try {
            JsonObject o = new JsonObject();
            for (Feature f : Feature.values()) {
                JsonArray arr = new JsonArray();
                for (UUID u : f.players) arr.add(u.toString());
                o.add(f.id, arr);
            }
            o.addProperty("hardcore", hardcore);
            o.addProperty("pvp", pvp);
            o.addProperty("chat", chat);
            o.addProperty("timeFrozen", timeFrozen);
            o.addProperty("weatherLock", weatherLock);
            o.add("kits", Kits.toJson(server));
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(o));
        } catch (Exception e) {
            DragonSpearFabric.LOGGER.error("Could not save DragonSpear data", e);
        }
    }

    public static synchronized void load(MinecraftServer server) {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            for (Feature feature : Feature.values()) {
                feature.players.clear();
                if (o.has(feature.id)) {
                    for (JsonElement el : o.getAsJsonArray(feature.id)) {
                        feature.players.add(UUID.fromString(el.getAsString()));
                    }
                }
            }
            if (o.has("hardcore")) hardcore = o.get("hardcore").getAsBoolean();
            if (o.has("pvp")) pvp = o.get("pvp").getAsBoolean();
            if (o.has("chat")) chat = o.get("chat").getAsBoolean();
            if (o.has("timeFrozen")) timeFrozen = o.get("timeFrozen").getAsBoolean();
            if (o.has("weatherLock")) weatherLock = o.get("weatherLock").getAsString();
            if (o.has("kits")) Kits.fromJson(server, o.getAsJsonObject("kits"));
        } catch (Exception e) {
            DragonSpearFabric.LOGGER.error("Could not load DragonSpear data", e);
        }
    }
}
