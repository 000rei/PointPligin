package com.example.pointpligin;

import com.example.pointpligin.command.RankingCommand;
import com.example.pointpligin.listener.AdvancementListener;
import com.example.pointpligin.listener.LoginBonusListener;
import com.example.pointpligin.listener.ShopListener;
import com.example.pointpligin.storage.DataStorage;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private PointManager pointManager;
    private DataStorage dataStorage;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.pointManager = new PointManager();
        this.dataStorage = new DataStorage(this);

        getServer().getPluginManager().registerEvents(new AdvancementListener(this, pointManager, dataStorage), this);
        getServer().getPluginManager().registerEvents(new LoginBonusListener(this, pointManager, dataStorage), this);
        getServer().getPluginManager().registerEvents(new ShopListener(this, pointManager, dataStorage), this);

        if (getCommand("ranking") != null) {
            getCommand("ranking").setExecutor(new RankingCommand(pointManager));
        }
    }

    @Override
    public void onDisable() {
        dataStorage.save();
    }
}
