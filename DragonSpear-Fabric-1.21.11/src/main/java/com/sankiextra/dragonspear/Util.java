package com.sankiextra.dragonspear;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.UUID;

/** Small helpers shared by the whole mod. */
public final class Util {
    private Util() {}

    /** Set when the server starts. Used for running commands and looking up players. */
    public static volatile MinecraftServer server;

    /** Chat/GUI text without the default italic style. */
    public static Component text(String s, ChatFormatting... formats) {
        return Component.literal(s).withStyle(formats).withStyle(st -> st.withItalic(false));
    }

    public static Component gray(String s) {
        return text(s, ChatFormatting.GRAY);
    }

    public static void msg(ServerPlayer p, String s, ChatFormatting color) {
        p.sendSystemMessage(Component.literal(s).withStyle(color));
    }

    public static void broadcast(String s, ChatFormatting... formats) {
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(s).withStyle(formats), false);
        }
    }

    public static String name(ServerPlayer p) {
        return p.getName().getString();
    }

    public static ServerPlayer player(UUID id) {
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }

    /** Runs a vanilla command as the server (permission level 4, no output spam). */
    public static void run(String command) {
        if (server == null) return;
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    /** A GUI icon with a custom name, lore lines and an optional enchant glow. */
    public static ItemStack icon(Item item, Component name, boolean glow, Component... lore) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.CUSTOM_NAME, name);
        if (lore.length > 0) {
            s.set(DataComponents.LORE, new ItemLore(List.of(lore)));
        }
        if (glow) {
            s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        }
        return s;
    }

    /** Re-applies every stored toggle (fly, vanish, freeze) after join or respawn. */
    public static void reapply(ServerPlayer p) {
        for (Feature f : Feature.values()) {
            if (f.has(p)) {
                f.apply(p, true);
            }
        }
    }

    public static void heal(ServerPlayer p) {
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(20.0F);
        p.clearFire();
    }
}
