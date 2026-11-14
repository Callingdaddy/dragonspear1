package com.sankiextra.dragonspear;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** DragonSpear - Recording Helper (Fabric 1.21.11). Server-side only features, no client mod needed. */
public final class DragonSpearFabric implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("dragonspear");

    private static final List<String> GAMEMODES = List.of("survival", "creative", "adventure", "spectator");

    @Override
    public void onInitialize() {
        registerLifecycle();
        registerEvents();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // Same aliases as the Paper plugin
            for (String name : new String[] {"rh", "recordinghelper", "recorder", "rechelp"}) {
                dispatcher.register(buildRh(name));
            }
        });
        LOGGER.info("DragonSpear (Recording Helper) loaded");
    }

    // ================================================================= lifecycle

    private static void registerLifecycle() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Util.server = server;
            State.load(server);
            Kits.createStarterKits(server);
            State.save();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> State.save());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> Util.server = null);
    }

    // ================================================================= events

    private static void registerEvents() {
        // God mode, no-PVP
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer p) {
                if (Feature.GOD.has(p)) return false;
                if (!State.pvp && source.getEntity() instanceof ServerPlayer attacker && attacker != p) return false;
            }
            return true;
        });

        // Immortality + death backup (for Inventory Rollback / Keep Inventory)
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer p) {
                if (Feature.IMMORTAL.has(p)) {
                    p.setHealth(1.0F);
                    return false;
                }
                State.BACKUPS.put(p.getUUID(), State.snapshot(p));
            }
            return true;
        });

        // Keep Inventory: delete what was dropped. Hardcore: ban
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer p)) return;
            if (Feature.KEEPINV.has(p)) {
                State.removeDeathDrops(p);
            }
            if (State.hardcore && Util.server != null) {
                final String name = Util.name(p);
                Util.server.execute(() -> Util.run("ban " + name + " Died in HARDCORE mode"));
            }
        });

        // Keep Inventory: give the items back after respawn, and re-apply toggles
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive && Feature.KEEPINV.has(newPlayer)) {
                State.Backup b = State.BACKUPS.get(newPlayer.getUUID());
                if (b != null) {
                    State.restore(newPlayer, b);
                }
            }
            Util.reapply(newPlayer);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Util.reapply(handler.getPlayer()));

        // Chat toggle
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (!State.chat) {
                Util.msg(sender, "Chat is currently disabled!", ChatFormatting.RED);
                return false;
            }
            return true;
        });

        // Flight can get reset by gamemode changes; check once per second
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (Feature.FLY.has(p) && !p.getAbilities().mayfly) {
                    Feature.FLY.apply(p, true);
                }
            }
        });
    }

    // ================================================================= commands

    private static void ok(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GREEN), false);
    }

    private static void info(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }

    private static int fail(CommandSourceStack src, String text) {
        src.sendFailure(Component.literal(text));
        return 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> toggle(String literal, Feature feature) {
        return Commands.literal(literal)
                .executes(ctx -> {
                    feature.toggle(ctx.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> {
                            int n = 0;
                            for (ServerPlayer t : EntityArgument.getPlayers(ctx, "targets")) {
                                feature.toggle(t);
                                n++;
                            }
                            return n;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRh(String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    Menus.openMain(ctx.getSource().getPlayerOrException());
                    return 1;
                });

        root.then(Commands.literal("help").executes(ctx -> help(ctx.getSource())));

        // toggles: /rh god [players] ...
        root.then(toggle("god", Feature.GOD));
        root.then(toggle("immortal", Feature.IMMORTAL));
        root.then(toggle("fly", Feature.FLY));
        root.then(toggle("vanish", Feature.VANISH));
        root.then(toggle("freeze", Feature.FREEZE));
        root.then(toggle("keepinv", Feature.KEEPINV));

        // /rh heal [players]
        root.then(Commands.literal("heal")
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Util.heal(p);
                    Util.msg(p, "You have been healed.", ChatFormatting.GREEN);
                    return 1;
                })
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> {
                            int n = 0;
                            for (ServerPlayer t : EntityArgument.getPlayers(ctx, "targets")) {
                                Util.heal(t);
                                Util.msg(t, "You have been healed.", ChatFormatting.GREEN);
                                n++;
                            }
                            return n;
                        })));

        // /rh gm <mode> [players]
        root.then(Commands.literal("gm")
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(GAMEMODES, builder))
                        .executes(ctx -> {
                            String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
                            if (!GAMEMODES.contains(mode)) return fail(ctx.getSource(), "Use survival, creative, adventure or spectator.");
                            Actions.gamemode(ctx.getSource().getPlayerOrException(), mode);
                            return 1;
                        })
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> {
                                    String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
                                    if (!GAMEMODES.contains(mode)) return fail(ctx.getSource(), "Use survival, creative, adventure or spectator.");
                                    int n = 0;
                                    for (ServerPlayer t : EntityArgument.getPlayers(ctx, "targets")) {
                                        Actions.gamemode(t, mode);
                                        n++;
                                    }
                                    return n;
                                }))));

        // /rh time <day|night>
        root.then(Commands.literal("time")
                .then(Commands.literal("day").executes(ctx -> {
                    Actions.setTime("day");
                    ok(ctx.getSource(), "Time set to day.");
                    return 1;
                }))
                .then(Commands.literal("night").executes(ctx -> {
                    Actions.setTime("night");
                    ok(ctx.getSource(), "Time set to night.");
                    return 1;
                })));

        // /rh weather <clear|rain|thunder|unlock>
        LiteralArgumentBuilder<CommandSourceStack> weather = Commands.literal("weather");
        for (String w : new String[] {"clear", "rain", "thunder"}) {
            weather.then(Commands.literal(w).executes(ctx -> {
                Actions.setWeather(w);
                ok(ctx.getSource(), "Weather locked to " + w + ".");
                return 1;
            }));
        }
        weather.then(Commands.literal("unlock").executes(ctx -> {
            Actions.setWeather("");
            ok(ctx.getSource(), "Weather unlocked.");
            return 1;
        }));
        root.then(weather);

        // /rh clear <mobs|items|all>  and  /rh clearitems
        root.then(Commands.literal("clear")
                .then(Commands.literal("mobs").executes(ctx -> clear(ctx.getSource(), true, false)))
                .then(Commands.literal("items").executes(ctx -> clear(ctx.getSource(), false, true)))
                .then(Commands.literal("all").executes(ctx -> clear(ctx.getSource(), true, true))));
        root.then(Commands.literal("clearitems").executes(ctx -> clear(ctx.getSource(), false, true)));

        // kits
        root.then(Commands.literal("createkit")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            String kit = StringArgumentType.getString(ctx, "name");
                            Kits.createFromInventory(kit, p);
                            ok(ctx.getSource(), "Kit '" + kit + "' created from your inventory.");
                            return 1;
                        })));
        root.then(Commands.literal("delkit")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Kits.names(), builder))
                        .executes(ctx -> {
                            String kit = StringArgumentType.getString(ctx, "name");
                            if (!Kits.delete(kit)) return fail(ctx.getSource(), "Kit not found!");
                            ok(ctx.getSource(), "Kit '" + kit + "' deleted.");
                            return 1;
                        })));
        root.then(Commands.literal("kits").executes(ctx -> {
            List<String> names = Kits.names();
            info(ctx.getSource(), names.isEmpty() ? "No kits yet. Use /rh createkit <name>" : "Kits: " + String.join(", ", names));
            return names.size();
        }));
        root.then(Commands.literal("kit")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Kits.names(), builder))
                        .executes(ctx -> {
                            String kit = StringArgumentType.getString(ctx, "name");
                            if (!Actions.giveKit(ctx.getSource().getPlayerOrException(), kit)) return fail(ctx.getSource(), "Kit not found!");
                            return 1;
                        })
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> {
                                    String kit = StringArgumentType.getString(ctx, "name");
                                    if (!Kits.exists(kit)) return fail(ctx.getSource(), "Kit not found!");
                                    int n = 0;
                                    for (ServerPlayer t : EntityArgument.getPlayers(ctx, "targets")) {
                                        if (Actions.giveKit(t, kit)) n++;
                                    }
                                    ok(ctx.getSource(), "Gave '" + kit + "' to " + n + " player(s).");
                                    return n;
                                }))));

        // /rh rollback <player>
        root.then(Commands.literal("rollback")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer t = EntityArgument.getPlayer(ctx, "target");
                            if (!Actions.rollback(t)) return fail(ctx.getSource(), Util.name(t) + " has no death backup yet.");
                            ok(ctx.getSource(), "Restored " + Util.name(t) + "'s inventory.");
                            return 1;
                        })));

        // /rh setskin <player> <skin>  (not possible server-side without extra tricks)
        root.then(Commands.literal("setskin")
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("skin", StringArgumentType.word())
                                .executes(ctx -> fail(ctx.getSource(), "Skin changing is not available in the Fabric version yet.")))));

        return root;
    }

    private static int clear(CommandSourceStack src, boolean mobs, boolean items) {
        int[] r = Actions.clearEntities(mobs, items);
        ok(src, "Removed " + r[0] + " mob(s) and " + r[1] + " item(s).");
        return r[0] + r[1];
    }

    private static int help(CommandSourceStack src) {
        info(src, "=== DragonSpear - Recording Helper ===");
        info(src, "/rh  - open the GUI menu (aliases: /recorder, /rechelp, /recordinghelper)");
        info(src, "/rh god|immortal|fly|vanish|freeze|keepinv [players]  - toggle");
        info(src, "/rh heal [players]");
        info(src, "/rh gm <survival|creative|adventure|spectator> [players]");
        info(src, "/rh time <day|night>   /rh weather <clear|rain|thunder|unlock>");
        info(src, "/rh clear <mobs|items|all>");
        info(src, "/rh createkit <name>   /rh kit <name> [players]   /rh kits   /rh delkit <name>");
        info(src, "/rh rollback <player>  - restore inventory from last death");
        return 1;
    }
}
