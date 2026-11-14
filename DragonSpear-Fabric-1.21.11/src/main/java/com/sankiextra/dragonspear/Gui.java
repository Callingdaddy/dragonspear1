package com.sankiextra.dragonspear;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * A server-side chest GUI. Vanilla clients see a normal chest, so no client mod is needed.
 * Every click is cancelled; only our own click handlers run.
 */
public final class Gui extends ChestMenu {

    @FunctionalInterface
    public interface Click {
        void on(ServerPlayer player, boolean rightClick);
    }

    private final Click[] handlers;
    private final int slotCount;

    private Gui(MenuType<?> type, int syncId, Inventory inventory, SimpleContainer container, int rows, Click[] handlers) {
        super(type, syncId, inventory, container, rows);
        this.handlers = handlers;
        this.slotCount = rows * 9;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < slotCount && player instanceof ServerPlayer sp
                && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE)) {
            Click handler = handlers[slotId];
            if (handler != null) {
                try {
                    handler.on(sp, button == 1);
                } catch (Exception e) {
                    DragonSpearFabric.LOGGER.error("Error in GUI click handler", e);
                }
            }
        }
        // Undo anything the client predicted (picked-up items etc.)
        if (player.containerMenu == this) {
            this.sendAllDataToRemote();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    // ---------------------------------------------------------------- builder

    public static final class Builder {
        private final Component title;
        private final int rows;
        private final ItemStack[] stacks;
        private final Click[] clicks;

        public Builder(Component title, int rows) {
            this.title = title;
            this.rows = rows;
            this.stacks = new ItemStack[rows * 9];
            this.clicks = new Click[rows * 9];
        }

        public Builder set(int slot, ItemStack stack, Click click) {
            if (slot >= 0 && slot < stacks.length) {
                stacks[slot] = stack;
                clicks[slot] = click;
            }
            return this;
        }

        public Builder set(int slot, ItemStack stack) {
            return set(slot, stack, null);
        }

        /** Fills every still-empty slot with the given (decoration) item. */
        public Builder fillEmpty(ItemStack filler) {
            for (int i = 0; i < stacks.length; i++) {
                if (stacks[i] == null) stacks[i] = filler;
            }
            return this;
        }

        public void open(ServerPlayer player) {
            final MenuType<?> type = switch (rows) {
                case 1 -> MenuType.GENERIC_9x1;
                case 2 -> MenuType.GENERIC_9x2;
                case 3 -> MenuType.GENERIC_9x3;
                case 4 -> MenuType.GENERIC_9x4;
                case 5 -> MenuType.GENERIC_9x5;
                default -> MenuType.GENERIC_9x6;
            };
            player.openMenu(new SimpleMenuProvider((syncId, inventory, p) -> {
                SimpleContainer container = new SimpleContainer(rows * 9);
                for (int i = 0; i < stacks.length; i++) {
                    container.setItem(i, stacks[i] == null ? ItemStack.EMPTY : stacks[i].copy());
                }
                return new Gui(type, syncId, inventory, container, rows, clicks);
            }, title));
        }
    }
}
