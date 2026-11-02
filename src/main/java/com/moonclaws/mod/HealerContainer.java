package com.moonclaws.mod;

import java.util.Arrays;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IWorldPosCallable;

/**
 * Healer's Stone window: 3 herb slots, 1 cobweb slot, result. Ingredients go back to the player when closed
 * (like the crafting table).
 */
public class HealerContainer extends Container {

    public static final int HERB_1 = 0;
    public static final int WEB = 3;
    public static final int RESULT = 4;
    private static final int INV_START = 5;
    private static final int INV_END = 41; // exclusive
    private static final int HOTBAR_START = 32;

    private final Inventory ingredients = new Inventory(4);
    private final CraftResultInventory resultSlots = new CraftResultInventory();
    private final IWorldPosCallable access;
    private final PlayerEntity player;

    /** Client side. */
    public HealerContainer(int id, PlayerInventory inventory) {
        this(id, inventory, IWorldPosCallable.NULL);
    }

    public HealerContainer(int id, PlayerInventory inventory, IWorldPosCallable access) {
        super(MoonclawsMod.HEALER_CONTAINER.get(), id);
        this.access = access;
        this.player = inventory.player;
        ingredients.addListener(inv -> this.slotsChanged(inv));

        for (int i = 0; i < 3; i++) {
            this.addSlot(new Slot(ingredients, i, 26 + i * 20, 20) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return Medicine.isHerb(stack);
                }
            });
        }
        this.addSlot(new Slot(ingredients, WEB, 46, 46) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Medicine.isWeb(stack);
            }
        });
        this.addSlot(new Slot(resultSlots, 0, 124, 33) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public ItemStack onTake(PlayerEntity p, ItemStack stack) {
                consumeIngredients();
                return super.onTake(p, stack);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    public ItemStack getIngredient(int index) {
        return ingredients.getItem(index);
    }

    private ItemStack computeResult() {
        return Medicine.result(CatState.getRank(player),
                Arrays.asList(ingredients.getItem(0), ingredients.getItem(1), ingredients.getItem(2)),
                ingredients.getItem(WEB));
    }

    private void consumeIngredients() {
        boolean webUsed = Medicine.hasWeb(resultSlots.getItem(0)) || CatState.getRank(player) == CatState.RANK_HEALER;
        for (int i = 0; i < 3; i++) {
            if (!ingredients.getItem(i).isEmpty()) {
                ingredients.removeItem(i, 1);
            }
        }
        if (webUsed && !ingredients.getItem(WEB).isEmpty()) {
            ingredients.removeItem(WEB, 1);
        }
        this.slotsChanged(ingredients);
    }

    @Override
    public void slotsChanged(net.minecraft.inventory.IInventory inventory) {
        if (!player.level.isClientSide) {
            resultSlots.setItem(0, computeResult());
            this.broadcastChanges();
        }
        super.slotsChanged(inventory);
    }

    @Override
    public boolean stillValid(PlayerEntity p) {
        return stillValid(access, p, MoonclawsMod.HEALER_STONE.get());
    }

    @Override
    public void removed(PlayerEntity p) {
        super.removed(p);
        this.access.execute((world, pos) -> this.clearContainer(p, world, ingredients));
    }

    @Override
    public ItemStack quickMoveStack(PlayerEntity p, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == RESULT) {
            if (!this.moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, copy);
        } else if (index < INV_START) {
            if (!this.moveItemStackTo(stack, INV_START, INV_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (Medicine.isHerb(stack)) {
            if (!this.moveItemStackTo(stack, HERB_1, WEB, false)) {
                return ItemStack.EMPTY;
            }
        } else if (Medicine.isWeb(stack)) {
            if (!this.moveItemStackTo(stack, WEB, RESULT, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < HOTBAR_START) {
            if (!this.moveItemStackTo(stack, HOTBAR_START, INV_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, INV_START, HOTBAR_START, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = slot.onTake(p, stack);
        if (index == RESULT) {
            p.drop(taken, false);
        }
        return copy;
    }
}
