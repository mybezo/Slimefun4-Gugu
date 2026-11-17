package io.github.thebusybiscuit.slimefun4.implementation.items.electric;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetProvider;
import io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.reactors.Reactor;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AGenerator;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineFuel;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.interfaces.InventoryBlock;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.apache.commons.lang.Validate;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * This is an abstract super class for machines that produce energy.
 *
 * @author TheBusyBiscuit
 * @see AGenerator
 * @see Reactor
 */
public abstract class AbstractEnergyProvider extends SlimefunItem
        implements InventoryBlock, RecipeDisplayItem, EnergyNetProvider {

    /**
     * The set of fuel types for this energy provider.
     */
    protected final Set<MachineFuel> fuelTypes = new HashSet<>();

    /**
     * The block data key that stores how many fuel units a compressed energy provider is burning.
     */
    protected static final String COMPRESSED_BATCH_KEY = "compressed-batch";

    /**
     * The amount of energy providers combined into this one (compressed generators), 1 for a normal one.
     */
    private int compressedMultiplier = 1;

    /**
     * Constructs a new AbstractEnergyProvider.
     *
     * @param itemGroup   The item group this item belongs to
     * @param item        The item stack for this energy provider
     * @param recipeType  The recipe type used to craft this item
     * @param recipe      The recipe to craft this item
     */
    @ParametersAreNonnullByDefault
    protected AbstractEnergyProvider(
            ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    /**
     * This method returns the title that is used for the {@link Inventory} of an
     * {@link AbstractEnergyProvider} that has been opened by a Player.
     *
     * Override this method to set the title.
     *
     * @return The title of the {@link Inventory} of this {@link AbstractEnergyProvider}
     */
    @Nonnull
    public String getInventoryTitle() {
        return getItemName();
    }

    /**
     * This method returns the {@link ItemStack} that this {@link AGenerator} will
     * use as a progress bar.
     *
     * Override this method to set the progress bar.
     *
     * @return The {@link ItemStack} to use as the progress bar
     */
    @Nonnull
    public abstract ItemStack getProgressBar();

    /**
     * This method returns the amount of energy that is produced per tick.
     *
     * @return The rate of energy generation
     */
    public abstract int getEnergyProduction();

    /**
     * This method is used to register the default fuel types.
     */
    protected abstract void registerDefaultFuelTypes();

    @Override
    @Nonnull
    public final EnergyNetComponentType getEnergyComponentType() {
        return EnergyNetComponentType.GENERATOR;
    }

    /**
     * Registers a fuel type for this energy provider.
     *
     * @param fuel The {@link MachineFuel} to register
     */
    public void registerFuel(@Nonnull MachineFuel fuel) {
        Validate.notNull(fuel, "Machine Fuel cannot be null!");
        fuelTypes.add(fuel);
    }

    /**
     * Gets the set of fuel types for this energy provider.
     *
     * @return The set of {@link MachineFuel} types
     */
    @Nonnull
    public Set<MachineFuel> getFuelTypes() {
        return fuelTypes;
    }

    @Override
    public String getLabelLocalPath() {
        return "guide.tooltips.recipes.generator";
    }

    @Override
    public List<ItemStack> getDisplayRecipes() {
        List<ItemStack> list = new ArrayList<>();

        for (MachineFuel fuel : fuelTypes) {
            ItemStack item = fuel.getInput().clone();
            ItemMeta im = item.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add(ChatColors.color("&8\u21E8 &7Lasts " + NumberUtils.getTimeLeft(fuel.getTicks() / 2)));
            lore.add(ChatColors.color("&8\u21E8 &e\u26A1 &7" + getEnergyProduction() * 2) + " J/s");
            lore.add(ChatColors.color("&8\u21E8 &e\u26A1 &7Max. Capacity: "
                    + NumberUtils.getCompactDouble((double) fuel.getTicks() * getEnergyProduction())
                    + " J"));
            im.setLore(lore);
            item.setItemMeta(im);
            list.add(item);
        }

        return list;
    }

    /**
     * This turns this energy provider into a compressed one: multiple energy providers combined into one.
     * It burns up to {@code multiplier} fuel units at once and produces the energy of all of them.
     * This method <strong>must</strong> be called before registering the item.
     *
     * @param multiplier
     *            The amount of energy providers combined into this one
     */
    public final void setCompressedMultiplier(int multiplier) {
        Validate.isTrue(multiplier > 0, "The multiplier must be greater than zero!");
        this.compressedMultiplier = multiplier;
    }

    /**
     * This returns how many energy providers are combined into this one.
     *
     * @return The multiplier of this compressed energy provider, 1 for a normal one
     */
    public int getCompressedMultiplier() {
        return compressedMultiplier;
    }

    /**
     * This returns how many fuel units are burning in the current cycle, stored in the block data.
     *
     * @param data
     *            The block data
     *
     * @return The amount of fuel units, at least 1
     */
    protected int getCompressedBatch(@Nonnull ASlimefunDataContainer data) {
        if (compressedMultiplier <= 1) {
            return 1;
        }

        String value = data.getData(COMPRESSED_BATCH_KEY);

        if (value == null) {
            return 1;
        }

        try {
            return Math.max(1, Math.min(compressedMultiplier, Integer.parseInt(value)));
        } catch (NumberFormatException x) {
            return 1;
        }
    }

    /**
     * This stores how many fuel units are burning in the current cycle.
     *
     * @param data
     *            The block data
     * @param batch
     *            The amount of fuel units
     */
    protected void setCompressedBatch(@Nonnull ASlimefunDataContainer data, int batch) {
        if (compressedMultiplier > 1) {
            data.setData(COMPRESSED_BATCH_KEY, String.valueOf(batch));
        }
    }

    /**
     * This counts how many units of the given fuel are in the given slots.
     *
     * @param menu
     *            The {@link BlockMenu}
     * @param slots
     *            The slots to check
     * @param fuel
     *            The fuel
     *
     * @return The amount of fuel units
     */
    @ParametersAreNonnullByDefault
    protected int countFuelUnits(BlockMenu menu, int[] slots, MachineFuel fuel) {
        int perUnit = Math.max(1, fuel.getInput().getAmount());
        int units = 0;

        for (int slot : slots) {
            ItemStack item = menu.getItemInSlot(slot);

            if (item != null && fuel.test(item)) {
                units += item.getAmount() / perUnit;
            }
        }

        return units;
    }

    /**
     * This consumes the given amount of fuel units from the given slots.
     *
     * @param menu
     *            The {@link BlockMenu}
     * @param slots
     *            The slots to consume from
     * @param fuel
     *            The fuel
     * @param units
     *            The amount of fuel units to consume
     */
    @ParametersAreNonnullByDefault
    protected void consumeFuelUnits(BlockMenu menu, int[] slots, MachineFuel fuel, int units) {
        int perUnit = Math.max(1, fuel.getInput().getAmount());
        int remaining = units;

        for (int slot : slots) {
            if (remaining <= 0) {
                break;
            }

            ItemStack item = menu.getItemInSlot(slot);

            if (item != null && fuel.test(item)) {
                int amount = Math.min(remaining, item.getAmount() / perUnit);

                if (amount > 0) {
                    menu.consumeItem(slot, amount * perUnit);
                    remaining -= amount;
                }
            }
        }
    }
}
