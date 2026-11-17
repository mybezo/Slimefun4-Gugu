package io.github.thebusybiscuit.slimefun4.implementation.compressed;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.inventory.ItemStack;

/**
 * Helpers for the outputs of compressed machines.
 * A compressed machine produces the output of multiple machines at once,
 * so its outputs have to be split into valid stacks and checked against the free space before
 * any input is consumed.
 *
 * @author mybezo
 */
public final class CompressedOutputs {

    private CompressedOutputs() {}

    /**
     * Multiplies the given items by the given amount and splits the result into valid stacks.
     * A stack is never larger than the max stack size of its item.
     *
     * @param items
     *            The items of a single operation
     * @param times
     *            How many operations are combined
     *
     * @return The combined items, as valid stacks
     */
    @Nonnull
    public static List<ItemStack> multiply(@Nonnull ItemStack[] items, int times) {
        List<ItemStack> result = new ArrayList<>();

        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                split(item, (long) item.getAmount() * times, result);
            }
        }

        return result;
    }

    /**
     * Splits the given total amount of an item into valid stacks.
     *
     * @param item
     *            The item
     * @param total
     *            The total amount
     * @param result
     *            The list to add the stacks to
     */
    @ParametersAreNonnullByDefault
    public static void split(ItemStack item, long total, List<ItemStack> result) {
        int max = Math.max(1, item.getMaxStackSize());
        long remaining = total;

        while (remaining > 0) {
            int amount = (int) Math.min(remaining, max);
            ItemStack stack = item.clone();
            stack.setAmount(amount);
            result.add(stack);
            remaining -= amount;
        }
    }

    /**
     * Checks whether all the given stacks fit into the given slots.
     * This simulates {@link BlockMenu#pushItem(ItemStack, int...)}, without changing the menu.
     *
     * @param menu
     *            The {@link BlockMenu}
     * @param slots
     *            The output slots
     * @param stacks
     *            The stacks to insert
     *
     * @return Whether all the stacks fit
     */
    @ParametersAreNonnullByDefault
    public static boolean fits(BlockMenu menu, int[] slots, List<ItemStack> stacks) {
        return fits(menu::getItemInSlot, slots, stacks);
    }

    /**
     * Checks whether all the given stacks fit into the given slots.
     *
     * @param inventory
     *            Returns the current item in a slot
     * @param slots
     *            The output slots
     * @param stacks
     *            The stacks to insert
     *
     * @return Whether all the stacks fit
     */
    @ParametersAreNonnullByDefault
    public static boolean fits(IntFunction<ItemStack> inventory, int[] slots, List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return true;
        }

        // A copy of the slot contents, only the copy is changed
        ItemStack[] contents = new ItemStack[slots.length];
        int[] amounts = new int[slots.length];

        for (int i = 0; i < slots.length; i++) {
            ItemStack item = inventory.apply(slots[i]);

            if (item != null && !item.getType().isAir()) {
                contents[i] = item;
                amounts[i] = item.getAmount();
            }
        }

        for (ItemStack stack : stacks) {
            int remaining = stack.getAmount();
            int max = Math.min(Math.max(1, stack.getMaxStackSize()), 64);

            for (int i = 0; i < slots.length && remaining > 0; i++) {
                if (contents[i] == null) {
                    contents[i] = stack;
                    int moved = Math.min(remaining, max);
                    amounts[i] = moved;
                    remaining -= moved;
                } else if (amounts[i] < max && isSame(contents[i], stack)) {
                    int moved = Math.min(remaining, max - amounts[i]);
                    amounts[i] += moved;
                    remaining -= moved;
                }
            }

            if (remaining > 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Finds the highest amount of operations (up to the given maximum) whose outputs fit into the slots.
     *
     * @param menu
     *            The {@link BlockMenu}
     * @param slots
     *            The output slots
     * @param outputs
     *            The outputs of a single operation
     * @param max
     *            The max amount of operations
     *
     * @return The amount of operations that fit, 0 if not even one operation fits
     */
    @ParametersAreNonnullByDefault
    public static int maxFitting(BlockMenu menu, int[] slots, ItemStack[] outputs, int max) {
        int low = 0;
        int high = max;

        while (low < high) {
            int mid = (low + high + 1) / 2;

            if (fits(menu, slots, multiply(outputs, mid))) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }

        return low;
    }

    /**
     * Pushes all the given stacks into the given slots.
     *
     * @param menu
     *            The {@link BlockMenu}
     * @param slots
     *            The output slots
     * @param stacks
     *            The stacks to insert
     */
    @ParametersAreNonnullByDefault
    public static void push(BlockMenu menu, int[] slots, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            menu.pushItem(stack.clone(), slots);
        }
    }

    private static boolean isSame(@Nullable ItemStack a, @Nullable ItemStack b) {
        if (a == b) {
            return true;
        }

        if (a == null || b == null || a.getType() != b.getType()) {
            return false;
        }

        /*
         * Identical items always stack in a BlockMenu.
         * This is stricter than the menu itself, so a batch is never larger than what really fits.
         */
        return a.isSimilar(b);
    }
}
