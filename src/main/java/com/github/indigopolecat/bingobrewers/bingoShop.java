package com.github.indigopolecat.bingobrewers;


import net.minecraftforge.common.MinecraftForge;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import java.text.DecimalFormat;

public class bingoShop {
    @SubscribeEvent
    public void onShopOpen(GuiOpenEvent event) {
        GuiChest guiChest;
        if (event.gui instanceof GuiChest) {
            guiChest = (GuiChest) event.gui;
            Container gui = guiChest.inventorySlots;

            if (gui instanceof ContainerChest) {
                ContainerChest containerChest = (ContainerChest) gui;
                String name = containerChest.getLowerChestInventory().getDisplayName().getUnformattedText();
                if (name.equals("Bingo Shop")) {
                    System.out.println("Bingo Shop opened!");
                    doneLoading chestLoad = new doneLoading();
                    chestLoad.onInventoryChanged(containerChest);
                    MinecraftForge.EVENT_BUS.register(new bingoShop() {
                        @SubscribeEvent
                        // Event that occurs after the last item in the chest is loaded, or 3 seconds later.
                        public void onInitGuiPost(doneLoading.InventoryLoadingDoneEvent event) {
                            // set variables in correct scope
                            String cost;
                            int costInt = 0;
                            ArrayList<String> itemNames = new ArrayList<>();
                            ArrayList<Integer> itemCosts = new ArrayList<>();
                            ArrayList<String> extraItems = new ArrayList<>();
                            List<ItemStack> chestInventory = containerChest.getInventory();

                            for (ItemStack item : chestInventory) {
                                if (item != null) {
                                    List<String> itemLore = item.getTooltip(Minecraft.getMinecraft().thePlayer, false);
                                    String target = "Cost";
                                    boolean costFound = false;
                                    for (int i = 0; i < itemLore.size(); i++) {
                                        String extraItem = null;
                                        // if the previous lore line was "Cost", set this line to the cost variable and break the loop
                                        if (costFound) {
                                            cost = itemLore.get(i);
                                            String unformattedCost = removeFormatting(cost);
                                            System.out.println(cost);

                                            // if the next lore line is not empty, set it to the extra item variable, doesn't work for multiple extra items in the cost
                                            if (!itemLore.get(i + 1).equals("")) {
                                                extraItem = itemLore.get(i + 1);
                                            }
                                            try {
                                                costInt = Integer.parseInt(unformattedCost);
                                                System.out.println(costInt);
                                            } catch (NumberFormatException e) {
                                                System.out.println("Cost is not a number!");
                                            }
                                            itemCosts.add(costInt);
                                            extraItems.add(extraItem);
                                            String displayName = item.getDisplayName();
                                            itemNames.add(displayName);
                                            break;
                                        } else {
                                            // if lore line is "§5§o§7Cost"
                                            if (removeFormatting(itemLore.get(i)).equals(target)) {
                                                costFound = true;
                                            }
                                        }
                                    }
                                }
                            }
                            if (costInt == 0) {
                                System.out.println("Something went wrong: Bingo Point Cost not found in inventory named Bingo Shop!");
                            }
                            ArrayList<String> itemNamesFormatless = new ArrayList<>();
                            for (String itemName : itemNames) {
                                itemNamesFormatless.add(removeFormatting(itemName));
                            }
                            ArrayList<String> extraItemsFormatless = new ArrayList<>();
                            for (String extraItem : extraItems) {
                                extraItemsFormatless.add(removeFormatting(extraItem));
                            }
                            System.out.println("Extra Items" + extraItemsFormatless);

                            System.out.println(itemNamesFormatless);
                            CompletableFuture<ArrayList<Double>> costFuture = auctionAPI.fetchPriceMap(itemNamesFormatless).whenComplete((lbinMap, throwable) -> {
                            });
                            costFuture.thenAccept (coinCosts -> {
                                CompletableFuture<ArrayList<Double>> extraItemFuture = auctionAPI.fetchPriceMap(extraItemsFormatless).whenComplete((lbinMap, throwable) -> {
                                });
                            extraItemFuture.thenAccept(extraCoinCosts -> {
                            System.out.println(coinCosts);

                            if (itemCosts.size() == coinCosts.size() && itemCosts.size() == itemNames.size()) {
                                DecimalFormat decimalFormat = new DecimalFormat("#,###");
                                String extraName = null;
                                Double extraCoinCost = null;
                                for (int i = 0; i < itemCosts.size(); i++) {
                                    Double coinCost = coinCosts.get(i);

                                    // Skip the item if coin cost is null (item not found in auction house b/c soulbound or other reasons.)
                                    if (coinCost == (null)) {
                                        continue;
                                    }

                                    if (extraCoinCosts.get(i) != null) {
                                        extraCoinCost = extraCoinCosts.get(i);
                                        coinCost = coinCost - extraCoinCost;
                                        extraName = extraItemsFormatless.get(i);
                                    }
                                    int bingoCost = itemCosts.get(i);
                                    String itemName = itemNames.get(i);

                                    if (coinCost == 0) {
                                        System.out.println("Item not found in auction house or price is somehow 0: " + itemName);
                                    } else if (bingoCost == 0) {
                                        System.out.println("Failed to get Bingo Point cost of item: " + itemName);
                                    } else {
                                        double coinsPerPointdouble = coinCost / bingoCost;
                                        long coinsPerPointLong = Math.round(coinsPerPointdouble);
                                        String coinsPerPoint = decimalFormat.format(coinsPerPointLong);

                                        for (ItemStack item : chestInventory) {
                                            if (item != null) {
                                                String displayName = item.getDisplayName();

                                                // compare the display name of the item in the chest loop to the item name in the name array (aka the current one we are calculating for)
                                                if (displayName.equals(itemName)) {
                                                    NBTTagCompound nbt = item.getTagCompound();
                                                    NBTTagCompound displayTag = nbt.getCompoundTag("display");
                                                    NBTTagList loreList = displayTag.getTagList("Lore", 8);
                                                    System.out.println("lorelist: " + loreList);

                                                    int costLineIndex = -1;
                                                    int extraCostIndex = -1;
                                                    for (int j = 0; j < loreList.tagCount(); j++) {
                                                        // Compare the current line without formatting to the cost in bingo points
                                                        // removeFormatting method removes " Bingo Points" from the end of the string
                                                        System.out.println("loreList.getStringTagAt(j): " + loreList.getStringTagAt(j));
                                                        if (removeFormatting(loreList.getStringTagAt(j)).equals(Integer.toString(bingoCost))) {
                                                            costLineIndex = j + 1;
                                                            System.out.println("costLineIndex: " + costLineIndex);
                                                            System.out.println("nextline: " + removeFormatting(loreList.getStringTagAt(j + 1)));
                                                            System.out.println("extraName: " + extraName);
                                                            if (extraName != null && extraName.equals(removeFormatting(loreList.getStringTagAt(j + 1)))) {
                                                                System.out.println("running");
                                                                extraCostIndex = j + 2;
                                                                costLineIndex += 1;

                                                                System.out.println("extra: " + extraCostIndex);
                                                            }
                                                        }
                                                    }
                                                    // If no empty line is found after the cost line, set the cost line index to the end of the lore list
                                                    if (costLineIndex == -1) {
                                                        // Add one because the tooltip list used to add the line includes the display name and is 1 longer as a result
                                                        costLineIndex = loreList.tagCount() + 1;
                                                    }

                                                    System.out.println("costLineIndex: " + costLineIndex + " item name: " + itemName);
                                                    int finalCostLineIndex = costLineIndex;
                                                    int finalExtraCostIndex = extraCostIndex;
                                                    String finalExtraCost = null;
                                                    if (extraCoinCost != null) {
                                                        finalExtraCost = formatNumber(Math.round(extraCoinCost));
                                                    }
                                                    String finalExtraCost2 = finalExtraCost;
                                                    System.out.println("finalExtraCostIndex: " + finalExtraCostIndex);
                                                    System.out.println("finalExtraCost2: " + finalExtraCost2);
                                                    MinecraftForge.EVENT_BUS.register(new bingoShop() {
                                                        @SubscribeEvent
                                                        public void onItemTooltip(ItemTooltipEvent event) {
                                                            ItemStack eventItem = event.itemStack;
                                                            if (eventItem.getDisplayName().equals(itemName)) {
                                                                event.toolTip.add(finalCostLineIndex + 1, "§6" + coinsPerPoint + " Coins/Point");
                                                            }
                                                            if (finalExtraCostIndex != -1 && finalExtraCost2 != null && eventItem.getDisplayName().equals(itemName)) {
                                                                String extraCostLine = event.toolTip.get(finalExtraCostIndex);
                                                                event.toolTip.set(finalExtraCostIndex, extraCostLine + " §6(" + finalExtraCost2 + " Coins)");
                                                            }
                                                        }
                                                        @SubscribeEvent
                                                        public void unregister(GuiOpenEvent event) {
                                                            MinecraftForge.EVENT_BUS.unregister(this);
                                                        }
                                                    });





                                                    // TODO: better done loading detection currently works badly if there's no delay


                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                System.out.println("Something went wrong: itemCosts, coinCosts, and itemNames are not the same size!");
                            }
                            });
                            });
                            MinecraftForge.EVENT_BUS.unregister(this);
                        }
                    });
                }
            }
        }
    }

    private static String removeFormatting(String s) {
        String news = s.replaceAll("§.", "");
        if (news.endsWith(" Bingo Points")) {
            news = news.substring(0, news.length() - 13);
        }
        return news;
    }
    public static String formatNumber(long number) {
        if (number < 1_000) {
            return String.valueOf(number);
        } else {
            String pattern;
            double value;

            if (number < 1_000_000) {
                pattern = "#.#k";
                value = number / 1_000.0;
            } else {
                pattern = "#.#M";
                value = number / 1_000_000.0;
            }

            DecimalFormat decimalFormat = new DecimalFormat(pattern);
            return decimalFormat.format(value);
        }
    }
}


