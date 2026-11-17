package io.github.thebusybiscuit.slimefun4.implementation.compressed;

import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.groups.FlexItemGroup;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.GuideHistory;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.guide.SurvivalSlimefunGuide;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The guide entry of the compressed machines and electric devices.
 * The items are listed directly as a matrix: every column is one machine,
 * every row one multiplier (x8, x16, x32, x64 from top to bottom).
 *
 * @author mybezo
 */
public class CompressedItemGroup extends FlexItemGroup {

    private static final int COLUMNS = 9;
    private static final int ROWS = 4;

    private final List<SlimefunItem[]> columns = new ArrayList<>();

    @ParametersAreNonnullByDefault
    public CompressedItemGroup(NamespacedKey key, ItemStack item, int tier) {
        super(key, item, tier);
    }

    /**
     * Adds a column: all the compressed versions of one machine, from the lowest to the highest multiplier.
     *
     * @param items
     *            The compressed items of one machine
     */
    public void addColumn(@Nonnull List<SlimefunItem> items) {
        if (!items.isEmpty()) {
            columns.add(items.toArray(new SlimefunItem[0]));
        }
    }

    public boolean isEmpty() {
        return columns.isEmpty();
    }

    @Override
    @ParametersAreNonnullByDefault
    public boolean isVisible(Player p, PlayerProfile profile, SlimefunGuideMode mode) {
        return !columns.isEmpty();
    }

    @Override
    @ParametersAreNonnullByDefault
    public void open(Player p, PlayerProfile profile, SlimefunGuideMode mode) {
        openPage(p, profile, mode, 1);
    }

    @ParametersAreNonnullByDefault
    private void openPage(Player p, PlayerProfile profile, SlimefunGuideMode mode, int page) {
        GuideHistory history = profile.getGuideHistory();

        if (mode == SlimefunGuideMode.SURVIVAL_MODE) {
            history.add(this, page);
        }

        List<SlimefunItem[]> visible = new ArrayList<>(columns.size());

        for (SlimefunItem[] column : columns) {
            for (SlimefunItem item : column) {
                if (!item.isDisabledIn(p.getWorld())) {
                    visible.add(column);
                    break;
                }
            }
        }

        int pages = Math.max(1, (visible.size() + COLUMNS - 1) / COLUMNS);
        int currentPage = Math.max(1, Math.min(page, pages));

        ChestMenu menu = new ChestMenu(Slimefun.getLocalization().getMessage(p, "guide.title.main"));
        SurvivalSlimefunGuide guide =
                (SurvivalSlimefunGuide) Slimefun.getRegistry().getSlimefunGuide(mode);

        menu.setEmptySlotsClickable(false);
        menu.addMenuOpeningHandler(SoundEffect.GUIDE_BUTTON_CLICK_SOUND::playFor);
        guide.createHeader(p, profile, menu);

        menu.addItem(
                1,
                new CustomItemStack(ChestMenuUtils.getBackButton(
                        p, "", ChatColor.GRAY + Slimefun.getLocalization().getMessage(p, "guide.back.guide"))));
        menu.addMenuClickHandler(1, (pl, s, is, action) -> {
            SlimefunGuide.openMainMenu(profile, mode, history.getMainMenuPage());
            return false;
        });

        int first = (currentPage - 1) * COLUMNS;

        for (int column = 0; column < COLUMNS && first + column < visible.size(); column++) {
            SlimefunItem[] items = visible.get(first + column);

            for (int row = 0; row < ROWS && row < items.length; row++) {
                SlimefunItem item = items[row];

                if (item.isDisabledIn(p.getWorld())) {
                    continue;
                }

                int slot = 9 + row * 9 + column;
                menu.addItem(slot, item.getItem().clone());
                menu.addMenuClickHandler(slot, (pl, s, is, action) -> {
                    if (mode == SlimefunGuideMode.CHEAT_MODE) {
                        if (pl.isOp() || pl.hasPermission("slimefun.cheat.items")) {
                            ItemStack stack = item.getItem().clone();
                            stack.setAmount(action.isShiftClicked() ? stack.getMaxStackSize() : 1);
                            pl.getInventory().addItem(stack);
                        } else {
                            Slimefun.getLocalization().sendMessage(pl, "messages.no-permission", true);
                        }
                    } else {
                        guide.displayItem(profile, item, true);
                    }

                    return false;
                });
            }
        }

        for (int slot = 45; slot < 54; slot++) {
            menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }

        menu.addItem(46, ChestMenuUtils.getPreviousButton(p, currentPage, pages));
        menu.addMenuClickHandler(46, (pl, slot, item, action) -> {
            if (currentPage > 1) {
                openPage(pl, profile, mode, currentPage - 1);
            }

            return false;
        });

        menu.addItem(52, ChestMenuUtils.getNextButton(p, currentPage, pages));
        menu.addMenuClickHandler(52, (pl, slot, item, action) -> {
            if (currentPage < pages) {
                openPage(pl, profile, mode, currentPage + 1);
            }

            return false;
        });

        menu.open(p);
    }
}
