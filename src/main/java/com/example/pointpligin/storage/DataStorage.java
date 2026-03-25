package com.example.pointpligin.storage;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DataStorage {
    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration config;

    public DataStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "players.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException("Could not create players.yml", e);
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public String getLastLoginDate(UUID uuid) {
        return config.getString("players." + uuid + ".last-login-date");
    }

    public void setLastLoginDate(UUID uuid, String date) {
        config.set("players." + uuid + ".last-login-date", date);
    }

    public Set<String> getClaimedAdvancements(UUID uuid) {
        return new HashSet<>(config.getStringList("players." + uuid + ".claimed-advancements"));
    }

    public boolean hasClaimedAdvancement(UUID uuid, String key) {
        return getClaimedAdvancements(uuid).contains(key);
    }

    public void addClaimedAdvancement(UUID uuid, String key) {
        Set<String> claimed = getClaimedAdvancements(uuid);
        claimed.add(key);
        config.set("players." + uuid + ".claimed-advancements", claimed.stream().toList());
    }

    public int getLoginBonusBoost(UUID uuid) {
        return config.getInt("players." + uuid + ".login-bonus-boost", 0);
    }

    public void addLoginBonusBoost(UUID uuid, int increase) {
        int current = getLoginBonusBoost(uuid);
        config.set("players." + uuid + ".login-bonus-boost", current + Math.max(0, increase));
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save players.yml: " + e.getMessage());
        }
    }
}
