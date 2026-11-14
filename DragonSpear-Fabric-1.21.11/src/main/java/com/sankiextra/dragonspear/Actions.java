package com.sankiextra.dragonspear;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.entity.EntityTypeTest;

/** The actual work behind the GUI buttons and the /rh commands. */
public final class Actions {
    private Actions() {}

    public static void setHardcore(boolean on) {
        State.hardcore = on;
        if (on) {
            Util.run("difficulty hard");
            Util.broadcast("\u26A0 HARDCORE MODE ENABLED! \u26A0", ChatFormatting.RED, ChatFormatting.BOLD);
            Util.broadcast("Death = Permanent Ban!", ChatFormatting.DARK_RED);
        } else {
            Util.broadcast("Hardcore mode disabled", ChatFormatting.GREEN);
        }
        State.save();
    }

    public static void setPvp(boolean on) {
        State.pvp = on;
        Util.broadcast("PVP has been " + (on ? "ENABLED!" : "DISABLED!"), on ? ChatFormatting.GREEN : ChatFormatting.RED);
        State.save();
    }

    public static void setChat(boolean on) {
        State.chat = on;
        Util.broadcast("Public chat has been " + (on ? "ENABLED!" : "DISABLED!"), on ? ChatFormatting.GREEN : ChatFormatting.RED);
        State.save();
    }

    public static void setTimeFrozen(boolean frozen) {
        State.timeFrozen = frozen;
        Util.run("gamerule advance_time " + (frozen ? "false" : "true"));
        State.save();
    }

    /** weather = "clear", "rain", "thunder" to lock it, or "" to unlock. */
    public static void setWeather(String weather) {
        State.weatherLock = weather;
        if (weather.isEmpty()) {
            Util.run("gamerule advance_weather true");
        } else {
            Util.run("weather " + weather);
            Util.run("gamerule advance_weather false");
        }
        State.save();
    }

    public static void setTime(String dayOrNight) {
        Util.run("time set " + dayOrNight);
    }

    public static void gamemode(ServerPlayer target, String mode) {
        Util.run("gamemode " + mode + " " + target.getUUID());
        Util.msg(target, "Gamemode: " + mode, ChatFormatting.GREEN);
    }

    /** Gives an infinite potion effect without particles. */
    public static void effect(ServerPlayer target, String effectId, int amplifier) {
        Util.run("effect give " + target.getUUID() + " minecraft:" + effectId + " infinite " + amplifier + " true");
    }

    public static void clearEffects(ServerPlayer target) {
        Util.run("effect clear " + target.getUUID());
    }

    public static boolean rollback(ServerPlayer target) {
        State.Backup b = State.BACKUPS.get(target.getUUID());
        if (b == null) return false;
        State.restore(target, b);
        Util.msg(target, "Inventory restored!", ChatFormatting.GREEN);
        return true;
    }

    public static boolean giveKit(ServerPlayer target, String kit) {
        if (!Kits.give(target, kit)) return false;
        Util.msg(target, "You received the kit: " + kit, ChatFormatting.GOLD);
        return true;
    }

    /** Removes mobs (named ones are kept) and/or dropped items + XP orbs. Returns {mobs, items}. */
    public static int[] clearEntities(boolean mobs, boolean items) {
        int mobCount = 0;
        int itemCount = 0;
        for (ServerLevel level : Util.server.getAllLevels()) {
            if (mobs) {
                for (Mob e : level.getEntities(EntityTypeTest.forClass(Mob.class), x -> !x.hasCustomName())) {
                    e.discard();
                    mobCount++;
                }
            }
            if (items) {
                for (ItemEntity e : level.getEntities(EntityTypeTest.forClass(ItemEntity.class), x -> true)) {
                    e.discard();
                    itemCount++;
                }
                for (ExperienceOrb e : level.getEntities(EntityTypeTest.forClass(ExperienceOrb.class), x -> true)) {
                    e.discard();
                }
            }
        }
        return new int[] {mobCount, itemCount};
    }
}
