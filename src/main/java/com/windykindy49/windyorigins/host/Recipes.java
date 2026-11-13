package com.windykindy49.windyorigins.host;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.StonecuttingRecipe;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

/**
 * Making things for Volta with the body: the game's own recipes, of every mod, worked out from what the Host
 * carries. Nothing is made out of nothing: every ingredient comes out of the inventory, and what a recipe
 * needs and the Host has not got is made first, if that can be made from what there is (planks from logs,
 * sticks from planks), or else named as missing. Whatever a stonecutter can cut is cut on one when there is
 * one to hand: it wastes nothing.
 */
final class Recipes {
    static final int HAND = 0;
    static final int TABLE = 1;
    static final int CUTTER = 2;

    /** One recipe to be carried out so many times, in the order the steps are listed, and where: in the hands, at a table, on a stonecutter. */
    record Step(List<Ingredient> needs, ItemStack makes, int times, int station) {
    }

    /** The recipes of a world, by what they make. */
    static final class Book {
        World world;
        Map<Item, List<CraftingRecipe>> index;
        Map<Item, List<StonecuttingRecipe>> cuts;
        /** There is a stonecutter to use. */
        boolean cutter;

        boolean knows(Item item) {
            return this.index.containsKey(item) || this.cutter && this.cuts.containsKey(item);
        }
    }

    /** What a plan fell short of: by kind, and as the ingredients themselves, each as many times as it is wanted. */
    static final class Lack {
        final Map<Item, Integer> items = new LinkedHashMap<>();
        final List<Ingredient> wants = new ArrayList<>();

        int sum() {
            return this.items.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private Recipes() {
    }

    static List<CraftingRecipe> all(World world) {
        List<CraftingRecipe> all = new ArrayList<>();
        world.getRecipeManager().listAllOfType(RecipeType.CRAFTING).forEach(entry -> all.add(entry.value()));
        return all;
    }

    static List<StonecuttingRecipe> allCuts(World world) {
        List<StonecuttingRecipe> all = new ArrayList<>();
        world.getRecipeManager().listAllOfType(RecipeType.STONECUTTING).forEach(entry -> all.add(entry.value()));
        return all;
    }

    static ItemStack out(Recipe<?> recipe, World world) {
        return recipe.getResult(world.getRegistryManager());
    }

    static Book book(World world, boolean cutter) {
        Book book = new Book();
        book.world = world;
        book.cutter = cutter;
        book.index = new HashMap<>();
        book.cuts = new HashMap<>();

        for (CraftingRecipe recipe : all(world)) {
            ItemStack makes = out(recipe, world);
            if (!makes.isEmpty() && !recipe.getIngredients().isEmpty()) {
                book.index.computeIfAbsent(makes.getItem(), key -> new ArrayList<>()).add(recipe);
            }
        }

        for (StonecuttingRecipe recipe : allCuts(world)) {
            ItemStack makes = out(recipe, world);
            if (!makes.isEmpty() && !recipe.getIngredients().isEmpty()) {
                book.cuts.computeIfAbsent(makes.getItem(), key -> new ArrayList<>()).add(recipe);
            }
        }

        return book;
    }

    /** What this turns into in a furnace of the given kind; nothing when it does not cook there. */
    static <T extends AbstractCookingRecipe> ItemStack cooked(World world, ItemStack stack, RecipeType<T> type) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        return world.getRecipeManager().getFirstMatch(type, new SimpleInventory(stack.copy()), world).map(recipe -> out(recipe.value(), world)).orElse(ItemStack.EMPTY);
    }

    /** What the Host carries, counted by kind. */
    static Map<Item, Integer> stock(ServerPlayerEntity host) {
        Map<Item, Integer> have = new HashMap<>();

        for (ItemStack stack : host.getInventory().main) {
            if (!stack.isEmpty()) {
                have.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }

        return have;
    }

    /**
     * Works out how to end up with so many of a thing. On success the steps are appended in the order to do them
     * and {@code have} becomes what the inventory will hold afterwards. On failure {@code lack} says what the
     * most straightforward recipe is short of.
     */
    static boolean make(Book book, Item target, int need, Map<Item, Integer> have, List<Step> steps, Lack lack, int depth, Set<Item> busy) {
        if (depth < 0 || !busy.add(target)) {
            return false;
        }

        try {
            World world = book.world;
            if (book.cutter) {
                for (StonecuttingRecipe cut : book.cuts.getOrDefault(target, List.of())) {
                    ItemStack makes = out(cut, world);
                    int times = (need + makes.getCount() - 1) / makes.getCount();
                    Ingredient from = cut.getIngredients().get(0);

                    for (ItemStack one : from.getMatchingStacks()) {
                        if (have.getOrDefault(one.getItem(), 0) >= times) {
                            have.merge(one.getItem(), -times, Integer::sum);
                            have.merge(target, makes.getCount() * times, Integer::sum);
                            steps.add(new Step(List.of(Ingredient.ofItems(one.getItem())), makes, times, CUTTER));
                            return true;
                        }
                    }
                }
            }

            for (CraftingRecipe recipe : book.index.getOrDefault(target, List.of())) {
                ItemStack makes = out(recipe, world);
                int times = (need + makes.getCount() - 1) / makes.getCount();
                Map<Item, Integer> trial = new HashMap<>(have);
                List<Step> before = new ArrayList<>();
                Lack short_ = new Lack();

                for (int n = 0; n < times; n++) {
                    for (Ingredient wanted : recipe.getIngredients()) {
                        if (wanted.isEmpty()) {
                            continue;
                        }

                        ItemStack[] fits = wanted.getMatchingStacks();
                        Item pick = null;
                        int most = 0;

                        for (ItemStack one : fits) {
                            int count = trial.getOrDefault(one.getItem(), 0);
                            if (count > most) {
                                most = count;
                                pick = one.getItem();
                            }
                        }

                        if (pick == null && short_.items.isEmpty()) {
                            // Not in the pockets: can one of the things that would do be made from what is?
                            for (int i = 0; i < Math.min(fits.length, 16) && pick == null; i++) {
                                if (make(book, fits[i].getItem(), 1, trial, before, new Lack(), depth - 1, busy)) {
                                    pick = fits[i].getItem();
                                }
                            }
                        }

                        if (pick == null) {
                            if (fits.length > 0) {
                                short_.items.merge(fits[0].getItem(), 1, Integer::sum);
                                short_.wants.add(wanted);
                            }

                            continue;
                        }

                        trial.merge(pick, -1, Integer::sum);
                        Item left = pick.getRecipeRemainder();
                        if (left != null) {
                            trial.merge(left, 1, Integer::sum);
                        }
                    }
                }

                if (short_.items.isEmpty()) {
                    trial.merge(target, makes.getCount() * times, Integer::sum);
                    have.clear();
                    have.putAll(trial);
                    steps.addAll(before);
                    List<Ingredient> needs = new ArrayList<>();

                    for (Ingredient wanted : recipe.getIngredients()) {
                        if (!wanted.isEmpty()) {
                            needs.add(wanted);
                        }
                    }

                    steps.add(new Step(needs, makes, times, recipe.fits(2, 2) ? HAND : TABLE));
                    return true;
                }

                if (lack.items.isEmpty()) {
                    lack.items.putAll(short_.items);
                    lack.wants.addAll(short_.wants);
                }
            }

            return false;
        } finally {
            busy.remove(target);
        }
    }

    static boolean make(Book book, Item target, int need, Map<Item, Integer> have, List<Step> steps, Lack lack) {
        return make(book, target, need, have, steps, lack, 3, new HashSet<>());
    }

    /** Carries one step out on the real inventory. False, with nothing more taken, when an ingredient is gone after all. */
    static boolean apply(ServerPlayerEntity host, Step step) {
        List<ItemStack> main = host.getInventory().main;

        for (int n = 0; n < step.times(); n++) {
            List<Integer> from = new ArrayList<>();
            Map<Integer, Integer> taken = new HashMap<>();

            for (Ingredient wanted : step.needs()) {
                int slot = -1;

                for (int i = 0; i < main.size(); i++) {
                    ItemStack stack = main.get(i);
                    if (!stack.isEmpty() && wanted.test(stack) && stack.getCount() - taken.getOrDefault(i, 0) > 0) {
                        slot = i;
                        break;
                    }
                }

                if (slot < 0) {
                    return false;
                }

                from.add(slot);
                taken.merge(slot, 1, Integer::sum);
            }

            for (int slot : from) {
                ItemStack stack = main.get(slot);
                Item left = stack.getItem().getRecipeRemainder();
                stack.decrement(1);
                if (left != null) {
                    host.getInventory().offerOrDrop(new ItemStack(left));
                }
            }

            host.getInventory().offerOrDrop(step.makes().copy());
        }

        host.getInventory().markDirty();
        host.playerScreenHandler.sendContentUpdates();
        return true;
    }
}
