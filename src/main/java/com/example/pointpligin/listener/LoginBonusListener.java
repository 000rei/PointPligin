package com.example.pointpligin.listener;

import com.example.pointpligin.Main;
import com.example.pointpligin.PointManager;
import com.example.pointpligin.storage.DataStorage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.LocalDate;
import java.time.ZoneId;

public class LoginBonusListener implements Listener {
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private final Main plugin;
    private final PointManager pointManager;
    private final DataStorage dataStorage;

    public LoginBonusListener(Main plugin, PointManager pointManager, DataStorage dataStorage) {
        this.plugin = plugin;
        this.pointManager = pointManager;
        this.dataStorage = dataStorage;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("daily-login.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        String today = LocalDate.now(JST).toString();
        String lastDate = dataStorage.getLastLoginDate(player.getUniqueId());
        if (today.equals(lastDate)) {
            return;
        }

        int base = plugin.getConfig().getInt("daily-login.points", 0);
        int boost = dataStorage.getLoginBonusBoost(player.getUniqueId());
        int total = Math.max(0, base + boost);
        if (total <= 0) {
            dataStorage.setLastLoginDate(player.getUniqueId(), today);
            dataStorage.save();
            return;
        }

        pointManager.addPoints(player, total);
        dataStorage.setLastLoginDate(player.getUniqueId(), today);
        dataStorage.save();
        player.sendMessage("§aログインボーナス: " + total + " ポイント獲得しました。");
    }
}
