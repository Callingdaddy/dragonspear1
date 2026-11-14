package com.sankiextra.dragonspear;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/** Every GUI of the Recording Helper. */
public final class Menus {
    private Menus() {}

    @FunctionalInterface
    public interface Pick {
        void run(ServerPlayer admin, ServerPlayer target, boolean rightClick);
    }

    private record Fx(String id, String label, Item item) {}

    private static final List<Fx> EFFECTS = List.of(
            new Fx("speed", "Speed", Items.SUGAR),
            new Fx("strength", "Strength", Items.BLAZE_POWDER),
            new Fx("jump_boost", "Jump Boost", Items.RABBIT_FOOT),
            new Fx("night_vision", "Night Vision", Items.GOLDEN_CARROT),
            new Fx("regeneration", "Regeneration", Items.GHAST_TEAR),
            new Fx("resistance", "Resistance", Items.IRON_INGOT),
            new Fx("fire_resistance", "Fire Resistance", Items.MAGMA_CREAM),
            new Fx("water_breathing", "Water Breathing", Items.PUFFERFISH),
            new Fx("haste", "Haste", Items.GOLDEN_PICKAXE),
            new Fx("saturation", "Saturation", Items.COOKED_BEEF),
            new Fx("absorption", "Absorption", Items.GOLDEN_APPLE),
            new Fx("slow_falling", "Slow Falling", Items.FEATHER),
            new Fx("glowing", "Glowing", Items.GLOWSTONE_DUST));

    // ---------------------------------------------------------------- small helpers

    private static Component bold(String s, ChatFormatting color) {
        return Util.text(s, color, ChatFormatting.BOLD);
    }

    private static Component title(String s) {
        return Component.literal(s).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD);
    }

    private static ItemStack filler() {
        ItemStack s = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        return s;
    }

    private static ItemStack backButton() {
        return Util.icon(Items.BARRIER, bold("Back", ChatFormatting.RED), false);
    }

    private static Component yellow(String s) {
        return Util.text(s, ChatFormatting.YELLOW);
    }

    private static Component state(boolean on) {
        return Util.text("Status: " + (on ? "ENABLED" : "DISABLED"), on ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    // ---------------------------------------------------------------- main menu

    public static void openMain(ServerPlayer admin) {
        Gui.Builder b = new Gui.Builder(
                Component.literal("Recording Helper ").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD)
                        .append(Component.literal("- 20 Features!").withStyle(ChatFormatting.GOLD)),
                5);

        // Row 1
        b.set(10, Util.icon(Items.RECOVERY_COMPASS, bold("Inventory Rollback", ChatFormatting.GREEN), false,
                        Util.gray("Restore any player's inventory"), Util.gray("from their last death")),
                (p, r) -> selector(p, "Inventory Rollback", null, false, (a, t, right) -> {
                    if (Actions.rollback(t)) {
                        Util.msg(a, "Restored " + Util.name(t) + "'s inventory.", ChatFormatting.GREEN);
                    } else {
                        Util.msg(a, Util.name(t) + " has no death backup yet.", ChatFormatting.RED);
                    }
                }));
        b.set(11, Util.icon(Items.CHEST, bold("Server Kits", ChatFormatting.GOLD), false,
                        Util.gray("Create and distribute kits"), Util.gray("for your recorders"),
                        Util.gray("Create one with /rh createkit <name>")),
                (p, r) -> kits(p));
        featureEntry(b, 12, Items.ENDER_CHEST, "Keep Inventory", ChatFormatting.LIGHT_PURPLE, Feature.KEEPINV,
                "Keep items + XP on death");
        featureEntry(b, 13, Items.TOTEM_OF_UNDYING, "Immortality", ChatFormatting.RED, Feature.IMMORTAL,
                "Can't die (stays at half a heart)");
        featureEntry(b, 14, Items.DIAMOND_CHESTPLATE, "God Mode", ChatFormatting.AQUA, Feature.GOD,
                "No damage at all");
        featureEntry(b, 15, Items.ELYTRA, "Creative Flight", ChatFormatting.WHITE, Feature.FLY,
                "Permanent flight");
        featureEntry(b, 16, Items.GLASS, "Vanish Mode", ChatFormatting.WHITE, Feature.VANISH,
                "Invisible (invisibility effect)");

        // Row 2
        featureEntry(b, 19, Items.ICE, "Freeze Players", ChatFormatting.BLUE, Feature.FREEZE,
                "Lock player movement");
        b.set(20, Util.icon(Items.ENDER_PEARL, bold("Teleport Tool", ChatFormatting.DARK_PURPLE), false,
                        Util.gray("Quick player teleporting"), yellow("Click to choose a player")),
                (p, r) -> selector(p, "Teleport Tool", null, false, (a, t, right) -> {
                    Util.run("tp " + a.getUUID() + " " + t.getUUID());
                    Util.msg(a, "Teleported to " + Util.name(t), ChatFormatting.GREEN);
                    a.closeContainer();
                }));
        b.set(21, Util.icon(Items.SPYGLASS, bold("Spectate Mode", ChatFormatting.GRAY), false,
                        Util.gray("Enter spectator mode"), Util.gray("for recording")),
                (p, r) -> {
                    Actions.gamemode(p, "spectator");
                    Util.msg(p, "Spectator mode enabled!", ChatFormatting.GREEN);
                    p.closeContainer();
                });
        b.set(22, Util.icon(Items.CLOCK, bold("Freeze Time", ChatFormatting.AQUA), State.timeFrozen,
                        Util.gray("Perfect lighting control"), state(State.timeFrozen)),
                (p, r) -> {
                    Actions.setTimeFrozen(!State.timeFrozen);
                    openMain(p);
                });
        b.set(23, Util.icon(Items.SUNFLOWER, bold("Weather Control", ChatFormatting.YELLOW), !State.weatherLock.isEmpty(),
                        Util.gray("Lock weather conditions"),
                        Util.gray("Current: " + (State.weatherLock.isEmpty() ? "Unlocked" : State.weatherLock))),
                (p, r) -> weather(p));
        b.set(24, Util.icon(Items.TNT, bold("Clear Entities", ChatFormatting.RED), false,
                        Util.gray("Remove mobs / dropped items"), Util.gray("(named mobs are kept)")),
                (p, r) -> clearMenu(p));
        b.set(25, Util.icon(Items.SKELETON_SKULL, bold("Hardcore Mode", ChatFormatting.DARK_RED), State.hardcore,
                        Util.gray("Death = Auto Ban!"), state(State.hardcore), yellow("Click to toggle")),
                (p, r) -> {
                    Actions.setHardcore(!State.hardcore);
                    openMain(p);
                });

        // Row 3
        b.set(28, Util.icon(Items.DIAMOND_SWORD, bold("PVP Toggle", ChatFormatting.RED), State.pvp,
                        Util.gray("Allow players to hurt each other"), state(State.pvp), yellow("Click to toggle")),
                (p, r) -> {
                    Actions.setPvp(!State.pvp);
                    openMain(p);
                });
        b.set(29, Util.icon(Items.WRITABLE_BOOK, bold("Chat Toggle", ChatFormatting.WHITE), State.chat,
                        Util.gray("Commands still work"), state(State.chat), yellow("Click to toggle")),
                (p, r) -> {
                    Actions.setChat(!State.chat);
                    openMain(p);
                });
        b.set(30, Util.icon(Items.GOLDEN_APPLE, bold("Quick Heal", ChatFormatting.GOLD), false,
                        Util.gray("Heal players instantly"), yellow("Click to choose a player")),
                (p, r) -> selector(p, "Quick Heal", null, false, (a, t, right) -> {
                    Util.heal(t);
                    Util.msg(t, "You have been healed.", ChatFormatting.GREEN);
                    Util.msg(a, "Healed " + Util.name(t), ChatFormatting.GREEN);
                }));
        b.set(31, Util.icon(Items.COMMAND_BLOCK, bold("Gamemode", ChatFormatting.GOLD), false,
                        Util.gray("Change player gamemode"), yellow("Click to choose a player")),
                (p, r) -> selector(p, "Gamemode: choose player", null, false, (a, t, right) -> gamemodeMenu(a, t.getUUID())));
        b.set(32, Util.icon(Items.POTION, bold("Potion Effects", ChatFormatting.LIGHT_PURPLE), false,
                        Util.gray("Give infinite potions"), Util.gray("No particles!")),
                (p, r) -> selector(p, "Potion Effects: choose player", null, false, (a, t, right) -> potionMenu(a, t.getUUID())));
        b.set(33, Util.icon(Items.PLAYER_HEAD, bold("Skin Changer", ChatFormatting.DARK_GRAY), false,
                Util.gray("Not available in the Fabric version yet")));
        b.set(34, Util.icon(Items.NETHER_STAR, bold("Plugin Info", ChatFormatting.GOLD), false,
                Util.gray("DragonSpear (Fabric 1.21.11)"), Util.gray("By Krishna"), Util.gray("20 Features!"),
                Util.gray("For Content Creators")));

        b.fillEmpty(filler()).open(admin);
    }

    private static void featureEntry(Gui.Builder b, int slot, Item item, String name, ChatFormatting color,
                                     Feature feature, String description) {
        b.set(slot, Util.icon(item, bold(name, color), !feature.players.isEmpty(),
                        Util.gray(description),
                        Util.gray("Active players: " + feature.players.size()),
                        yellow("Click to manage")),
                (p, r) -> selector(p, name, feature, true, (a, t, right) -> feature.toggle(t)));
    }

    // ---------------------------------------------------------------- player selector

    /**
     * Shows all online players as heads. If {@code active} is set, the heads show that feature's status.
     * If {@code refresh} is true the selector reopens after every click so the status stays current.
     */
    public static void selector(ServerPlayer admin, String titleText, Feature active, boolean refresh, Pick pick) {
        Gui.Builder b = new Gui.Builder(title(titleText), 6);
        int slot = 0;
        for (ServerPlayer target : Util.server.getPlayerList().getPlayers()) {
            if (slot >= 45) break;
            final UUID id = target.getUUID();
            boolean on = active != null && active.has(target);
            Component[] lore = active == null
                    ? new Component[] {yellow("Click to select")}
                    : new Component[] {Util.text(on ? "Status: ON" : "Status: OFF", on ? ChatFormatting.GREEN : ChatFormatting.RED),
                            yellow("Click to toggle")};
            b.set(slot++, Util.icon(Items.PLAYER_HEAD, Util.text(Util.name(target), ChatFormatting.WHITE), on, lore),
                    (p, right) -> {
                        ServerPlayer t = Util.player(id);
                        if (t == null) {
                            Util.msg(p, "Player not found!", ChatFormatting.RED);
                            return;
                        }
                        pick.run(p, t, right);
                        if (refresh) {
                            selector(p, titleText, active, true, pick);
                        }
                    });
        }
        b.set(49, backButton(), (p, r) -> openMain(p));
        b.open(admin);
    }

    // ---------------------------------------------------------------- kits

    private static void kits(ServerPlayer admin) {
        Gui.Builder b = new Gui.Builder(title("Server Kits"), 6);
        int slot = 0;
        for (String name : Kits.names()) {
            if (slot >= 45) break;
            final String kit = name;
            b.set(slot++, Util.icon(Items.CHEST, bold(kit, ChatFormatting.GOLD), false,
                            Util.gray("Left click: give to yourself"),
                            Util.gray("Right click: give to ALL players"),
                            Util.gray("(replaces their whole inventory!)")),
                    (p, right) -> {
                        if (right) {
                            int n = 0;
                            for (ServerPlayer t : Util.server.getPlayerList().getPlayers()) {
                                if (Actions.giveKit(t, kit)) n++;
                            }
                            Util.msg(p, "Gave '" + kit + "' to " + n + " player(s).", ChatFormatting.GREEN);
                        } else {
                            Actions.giveKit(p, kit);
                        }
                    });
        }
        b.set(49, backButton(), (p, r) -> openMain(p));
        b.open(admin);
    }

    // ---------------------------------------------------------------- weather

    private static void weather(ServerPlayer admin) {
        Gui.Builder b = new Gui.Builder(title("Weather Control"), 3);
        b.set(10, Util.icon(Items.SUNFLOWER, bold("Clear (locked)", ChatFormatting.YELLOW), State.weatherLock.equals("clear")),
                (p, r) -> {
                    Actions.setWeather("clear");
                    weather(p);
                });
        b.set(12, Util.icon(Items.WATER_BUCKET, bold("Rain (locked)", ChatFormatting.BLUE), State.weatherLock.equals("rain")),
                (p, r) -> {
                    Actions.setWeather("rain");
                    weather(p);
                });
        b.set(14, Util.icon(Items.LIGHTNING_ROD, bold("Thunder (locked)", ChatFormatting.DARK_PURPLE), State.weatherLock.equals("thunder")),
                (p, r) -> {
                    Actions.setWeather("thunder");
                    weather(p);
                });
        b.set(16, Util.icon(Items.LEVER, bold("Unlock weather", ChatFormatting.GREEN), State.weatherLock.isEmpty()),
                (p, r) -> {
                    Actions.setWeather("");
                    weather(p);
                });
        b.set(22, backButton(), (p, r) -> openMain(p));
        b.fillEmpty(filler()).open(admin);
    }

    // ---------------------------------------------------------------- clear entities

    private static void clearMenu(ServerPlayer admin) {
        Gui.Builder b = new Gui.Builder(title("Clear Entities"), 3);
        b.set(11, Util.icon(Items.ZOMBIE_HEAD, bold("Clear mobs", ChatFormatting.RED), false, Util.gray("Named mobs are kept")),
                (p, r) -> reportClear(p, Actions.clearEntities(true, false)));
        b.set(13, Util.icon(Items.HOPPER, bold("Clear dropped items", ChatFormatting.GOLD), false),
                (p, r) -> reportClear(p, Actions.clearEntities(false, true)));
        b.set(15, Util.icon(Items.TNT, bold("Clear both", ChatFormatting.DARK_RED), false),
                (p, r) -> reportClear(p, Actions.clearEntities(true, true)));
        b.set(22, backButton(), (p, r) -> openMain(p));
        b.fillEmpty(filler()).open(admin);
    }

    private static void reportClear(ServerPlayer p, int[] result) {
        Util.msg(p, "Removed " + result[0] + " mob(s) and " + result[1] + " item(s).", ChatFormatting.GREEN);
    }

    // ---------------------------------------------------------------- gamemode

    private static void gamemodeMenu(ServerPlayer admin, UUID targetId) {
        ServerPlayer target = Util.player(targetId);
        String targetName = target == null ? "?" : Util.name(target);
        Gui.Builder b = new Gui.Builder(title("Gamemode: " + targetName), 3);
        gm(b, 10, Items.GRASS_BLOCK, "Survival", ChatFormatting.GREEN, "survival", targetId);
        gm(b, 12, Items.DIAMOND_BLOCK, "Creative", ChatFormatting.GOLD, "creative", targetId);
        gm(b, 14, Items.WOODEN_SWORD, "Adventure", ChatFormatting.DARK_GREEN, "adventure", targetId);
        gm(b, 16, Items.ENDER_EYE, "Spectator", ChatFormatting.GRAY, "spectator", targetId);
        b.set(22, backButton(), (p, r) -> openMain(p));
        b.fillEmpty(filler()).open(admin);
    }

    private static void gm(Gui.Builder b, int slot, Item item, String label, ChatFormatting color, String mode, UUID targetId) {
        b.set(slot, Util.icon(item, bold(label, color), false), (p, r) -> {
            ServerPlayer t = Util.player(targetId);
            if (t == null) {
                Util.msg(p, "Player not found!", ChatFormatting.RED);
                return;
            }
            Actions.gamemode(t, mode);
            Util.msg(p, Util.name(t) + " \u2192 " + label, ChatFormatting.GREEN);
            p.closeContainer();
        });
    }

    // ---------------------------------------------------------------- potions

    private static void potionMenu(ServerPlayer admin, UUID targetId) {
        ServerPlayer target = Util.player(targetId);
        String targetName = target == null ? "?" : Util.name(target);
        Gui.Builder b = new Gui.Builder(title("Potions: " + targetName), 4);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24};
        for (int i = 0; i < EFFECTS.size() && i < slots.length; i++) {
            final Fx fx = EFFECTS.get(i);
            b.set(slots[i], Util.icon(fx.item(), bold(fx.label(), ChatFormatting.LIGHT_PURPLE), false,
                            Util.gray("Left click: level I"), Util.gray("Right click: level II"),
                            Util.gray("Infinite, no particles")),
                    (p, right) -> {
                        ServerPlayer t = Util.player(targetId);
                        if (t == null) {
                            Util.msg(p, "Player not found!", ChatFormatting.RED);
                            return;
                        }
                        Actions.effect(t, fx.id(), right ? 1 : 0);
                        Util.msg(p, "Gave " + fx.label() + (right ? " II" : " I") + " to " + Util.name(t), ChatFormatting.GREEN);
                    });
        }
        b.set(30, Util.icon(Items.MILK_BUCKET, bold("Clear all effects", ChatFormatting.WHITE), false),
                (p, r) -> {
                    ServerPlayer t = Util.player(targetId);
                    if (t != null) {
                        Actions.clearEffects(t);
                        Util.msg(p, "Cleared all effects of " + Util.name(t), ChatFormatting.GREEN);
                    }
                });
        b.set(32, backButton(), (p, r) -> openMain(p));
        b.fillEmpty(filler()).open(admin);
    }
}
