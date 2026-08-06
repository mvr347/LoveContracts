package me.lovelace.lovecontracts.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

public class HeadUtil {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    public static String getHeadTexture(String key, String fallback) {
        me.lovelace.lovecontracts.LoveContracts plugin = me.lovelace.lovecontracts.LoveContracts.getInstance();
        if (plugin != null) {
            org.bukkit.configuration.file.FileConfiguration heads = plugin.getHeadsConfig();
            if (heads != null) {
                String val = heads.getString("Contracts.difficulty." + key);
                if (val == null) val = heads.getString("Contracts." + key);
                if (val == null) val = heads.getString("Gui-Buttons." + key);

                if (val == null) {
                    if ("active-quest".equals(key) || "active".equals(key)) val = heads.getString("Contracts.taken-contract");
                    else if ("locked".equals(key)) val = heads.getString("Contracts.inactive");
                    else if ("completed-contract".equals(key)) val = heads.getString("Contracts.completed");
                    else if ("failed-contract".equals(key)) val = heads.getString("Contracts.failed");
                }

                if (val == null) val = heads.getString("heads." + key);
                if (val == null) val = heads.getString("status-textures." + key);
                if (val == null) val = heads.getString("difficulty-textures." + key);

                if (val != null && !val.trim().isEmpty()) return val.trim();
            }

            org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
            if (config != null) {
                String val = config.getString("gui.status-textures." + key);
                if (val == null) val = config.getString("gui.difficulty-textures." + key);
                if (val == null) val = config.getString("gui." + key + "-head-texture");
                if (val != null && !val.trim().isEmpty()) return val.trim();
            }
        }
        return fallback;
    }

    public static ItemStack createBase64Head(String base64Texture, String displayName, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            if (base64Texture != null && !base64Texture.trim().isEmpty()) {
                try {
                    String clean = base64Texture.trim().replaceAll("\\s+", "");
                    PlayerProfile profile = Bukkit.createProfile(
                            UUID.nameUUIDFromBytes(clean.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                            "TextureHead"
                    );
                    profile.setProperty(new ProfileProperty("textures", clean));
                    meta.setPlayerProfile(profile);
                } catch (Exception e) {
                    Bukkit.getLogger().warning("Failed to set player head texture: " + e.getMessage());
                }
            }
            if (displayName != null && !displayName.isEmpty()) {
                meta.displayName(mm.deserialize(displayName));
            }
            if (lore != null) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
