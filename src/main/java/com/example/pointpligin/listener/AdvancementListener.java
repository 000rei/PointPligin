package com.example.pointpligin.listener;

import com.example.pointpligin.Main;
import com.example.pointpligin.PointManager;
import com.example.pointpligin.storage.DataStorage;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

public class AdvancementListener implements Listener {
    private final Main plugin;
    private final PointManager pointManager;
    private final DataStorage dataStorage;

    public AdvancementListener(Main plugin, PointManager pointManager, DataStorage dataStorage) {
        this.plugin = plugin;
        this.pointManager = pointManager;
        this.dataStorage = dataStorage;
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        NamespacedKey key = event.getAdvancement().getKey();
        String keyString = key.toString();

        if (!plugin.getConfig().contains("advancement-rewards." + keyString)) {
            return;
        }
        if (dataStorage.hasClaimedAdvancement(player.getUniqueId(), keyString)) {
            return;
        }

        int reward = plugin.getConfig().getInt("advancement-rewards." + keyString, 0);
        if (reward <= 0) {
            return;
        }

        pointManager.addPoints(player, reward);
        dataStorage.addClaimedAdvancement(player.getUniqueId(), keyString);
        dataStorage.save();
        player.sendMessage("§a実績報酬で " + reward + " ポイント獲得しました。");
    }
}
