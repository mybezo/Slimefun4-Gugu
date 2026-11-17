package io.github.thebusybiscuit.slimefun4.implementation.operations;

import javax.annotation.Nonnull;
import org.bukkit.inventory.ItemStack;

/**
 * A {@link CraftingOperation} that processes multiple recipes at once.
 * It is used by compressed machines: the energy consumption per tick
 * is multiplied by the amount of recipes in this batch.
 *
 * @author mybezo
 */
public class BatchCraftingOperation extends CraftingOperation {

    private final int batch;

    public BatchCraftingOperation(
            @Nonnull ItemStack[] ingredients, @Nonnull ItemStack[] results, int totalTicks, int batch) {
        super(ingredients, results, totalTicks);
        this.batch = Math.max(1, batch);
    }

    /**
     * This returns how many recipes are processed by this operation.
     *
     * @return The amount of recipes in this batch
     */
    public int getBatch() {
        return batch;
    }
}
