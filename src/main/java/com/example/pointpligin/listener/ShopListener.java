package com.example.pointpligin.listener;

import com.example.pointpligin.Main;
import com.example.pointpligin.PointManager;
import com.example.pointpligin.storage.DataStorage;
import org.bukkit.Location;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.block.Sign;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ShopListener implements Listener {
    private static final String SHOP_HEADER = "[PointShop]";
    private static final String ADMIN_PERMISSION = "pointpligin.admin";
    private static final long CONFIRM_WINDOW_MS = 5000L;
    private static final long FIRST_CLICK_COOLDOWN_MS = 500L;

    private final Main plugin;
    private final PointManager pointManager;
    private final DataStorage dataStorage;
    private final Map<UUID, PendingPurchase> pendingPurchases = new HashMap<>();
    private final File shopsFile;
    private FileConfiguration shopsConfig;

    public ShopListener(Main plugin, PointManager pointManager, DataStorage dataStorage) {
        this.plugin = plugin;
        this.pointManager = pointManager;
        this.dataStorage = dataStorage;
        this.shopsFile = new File(plugin.getDataFolder(), "shops.yml");
        this.shopsConfig = loadYaml(plugin, shopsFile);
    }

    @EventHandler
    public void onSignCreate(SignChangeEvent event) {
        String line = event.getLine(0);
        if (line == null || !line.equalsIgnoreCase(SHOP_HEADER)) {
            return;
        }

        if (!event.getPlayer().hasPermission(ADMIN_PERMISSION)) {
            event.getPlayer().sendMessage("§cショップ看板を作成する権限がありません。");
            event.setCancelled(true);
            return;
        }

        event.setLine(0, "§2" + SHOP_HEADER);
        String key = locationKey(event.getBlock().getLocation());
        shopsConfig.set("shops." + key + ".world", event.getBlock().getWorld().getName());
        shopsConfig.set("shops." + key + ".x", event.getBlock().getX());
        shopsConfig.set("shops." + key + ".y", event.getBlock().getY());
        shopsConfig.set("shops." + key + ".z", event.getBlock().getZ());
        saveShops();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!(event.getBlock().getState() instanceof Sign sign)) {
                return;
            }
            sign.setWaxed(true);
            sign.update(true, false);
        });
        event.getPlayer().sendMessage("§aPointShop看板を作成して保護しました。");
    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!(event.getClickedBlock().getState() instanceof Sign sign)) {
            return;
        }

        String line0 = strip(sign.getLine(0));
        if (!SHOP_HEADER.equalsIgnoreCase(line0)) {
            return;
        }

        String key = locationKey(event.getClickedBlock().getLocation());
        if (!isRegisteredShop(key)) {
            return;
        }

        event.setCancelled(true);

        long now = System.currentTimeMillis();
        UUID playerId = event.getPlayer().getUniqueId();
        PendingPurchase pending = pendingPurchases.get(playerId);

        if (pending == null || !pending.shopKey.equals(key) || now > pending.expiresAt) {
            pendingPurchases.put(playerId, new PendingPurchase(key, now + CONFIRM_WINDOW_MS, now + FIRST_CLICK_COOLDOWN_MS));
            event.getPlayer().sendMessage("§e商品を購入しますか？5秒以内にもう一度右クリックで購入します。");
            return;
        }

        if (now < pending.cooldownUntil) {
            event.getPlayer().sendMessage("§e連打防止中です。少し待ってから再度右クリックしてください。");
            return;
        }

        pendingPurchases.remove(playerId);

        ParsedSign parsed = parseSign(sign.getLine(1), sign.getLine(2), sign.getLine(3));
        if (!parsed.valid()) {
            event.getPlayer().sendMessage("§c看板フォーマットが不正です。");
            return;
        }

        processPurchase(event.getPlayer(), parsed);
    }

    @EventHandler
    public void onRegisteredShopBreak(BlockBreakEvent event) {
        String key = locationKey(event.getBlock().getLocation());
        if (!isRegisteredShop(key)) {
            return;
        }

        shopsConfig.set("shops." + key, null);
        saveShops();
    }

    private void processPurchase(Player player, ParsedSign parsed) {
        if (!pointManager.removePoints(player, parsed.price)) {
            player.sendMessage("§cポイントが足りません。");
            return;
        }

        if (!deliver(player, parsed)) {
            pointManager.addPoints(player, parsed.price);
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
                } else if (addAmplifier > 0) {
                    // "amplifier: 1" をレベル+1として扱い、初回購入でいきなりレベル2にならないよう補正
                    newAmplifier -= 1;
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

    private String locationKey(Location location) {
        return String.format(
                Locale.ROOT,
                "%s:%d:%d:%d",
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    private boolean isRegisteredShop(String key) {
        return shopsConfig.contains("shops." + key);
    }

    private void saveShops() {
        try {
            shopsConfig.save(shopsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save shops.yml: " + e.getMessage());
        }
    }

    private FileConfiguration loadYaml(JavaPlugin plugin, File file) {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException("Could not create " + file.getName(), e);
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private record ParsedSign(String itemKey, int amount, int price, String description) {
        private boolean valid() {
            return itemKey != null && !itemKey.isBlank();
        }

        private static ParsedSign invalid() {
            return new ParsedSign("", 0, 0, "");
        }
    }

    private record PendingPurchase(String shopKey, long expiresAt, long cooldownUntil) {
    }
}
