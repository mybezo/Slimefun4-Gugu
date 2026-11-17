package io.github.thebusybiscuit.slimefun4.implementation.compressed;

import io.github.thebusybiscuit.slimefun4.implementation.operations.BatchCraftingOperation;
import io.github.thebusybiscuit.slimefun4.implementation.operations.CraftingOperation;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.inventory.ItemStack;

/**
 * Batch processing for compressed machines whose output is random per input,
 * such as the Electric Gold Pan or the Electric Dust Washer.
 * Every input unit gets its own random output, only the units whose output fits are consumed.
 *
 * @author mybezo
 */
public final class RandomBatch {

    /**
     * How a single input unit is processed.
     *
     * @param input
     *            The input of one unit (amount 1)
     * @param seconds
     *            The duration of one unit, as passed to {@link MachineRecipe}
     * @param output
     *            Rolls the output of one unit, may return null or air when nothing is produced
     */
    public record Unit(
            @Nonnull ItemStack input, int seconds, @Nonnull Supplier<ItemStack> output) {}

    private RandomBatch() {}

    /**
     * Processes up to {@code max} units of the first valid input type at once.
     *
     * @param menu
     *            The {@link BlockMenu} of the machine
     * @param inputSlots
     *            The input slots
     * @param outputSlots
     *            The output slots
     * @param max
     *            The max amount of units
     * @param resolver
     *            Returns the {@link Unit} for an input item, or null if the item is not a valid input
     *
     * @return The operation for all the processed units, or null if nothing can be processed
     */
    @Nullable @ParametersAreNonnullByDefault
    public static CraftingOperation process(
            BlockMenu menu, int[] inputSlots, int[] outputSlots, int max, Function<ItemStack, Unit> resolver) {
        Unit unit = null;

        for (int slot : inputSlots) {
            ItemStack item = menu.getItemInSlot(slot);

            if (item != null && !item.getType().isAir()) {
                unit = resolver.apply(item);

                if (unit != null) {
                    break;
                }
            }
        }

        if (unit == null) {
            return null;
        }

        // How many units of the same input every slot holds
        int[] available = new int[inputSlots.length];
        int total = 0;

        for (int i = 0; i < inputSlots.length; i++) {
            ItemStack item = menu.getItemInSlot(inputSlots[i]);

            if (item != null && SlimefunUtils.isItemSimilar(item, unit.input(), true, false)) {
                available[i] = item.getAmount();
                total += item.getAmount();
            }
        }

        int units = Math.min(max, total);
        List<ItemStack> outputs = new ArrayList<>(units);
        int processed = 0;
        int attempts = 0;

        // An empty roll does not consume the input (like the original machines), the attempts are limited
        while (processed < units && attempts < units * 4) {
            attempts++;
            ItemStack output = unit.output().get();

            if (output == null || output.getType().isAir()) {
                continue;
            }

            outputs.add(output.clone());

            if (!CompressedOutputs.fits(menu, outputSlots, outputs)) {
                outputs.remove(outputs.size() - 1);
                break;
            }

            processed++;
        }

        if (processed == 0) {
            return null;
        }

        int remaining = processed;

        for (int i = 0; i < inputSlots.length && remaining > 0; i++) {
            int amount = Math.min(remaining, available[i]);

            if (amount > 0) {
                menu.consumeItem(inputSlots[i], amount);
                remaining -= amount;
            }
        }

        ItemStack[] ingredients = CompressedOutputs.multiply(new ItemStack[] {unit.input()}, processed)
                .toArray(new ItemStack[0]);
        int ticks = new MachineRecipe(unit.seconds(), ingredients, ingredients).getTicks();
        return new BatchCraftingOperation(ingredients, outputs.toArray(new ItemStack[0]), ticks, processed);
    }
}
