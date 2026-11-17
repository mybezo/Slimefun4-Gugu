package io.github.thebusybiscuit.slimefun4.implementation.compressed;

import io.github.bakedlibs.dough.config.Config;
import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.Capacitor;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.generators.BioGenerator;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.generators.CoalGenerator;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.generators.CombustionGenerator;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.generators.LavaGenerator;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.generators.MagnesiumGenerator;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.generators.SolarGenerator;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.AutoDrier;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.CarbonPress;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.ElectricDustWasher;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.ElectricFurnace;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.ElectricGoldPan;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.ElectricIngotFactory;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.ElectricIngotPulverizer;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.ElectricOreGrinder;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.ElectricPress;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.ElectricSmeltery;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.ElectrifiedCrucible;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.FoodComposter;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.FoodFabricator;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.Freezer;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.HeatedPressureChamber;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.Refinery;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.reactors.NetherStarReactor;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.reactors.NuclearReactor;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.reactors.Reactor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AGenerator;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Registers the compressed machines and electric devices: multiple machines combined into one.
 * They are configured in {@code compressed.yml}.
 *
 * <ul>
 * <li>Machines process up to N recipes at once (the inputs that are available), with the same speed.</li>
 * <li>Generators and reactors burn up to N fuel units at once and produce the energy of all of them.</li>
 * <li>Solar generators produce N times the energy, capacitors hold N times the energy.</li>
 * </ul>
 *
 * The recipes use the Compressor: x8 = 8 of the original, x16 = 2 of x8, x32 = 2 of x16, x64 = 2 of x32.
 *
 * @author mybezo
 */
public final class CompressedMachineSetup {

    /**
     * The multipliers, from the lowest to the highest.
     */
    public static final int[] MULTIPLIERS = {8, 16, 32, 64};

    // A number followed by " J" in a lore line (energy, buffer and capacity), color codes are kept
    private static final Pattern ENERGY_PATTERN = Pattern.compile("((?:[&§][0-9a-fk-orx])*)(\\d+)(?= J)");

    private static final Map<String, Integer> multipliers = new HashMap<>();

    private CompressedMachineSetup() {}

    /**
     * The kind of a compressed item, decides the config section and the lore.
     */
    private enum Category {
        MACHINE("machines", "Machine"),
        CAPACITOR("capacitors", "Capacitor"),
        GENERATOR("generators", "Generator"),
        REACTOR("reactors", "Reactor");

        private final String configSection;
        private final String loreName;

        Category(String configSection, String loreName) {
            this.configSection = configSection;
            this.loreName = loreName;
        }
    }

    @FunctionalInterface
    private interface MachineConstructor {
        AContainer create(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe);
    }

    @FunctionalInterface
    private interface GeneratorConstructor {
        AGenerator create(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe);
    }

    /**
     * Creates the compressed item, returns null if the original item is not of the expected type.
     */
    @FunctionalInterface
    private interface Factory {
        @Nullable SlimefunItem create(
                SlimefunItem base, ItemGroup itemGroup, SlimefunItemStack item, ItemStack[] recipe, int multiplier);
    }

    private record Entry(String id, Category category, Factory factory) {}

    /**
     * This returns how many machines are combined into the given item.
     *
     * @param item
     *            The {@link SlimefunItem}
     *
     * @return The multiplier, or 0 if the item is not a compressed item
     */
    public static int getMultiplier(@Nonnull SlimefunItem item) {
        return multipliers.getOrDefault(item.getId(), 0);
    }

    /**
     * Registers all the enabled compressed items.
     *
     * @param plugin
     *            The {@link Slimefun} instance
     */
    public static void setup(@Nonnull Slimefun plugin) {
        List<Entry> entries = entries();
        Config config = new Config(plugin, "compressed.yml");

        // Missing options are added to the file, existing values are kept
        config.setDefaultValue("enabled", true);
        for (int multiplier : MULTIPLIERS) {
            config.setDefaultValue("multipliers.x" + multiplier, true);
        }
        for (Entry entry : entries) {
            config.setDefaultValue(entry.category().configSection + '.' + entry.id(), true);
        }
        config.save();

        if (!config.getBoolean("enabled")) {
            return;
        }

        // A multiplier needs all the lower multipliers, its recipe uses the previous one
        List<Integer> enabled = new ArrayList<>();
        for (int multiplier : MULTIPLIERS) {
            if (!config.getBoolean("multipliers.x" + multiplier)) {
                break;
            }
            enabled.add(multiplier);
        }

        if (enabled.isEmpty()) {
            return;
        }

        // Only the highest multiplier of every item is listed in the guide
        ItemGroup guideGroup = new ItemGroup(
                new NamespacedKey(plugin, "compressed_machines"),
                new CustomItemStack(SlimefunItems.NETHER_STAR_REACTOR.clone(), "&aCompressed Machine & Electric"),
                5);
        // The lower multipliers are still craftable, but hidden
        ItemGroup hiddenGroup =
                new ItemGroup(
                        new NamespacedKey(plugin, "compressed_machines_items"),
                        new CustomItemStack(
                                SlimefunItems.NETHER_STAR_REACTOR.clone(), "&aCompressed Machine & Electric")) {

                    @Override
                    public boolean isVisible(@Nonnull Player p) {
                        return false;
                    }
                };

        int count = 0;

        for (Entry entry : entries) {
            if (!config.getBoolean(entry.category().configSection + '.' + entry.id())) {
                continue;
            }

            SlimefunItem base = SlimefunItem.getById(entry.id());

            if (base == null || base.isDisabled()) {
                continue;
            }

            // A multiplier that is disabled in the Items.yml also disables the multipliers above it
            List<Integer> available = new ArrayList<>(enabled.size());
            for (int multiplier : enabled) {
                String id = entry.id() + "_X" + multiplier;

                if (Slimefun.getItemCfg().contains(id + ".enabled")
                        && !Slimefun.getItemCfg().getBoolean(id + ".enabled")) {
                    break;
                }

                available.add(multiplier);
            }

            ItemStack previous = base.getItem();
            int previousMultiplier = 1;

            try {
                for (int multiplier : available) {
                    SlimefunItemStack itemStack = createItemStack(entry, base, multiplier);
                    ItemStack[] recipe = new ItemStack[9];
                    recipe[0] = new CustomItemStack(previous, multiplier / previousMultiplier);

                    ItemGroup group = multiplier == available.get(available.size() - 1) ? guideGroup : hiddenGroup;
                    SlimefunItem item = entry.factory().create(base, group, itemStack, recipe, multiplier);

                    if (item == null) {
                        break;
                    }

                    item.register(plugin);

                    if (item.isDisabled()) {
                        break;
                    }

                    multipliers.put(item.getId(), multiplier);
                    count++;

                    previous = itemStack;
                    previousMultiplier = multiplier;
                }
            } catch (Exception | LinkageError x) {
                plugin.getLogger()
                        .log(Level.SEVERE, x, () -> "Could not register the compressed versions of " + entry.id());
            }
        }

        plugin.getLogger().log(Level.INFO, "Registered {0} compressed machines and electric devices", count);
    }

    @Nonnull
    private static SlimefunItemStack createItemStack(Entry entry, SlimefunItem base, int multiplier) {
        ItemStack item = base.getItem().clone();
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(
                    base.getItemName() + ChatColor.GRAY + " (" + ChatColor.GREEN + multiplier + ChatColor.GRAY + ")");

            List<String> lore = new ArrayList<>();

            if (meta.hasLore()) {
                for (String line : meta.getLore()) {
                    lore.add(scaleEnergy(line, multiplier));
                }
            }

            // Remove trailing empty lines before adding the multiplier line
            while (!lore.isEmpty()
                    && ChatColor.stripColor(lore.get(lore.size() - 1)).isBlank()) {
                lore.remove(lore.size() - 1);
            }

            lore.add("");
            lore.add(ChatColor.GRAY + "" + multiplier + ' ' + loreName(entry) + " in 1");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return new SlimefunItemStack(entry.id() + "_X" + multiplier, item);
    }

    private static String loreName(Entry entry) {
        if (entry.category() == Category.GENERATOR && entry.id().contains("REACTOR")) {
            return Category.REACTOR.loreName;
        }

        return entry.category().loreName;
    }

    @Nonnull
    private static String scaleEnergy(@Nonnull String line, int multiplier) {
        if (!line.contains(" J")) {
            return line;
        }

        Matcher matcher = ENERGY_PATTERN.matcher(line);
        StringBuilder builder = new StringBuilder();

        while (matcher.find()) {
            long value;

            try {
                value = Long.parseLong(matcher.group(2));
            } catch (NumberFormatException x) {
                continue;
            }

            matcher.appendReplacement(builder, Matcher.quoteReplacement(matcher.group(1) + (value * multiplier)));
        }

        matcher.appendTail(builder);
        return builder.toString();
    }

    private static int scale(int value, int multiplier) {
        return (int) Math.min(Integer.MAX_VALUE, (long) value * multiplier);
    }

    @Nonnull
    private static List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();

        // Machines, the large layout (7 inputs, 21 outputs) is used by single input machines
        machine(entries, ElectricOreGrinder::new, "ELECTRIC_ORE_GRINDER", 3, true);
        machine(entries, ElectricPress::new, "ELECTRIC_PRESS", 2, true);
        machine(entries, ElectricGoldPan::new, "ELECTRIC_GOLD_PAN", 3, true);
        machine(entries, ElectricDustWasher::new, "ELECTRIC_DUST_WASHER", 3, true);
        machine(entries, ElectricIngotFactory::new, "ELECTRIC_INGOT_FACTORY", 3, false);
        machine(entries, ElectricIngotPulverizer::new, "ELECTRIC_INGOT_PULVERIZER", 1, false);
        machine(entries, ElectricFurnace::new, "ELECTRIC_FURNACE", 3, true);
        machine(entries, ElectricSmeltery::new, "ELECTRIC_SMELTERY", 2, false);
        machine(entries, CarbonPress::new, "CARBON_PRESS", 3, true);
        machine(entries, HeatedPressureChamber::new, "HEATED_PRESSURE_CHAMBER", 2, false);
        machine(entries, ElectrifiedCrucible::new, "ELECTRIFIED_CRUCIBLE", 3, false);
        machine(entries, Refinery::new, "REFINERY", 1, false);
        machine(entries, Freezer::new, "FREEZER", 3, true);
        machine(entries, AutoDrier::new, "AUTO_DRIER", 1, false);
        machine(entries, FoodFabricator::new, "FOOD_FABRICATOR", 2, false);
        machine(entries, FoodComposter::new, "FOOD_COMPOSTER", 2, false);

        // Capacitors
        for (String id : List.of(
                "SMALL_CAPACITOR",
                "MEDIUM_CAPACITOR",
                "BIG_CAPACITOR",
                "LARGE_CAPACITOR",
                "CARBONADO_EDGED_CAPACITOR",
                "ENERGIZED_CAPACITOR")) {
            entries.add(new Entry(id, Category.CAPACITOR, (base, group, item, recipe, multiplier) -> {
                if (!(base instanceof Capacitor capacitor)) {
                    return null;
                }

                return new Capacitor(
                        group, scale(capacitor.getCapacity(), multiplier), item, RecipeType.COMPRESSOR, recipe);
            }));
        }

        // Generators
        generator(entries, BioGenerator::new, "BIO_REACTOR");
        for (String id : List.of("SOLAR_GENERATOR", "SOLAR_GENERATOR_2", "SOLAR_GENERATOR_3", "SOLAR_GENERATOR_4")) {
            entries.add(new Entry(id, Category.GENERATOR, (base, group, item, recipe, multiplier) -> {
                if (!(base instanceof SolarGenerator solar)) {
                    return null;
                }

                return new SolarGenerator(
                        group,
                        scale(solar.getDayEnergy(), multiplier),
                        scale(solar.getNightEnergy(), multiplier),
                        item,
                        RecipeType.COMPRESSOR,
                        recipe,
                        scale(solar.getCapacity(), multiplier));
            }));
        }
        generator(entries, CoalGenerator::new, "COAL_GENERATOR");
        generator(entries, CoalGenerator::new, "COAL_GENERATOR_2");
        generator(entries, LavaGenerator::new, "LAVA_GENERATOR");
        generator(entries, LavaGenerator::new, "LAVA_GENERATOR_2");
        generator(entries, MagnesiumGenerator::new, "MAGNESIUM_GENERATOR");
        generator(entries, CombustionGenerator::new, "COMBUSTION_REACTOR");

        // Reactors
        entries.add(new Entry("NUCLEAR_REACTOR", Category.REACTOR, (base, group, item, recipe, multiplier) -> {
            if (!(base instanceof Reactor reactor)) {
                return null;
            }

            int energy = reactor.getEnergyProduction();
            int capacity = scale(reactor.getCapacity(), multiplier);
            NuclearReactor compressed = new NuclearReactor(group, item, RecipeType.COMPRESSOR, recipe) {

                @Override
                public int getEnergyProduction() {
                    return energy;
                }

                @Override
                public int getCapacity() {
                    return capacity;
                }
            };
            compressed.setCompressedMultiplier(multiplier);
            return compressed;
        }));
        entries.add(new Entry("NETHERSTAR_REACTOR", Category.REACTOR, (base, group, item, recipe, multiplier) -> {
            if (!(base instanceof Reactor reactor)) {
                return null;
            }

            int energy = reactor.getEnergyProduction();
            int capacity = scale(reactor.getCapacity(), multiplier);
            NetherStarReactor compressed = new NetherStarReactor(group, item, RecipeType.COMPRESSOR, recipe) {

                @Override
                public int getEnergyProduction() {
                    return energy;
                }

                @Override
                public int getCapacity() {
                    return capacity;
                }
            };
            compressed.setCompressedMultiplier(multiplier);
            return compressed;
        }));

        return Collections.unmodifiableList(entries);
    }

    @ParametersAreNonnullByDefault
    private static void machine(
            List<Entry> entries, MachineConstructor constructor, String id, int tiers, boolean largeLayout) {
        for (int tier = 1; tier <= tiers; tier++) {
            String tierId = tier == 1 ? id : id + '_' + tier;

            entries.add(new Entry(tierId, Category.MACHINE, (base, group, item, recipe, multiplier) -> {
                if (!(base instanceof AContainer machine)) {
                    return null;
                }

                AContainer compressed = constructor.create(group, item, RecipeType.COMPRESSOR, recipe);
                compressed.setCapacity(scale(machine.getCapacity(), multiplier));
                compressed.setEnergyConsumption(machine.getEnergyConsumption());
                compressed.setProcessingSpeed(machine.getSpeed());
                compressed.setCompressed(machine, multiplier, largeLayout);
                return compressed;
            }));
        }
    }

    @ParametersAreNonnullByDefault
    private static void generator(List<Entry> entries, GeneratorConstructor constructor, String id) {
        entries.add(new Entry(id, Category.GENERATOR, (base, group, item, recipe, multiplier) -> {
            if (!(base instanceof AGenerator generator)) {
                return null;
            }

            AGenerator compressed = constructor.create(group, item, RecipeType.COMPRESSOR, recipe);
            compressed.setCapacity(scale(generator.getCapacity(), multiplier));
            compressed.setEnergyProduction(generator.getEnergyProduction());
            compressed.setCompressedMultiplier(multiplier);
            return compressed;
        }));
    }
}
