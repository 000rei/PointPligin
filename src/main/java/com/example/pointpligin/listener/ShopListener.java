package com.example.pointpligin.listener;

import com.example.pointpligin.Main;
import com.example.pointpligin.PointManager;
import com.example.pointpligin.storage.DataStorage;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

public class ShopListener implements Listener {
    private static final String SHOP_HEADER = "[PointShop]";

    private final Main plugin;
    private final PointManager pointManager;
    private final DataStorage dataStorage;

    public ShopListener(Main plugin, PointManager pointManager, DataStorage dataStorage) {
        this.plugin = plugin;
        this.pointManager = pointManager;
        this.dataStorage = dataStorage;
    }

    @EventHandler
    public void onSignCreate(SignChangeEvent event) {
        String line = event.getLine(0);
        if (line == null || !line.equalsIgnoreCase(SHOP_HEADER)) {
            return;
        }
        event.setLine(0, "§2" + SHOP_HEADER);
        event.getPlayer().sendMessage("§aPointShop看板を作成しました。");
    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!(event.getClickedBlock().getState() instanceof org.bukkit.block.Sign sign)) {
            return;
        }

        String line0 = strip(sign.getLine(0));
        if (!SHOP_HEADER.equalsIgnoreCase(line0)) {
            return;
        }

        ParsedSign parsed = parseSign(sign.getLine(1), sign.getLine(2), sign.getLine(3));
        if (!parsed.valid()) {
            event.getPlayer().sendMessage("§c看板フォーマットが不正です。");
            return;
        }

        String command = String.format(
                Locale.ROOT,
                "/pointshopbuy %s %d %d %d",
                event.getClickedBlock().getWorld().getName(),
                event.getClickedBlock().getX(),
                event.getClickedBlock().getY(),
                event.getClickedBlock().getZ()
        );

        event.getPlayer().sendMessage("§e商品: §f" + parsed.itemKey + " x" + parsed.amount);
        event.getPlayer().sendMessage("§e価格: §f" + parsed.price + " pt");
        event.getPlayer().sendMessage("§e説明: §f" + parsed.description);

        TextComponent button = new TextComponent("§a[購入する]");
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        event.getPlayer().spigot().sendMessage(new ComponentBuilder(button).create());
    }

    @EventHandler
    public void onPseudoBuyCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (!message.toLowerCase(Locale.ROOT).startsWith("/pointshopbuy ")) {
            return;
        }
        event.setCancelled(true);

        String[] split = message.split(" ");
        if (split.length != 5) {
            event.getPlayer().sendMessage("§c購入に失敗しました。");
            return;
        }

        String worldName = split[1];
        int x;
        int y;
        int z;
        try {
            x = Integer.parseInt(split[2]);
            y = Integer.parseInt(split[3]);
            z = Integer.parseInt(split[4]);
        } catch (NumberFormatException ex) {
            event.getPlayer().sendMessage("§c購入に失敗しました。");
            return;
        }

        if (Bukkit.getWorld(worldName) == null) {
            event.getPlayer().sendMessage("§c看板が見つかりません。");
            return;
        }
        if (!(Bukkit.getWorld(worldName).getBlockAt(x, y, z).getState() instanceof org.bukkit.block.Sign sign)) {
            event.getPlayer().sendMessage("§c看板が見つかりません。");
            return;
        }

        ParsedSign parsed = parseSign(sign.getLine(1), sign.getLine(2), sign.getLine(3));
        if (!parsed.valid()) {
            event.getPlayer().sendMessage("§c看板設定が不正です。");
            return;
        }

        if (!pointManager.removePoints(event.getPlayer(), parsed.price)) {
            event.getPlayer().sendMessage("§cポイントが足りません。");
            return;
        }

        if (!deliver(event.getPlayer(), parsed)) {
            pointManager.addPoints(event.getPlayer(), parsed.price);
        }
    }

    private boolean deliver(Player player, ParsedSign parsed) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("shop." + parsed.itemKey);
        if (section == null) {
            player.sendMessage("§c商品定義が見つかりません。");
            return false;
        }

        String type = section.getString("type", "").toLowerCase(Locale.ROOT);
        switch (type) {
            case "item" -> {
                Material material = Material.matchMaterial(section.getString("material", ""));
                if (material == null) {
                    player.sendMessage("§c無効なアイテムです。");
                    return false;
                }
                int baseAmount = Math.max(1, section.getInt("amount", 1));
                ItemStack stack = new ItemStack(material, baseAmount * parsed.amount);
                var leftover = player.getInventory().addItem(stack);
                if (!leftover.isEmpty()) {
                    player.sendMessage("§cインベントリが満杯のため購入できません。");
                    return false;
                }
                player.sendMessage("§a購入完了: " + material + " x" + (baseAmount * parsed.amount));
                return true;
            }
            case "potion" -> {
                PotionEffectType effectType = PotionEffectType.getByName(section.getString("effect", ""));
                if (effectType == null) {
                    player.sendMessage("§c無効なポーション効果です。");
                    return false;
                }
                int configDuration = section.getInt("duration", 1200);
                int duration = configDuration < 0 ? Integer.MAX_VALUE : configDuration;
                int addAmplifier = Math.max(0, section.getInt("amplifier", 0)) * parsed.amount;
                PotionEffect current = player.getPotionEffect(effectType);
                int newAmplifier = addAmplifier;
                int newDuration = duration;
                if (current != null) {
                    newAmplifier += current.getAmplifier();
                    newDuration = Math.max(current.getDuration(), duration);
                }
                player.addPotionEffect(new PotionEffect(effectType, newDuration, newAmplifier, true, true, true), true);
                player.sendMessage("§a購入完了: " + effectType.getName() + " レベルが上昇しました。");
                return true;
            }
            case "login_bonus_boost" -> {
                int increase = Math.max(0, section.getInt("increase", 0)) * parsed.amount;
                dataStorage.addLoginBonusBoost(player.getUniqueId(), increase);
                dataStorage.save();
                player.sendMessage("§a購入完了: ログインボーナス +" + increase);
                return true;
            }
            default -> {
                player.sendMessage("§c不明なショップタイプです。");
                return false;
            }
        }
    }

    private ParsedSign parseSign(String line1, String line2, String line3) {
        if (line1 == null || line2 == null) {
            return ParsedSign.invalid();
        }
        String[] keyAndAmount = line1.trim().split("\\s+");
        if (keyAndAmount.length < 1) {
            return ParsedSign.invalid();
        }
        String key = keyAndAmount[0];
        int amount = 1;
        if (keyAndAmount.length >= 2) {
            try {
                amount = Integer.parseInt(keyAndAmount[1]);
            } catch (NumberFormatException ignored) {
                return ParsedSign.invalid();
            }
        }
        int price;
        try {
            price = Integer.parseInt(line2.trim());
        } catch (NumberFormatException ignored) {
            return ParsedSign.invalid();
        }
        return new ParsedSign(key, Math.max(1, amount), Math.max(0, price), line3 == null ? "" : line3);
    }

    private String strip(String text) {
        return text == null ? "" : ChatColor.stripColor(text).trim();
    }

    private record ParsedSign(String itemKey, int amount, int price, String description) {
        private boolean valid() {
            return itemKey != null && !itemKey.isBlank();
        }

        private static ParsedSign invalid() {
            return new ParsedSign("", 0, 0, "");
        }
    }
}
