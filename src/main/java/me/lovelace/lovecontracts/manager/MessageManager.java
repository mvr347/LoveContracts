package me.lovelace.lovecontracts.manager;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.util.CoinFormat;
import me.lovelace.lovecontracts.util.Notify;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

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
        return mm.deserialize(applyReplacements(path, defaultValue, replacements));
    }

    private String applyReplacements(String path, String defaultValue, Map<String, String> replacements) {
        String text = getRaw(path, defaultValue);
        if (replacements != null) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                text = text.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return text;
    }

    public void sendMessage(CommandSender sender, String path, String defaultValue) {
        if (sender != null) {
            sender.sendMessage(getComponent(path, defaultValue));
        }
    }

    public void sendMessage(CommandSender sender, String path, String defaultValue, Map<String, String> replacements) {
        if (sender != null) {
            // Replacements may carry coin glyphs (%img_*%): resolve them for this player before parsing.
            String text = applyReplacements(path, defaultValue, replacements);
            if (sender instanceof org.bukkit.entity.Player player) {
                text = CoinFormat.resolveGlyphs(player, text);
            }
            sender.sendMessage(mm.deserialize(text));
        }
    }

    /**
     * Event-style notice (action bar first, LoveNotify moves it to chat when it is too long or the channel is off).
     * Command replies must keep using {@link #sendMessage}.
     */
    public void notice(Player player, String path, String defaultValue) {
        notice(player, path, defaultValue, Map.of());
    }

    public void notice(Player player, String path, String defaultValue, Map<String, String> replacements) {
        if (player != null) {
            Notify.event(player, resolve(player, path, defaultValue, replacements));
        }
    }

    /** Critical notice (money moved, contract failed): always duplicated to chat. */
    public void important(Player player, String path, String defaultValue) {
        important(player, path, defaultValue, Map.of());
    }

    public void important(Player player, String path, String defaultValue, Map<String, String> replacements) {
        if (player != null) {
            Notify.important(player, resolve(player, path, defaultValue, replacements));
        }
    }

    private Component resolve(Player player, String path, String defaultValue, Map<String, String> replacements) {
        // Replacements may carry coin glyphs (%img_*%): resolve them for this player before parsing.
        return mm.deserialize(CoinFormat.resolveGlyphs(player, applyReplacements(path, defaultValue, replacements)));
    }
}
