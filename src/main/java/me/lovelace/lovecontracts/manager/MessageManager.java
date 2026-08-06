package me.lovelace.lovecontracts.manager;

import me.lovelace.lovecontracts.LoveContracts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;
import java.util.logging.Level;

public class MessageManager {

    private final LoveContracts plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private YamlConfiguration config;

    public MessageManager(LoveContracts plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public String getRaw(String path, String defaultValue) {
        if (config == null) return defaultValue;
        return config.getString(path, defaultValue);
    }

    public Component getComponent(String path, String defaultValue) {
        return mm.deserialize(getRaw(path, defaultValue));
    }

    public Component getComponent(String path, String defaultValue, Map<String, String> replacements) {
        String text = getRaw(path, defaultValue);
        if (replacements != null) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                text = text.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return mm.deserialize(text);
    }

    public void sendMessage(CommandSender sender, String path, String defaultValue) {
        if (sender != null) {
            sender.sendMessage(getComponent(path, defaultValue));
        }
    }

    public void sendMessage(CommandSender sender, String path, String defaultValue, Map<String, String> replacements) {
        if (sender != null) {
            sender.sendMessage(getComponent(path, defaultValue, replacements));
        }
    }
}
