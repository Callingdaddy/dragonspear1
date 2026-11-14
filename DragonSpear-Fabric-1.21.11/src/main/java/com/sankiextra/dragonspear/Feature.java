package com.sankiextra.dragonspear;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player toggles (the same ones the Paper plugin had). */
public enum Feature {
    GOD("God Mode", "godmode"),
    IMMORTAL("Immortality", "immortal"),
    FLY("Creative Flight", "fly"),
    VANISH("Vanish Mode", "vanish"),
    FREEZE("Freeze", "freeze"),
    KEEPINV("Keep Inventory", "keepinv");

    public final String label;
    public final String id;
    public final Set<UUID> players = ConcurrentHashMap.newKeySet();

    Feature(String label, String id) {
        this.label = label;
        this.id = id;
    }

    public boolean has(ServerPlayer p) {
        return players.contains(p.getUUID());
    }

    /** Flips the toggle and returns the new state. */
    public boolean toggle(ServerPlayer p) {
        boolean on = !has(p);
        set(p, on);
        return on;
    }

    public void set(ServerPlayer p, boolean on) {
        if (on) {
            players.add(p.getUUID());
        } else {
            players.remove(p.getUUID());
        }
        apply(p, on);
        Util.msg(p, label + (on ? " enabled." : " disabled."), on ? ChatFormatting.GREEN : ChatFormatting.RED);
        State.save();
    }

    /** Makes the player's real state match this toggle. Also used on join/respawn. */
    public void apply(ServerPlayer p, boolean on) {
        String uuid = p.getUUID().toString();
        switch (this) {
            case FLY -> {
                var abilities = p.getAbilities();
                if (on) {
                    if (!abilities.mayfly) {
                        abilities.mayfly = true;
                        p.onUpdateAbilities();
                    }
                } else if (!p.isCreative() && !p.isSpectator()) {
                    abilities.mayfly = false;
                    abilities.flying = false;
                    p.onUpdateAbilities();
                }
            }
            case VANISH -> {
                if (on) {
                    Util.run("effect give " + uuid + " minecraft:invisibility infinite 0 true");
                } else {
                    Util.run("effect clear " + uuid + " minecraft:invisibility");
                }
            }
            case FREEZE -> {
                // Movement speed 0 + jump strength 0 = the player cannot walk or jump (they can still look around).
                Util.run("attribute " + uuid + " minecraft:movement_speed base set " + (on ? "0" : "0.1"));
                Util.run("attribute " + uuid + " minecraft:jump_strength base set " + (on ? "0" : "0.42"));
            }
            default -> {
                // GOD, IMMORTAL, KEEPINV are handled by the event listeners in DragonSpearFabric.
            }
        }
    }
}
