package io.github.thebusybiscuit.slimefun4.implementation.compressed;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class CompressedOutputsTest {

    private static final int[] SLOTS = {0, 1, 2};

    @BeforeAll
    static void load() {
        MockBukkit.mock();
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Multiplied outputs are split into valid stacks")
    void testMultiplySplitsStacks() {
        List<ItemStack> result =
                CompressedOutputs.multiply(new ItemStack[] {new ItemStack(Material.IRON_INGOT, 3)}, 64);

        // 3 * 64 = 192 = 3 full stacks
        Assertions.assertEquals(3, result.size());
        for (ItemStack stack : result) {
            Assertions.assertEquals(64, stack.getAmount());
        }
    }

    @Test
    @DisplayName("A stack is never larger than the max stack size of its item")
    void testMultiplyRespectsMaxStackSize() {
        List<ItemStack> result = CompressedOutputs.multiply(new ItemStack[] {new ItemStack(Material.BUCKET)}, 40);

        int total = 0;
        for (ItemStack stack : result) {
            Assertions.assertTrue(stack.getAmount() <= Material.BUCKET.getMaxStackSize());
            total += stack.getAmount();
        }
        Assertions.assertEquals(40, total);
    }

    @Test
    @DisplayName("Air and null outputs are ignored")
    void testMultiplyIgnoresEmpty() {
        List<ItemStack> result = CompressedOutputs.multiply(new ItemStack[] {null, new ItemStack(Material.AIR)}, 8);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Outputs fit into empty slots and partial stacks of the same item")
    void testFitsEmptyAndPartial() {
        Map<Integer, ItemStack> inventory = new HashMap<>();
        inventory.put(0, new ItemStack(Material.IRON_INGOT, 60));

        // 4 into the partial stack, 64 + 64 into the empty slots
        List<ItemStack> outputs = CompressedOutputs.multiply(new ItemStack[] {new ItemStack(Material.IRON_INGOT)}, 132);
        Assertions.assertTrue(CompressedOutputs.fits(inventory::get, SLOTS, outputs));

        List<ItemStack> tooMany = CompressedOutputs.multiply(new ItemStack[] {new ItemStack(Material.IRON_INGOT)}, 133);
        Assertions.assertFalse(CompressedOutputs.fits(inventory::get, SLOTS, tooMany));
    }

    @Test
    @DisplayName("Outputs do not merge with a different item")
    void testFitsDifferentItem() {
        Map<Integer, ItemStack> inventory = new HashMap<>();
        inventory.put(0, new ItemStack(Material.GOLD_INGOT, 1));
        inventory.put(1, new ItemStack(Material.GOLD_INGOT, 1));

        List<ItemStack> outputs = CompressedOutputs.multiply(new ItemStack[] {new ItemStack(Material.IRON_INGOT)}, 65);
        Assertions.assertFalse(CompressedOutputs.fits(inventory::get, SLOTS, outputs));

        List<ItemStack> oneStack = CompressedOutputs.multiply(new ItemStack[] {new ItemStack(Material.IRON_INGOT)}, 64);
        Assertions.assertTrue(CompressedOutputs.fits(inventory::get, SLOTS, oneStack));
    }

    @Test
    @DisplayName("Multiple different outputs share the free slots")
    void testFitsMultipleOutputs() {
        Map<Integer, ItemStack> inventory = new HashMap<>();

        ItemStack[] recipe = {new ItemStack(Material.IRON_INGOT), new ItemStack(Material.GOLD_INGOT)};
        // 64 iron + 64 gold = 2 slots
        Assertions.assertTrue(CompressedOutputs.fits(inventory::get, SLOTS, CompressedOutputs.multiply(recipe, 64)));
        // 65 iron + 65 gold = 4 slots
        Assertions.assertFalse(CompressedOutputs.fits(inventory::get, SLOTS, CompressedOutputs.multiply(recipe, 65)));
    }
}
