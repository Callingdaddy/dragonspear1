package com.sankiextra.dragonspear;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server kits. A kit is a full copy of an inventory: slots 0-35 normal, 36-39 armor (feet to head), 40 offhand. */
public final class Kits {
    private Kits() {}

    private static final int SIZE = 41;
    private static final Map<String, ItemStack[]> KITS = new LinkedHashMap<>();

    public static List<String> names() {
        return new ArrayList<>(KITS.keySet());
    }

    private static String find(String name) {
        for (String k : KITS.keySet()) {
            if (k.equalsIgnoreCase(name)) return k;
        }
        return null;
    }

    public static boolean exists(String name) {
        return find(name) != null;
    }

    public static void createFromInventory(String name, ServerPlayer p) {
        Inventory inv = p.getInventory();
        ItemStack[] items = blank();
        for (int i = 0; i < inv.getContainerSize() && i < SIZE; i++) {
            items[i] = inv.getItem(i).copy();
        }
        String existing = find(name);
        if (existing != null) KITS.remove(existing);
        KITS.put(name, items);
        State.save();
    }

    public static boolean delete(String name) {
        String k = find(name);
        if (k == null) return false;
        KITS.remove(k);
        State.save();
        return true;
    }

    /** Replaces the player's whole inventory with the kit. */
    public static boolean give(ServerPlayer p, String name) {
        String k = find(name);
        if (k == null) return false;
        ItemStack[] kit = KITS.get(k);
        Inventory inv = p.getInventory();
        inv.clearContent();
        for (int i = 0; i < kit.length && i < inv.getContainerSize(); i++) {
            inv.setItem(i, kit[i].copy());
        }
        inv.setChanged();
        return true;
    }

    private static ItemStack[] blank() {
        ItemStack[] a = new ItemStack[SIZE];
        Arrays.fill(a, ItemStack.EMPTY);
        return a;
    }

    // ---------------------------------------------------------------- saving / loading

    public static JsonObject toJson(MinecraftServer server) {
        RegistryOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        JsonObject out = new JsonObject();
        for (Map.Entry<String, ItemStack[]> e : KITS.entrySet()) {
            JsonArray arr = new JsonArray();
            for (ItemStack s : e.getValue()) {
                arr.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, s).getOrThrow());
            }
            out.add(e.getKey(), arr);
        }
        return out;
    }

    public static void fromJson(MinecraftServer server, JsonObject in) {
        RegistryOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        KITS.clear();
        for (Map.Entry<String, JsonElement> e : in.entrySet()) {
            try {
                JsonArray arr = e.getValue().getAsJsonArray();
                ItemStack[] items = blank();
                for (int i = 0; i < arr.size() && i < SIZE; i++) {
                    items[i] = ItemStack.OPTIONAL_CODEC.parse(ops, arr.get(i)).getOrThrow();
                }
                KITS.put(e.getKey(), items);
            } catch (Exception ex) {
                DragonSpearFabric.LOGGER.warn("Skipping broken kit '{}'", e.getKey(), ex);
            }
        }
    }

    // ---------------------------------------------------------------- starter kits

    private static ItemStack with(MinecraftServer server, Item item, Object... keyLevelPairs) {
        ItemStack stack = new ItemStack(item);
        var lookup = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (int i = 0; i + 1 < keyLevelPairs.length; i += 2) {
            @SuppressWarnings("unchecked")
            ResourceKey<Enchantment> key = (ResourceKey<Enchantment>) keyLevelPairs[i];
            stack.enchant(lookup.getOrThrow(key), (Integer) keyLevelPairs[i + 1]);
        }
        return stack;
    }

    /** Creates the three starter kits (Iron, Diamond, Netherite) if there are no kits yet. */
    public static void createStarterKits(MinecraftServer server) {
        if (!KITS.isEmpty()) return;

        ItemStack[] iron = blank();
        iron[0] = new ItemStack(Items.IRON_SWORD);
        iron[1] = new ItemStack(Items.BOW);
        iron[2] = new ItemStack(Items.IRON_PICKAXE);
        iron[3] = new ItemStack(Items.IRON_AXE);
        iron[4] = new ItemStack(Items.IRON_SHOVEL);
        iron[5] = new ItemStack(Items.COOKED_BEEF, 64);
        iron[6] = new ItemStack(Items.OAK_PLANKS, 64);
        iron[7] = new ItemStack(Items.COBBLESTONE, 64);
        iron[8] = new ItemStack(Items.TORCH, 64);
        iron[9] = new ItemStack(Items.ARROW, 64);
        iron[36] = new ItemStack(Items.IRON_BOOTS);
        iron[37] = new ItemStack(Items.IRON_LEGGINGS);
        iron[38] = new ItemStack(Items.IRON_CHESTPLATE);
        iron[39] = new ItemStack(Items.IRON_HELMET);
        iron[40] = new ItemStack(Items.SHIELD);
        KITS.put("Iron Kit", iron);

        ItemStack[] diamond = blank();
        diamond[0] = with(server, Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3);
        diamond[1] = with(server, Items.BOW, Enchantments.POWER, 5, Enchantments.INFINITY, 1);
        diamond[2] = with(server, Items.DIAMOND_PICKAXE, Enchantments.EFFICIENCY, 5, Enchantments.UNBREAKING, 3);
        diamond[3] = with(server, Items.DIAMOND_AXE, Enchantments.EFFICIENCY, 5, Enchantments.UNBREAKING, 3);
        diamond[4] = with(server, Items.DIAMOND_SHOVEL, Enchantments.EFFICIENCY, 5, Enchantments.UNBREAKING, 3);
        diamond[5] = new ItemStack(Items.GOLDEN_APPLE, 16);
        diamond[6] = new ItemStack(Items.ENDER_PEARL, 16);
        diamond[9] = new ItemStack(Items.ARROW, 1);
        diamond[36] = with(server, Items.DIAMOND_BOOTS, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.FEATHER_FALLING, 4);
        diamond[37] = with(server, Items.DIAMOND_LEGGINGS, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3);
        diamond[38] = with(server, Items.DIAMOND_CHESTPLATE, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3);
        diamond[39] = with(server, Items.DIAMOND_HELMET, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3);
        KITS.put("Diamond Kit", diamond);

        ItemStack[] nether = blank();
        nether[0] = with(server, Items.NETHERITE_SWORD, Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3);
        nether[1] = with(server, Items.BOW, Enchantments.POWER, 5, Enchantments.PUNCH, 2, Enchantments.FLAME, 1, Enchantments.INFINITY, 1);
        nether[2] = with(server, Items.NETHERITE_PICKAXE, Enchantments.EFFICIENCY, 5, Enchantments.FORTUNE, 3, Enchantments.UNBREAKING, 3);
        nether[3] = with(server, Items.NETHERITE_AXE, Enchantments.EFFICIENCY, 5, Enchantments.UNBREAKING, 3);
        nether[4] = with(server, Items.NETHERITE_SHOVEL, Enchantments.EFFICIENCY, 5, Enchantments.UNBREAKING, 3);
        nether[5] = new ItemStack(Items.GOLDEN_APPLE, 16);
        nether[6] = new ItemStack(Items.ENDER_PEARL, 16);
        nether[9] = new ItemStack(Items.ARROW, 1);
        nether[36] = with(server, Items.NETHERITE_BOOTS, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.FEATHER_FALLING, 4);
        nether[37] = with(server, Items.NETHERITE_LEGGINGS, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3);
        nether[38] = with(server, Items.NETHERITE_CHESTPLATE, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3);
        nether[39] = with(server, Items.NETHERITE_HELMET, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.RESPIRATION, 3);
        KITS.put("Netherite Kit", nether);

        DragonSpearFabric.LOGGER.info("Created 3 starter kits");
    }
}
