package me.lovelace.lovecontracts.player.util;

import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base64(BukkitObjectStream) сериализация списка ItemStack для хранения в TEXT-колонке SQLite.
 * Стандартный для Bukkit-плагинов подход — переживает рестарт сервера и смену версии Paper.
 */
public final class ItemStackSerializer {

    private ItemStackSerializer() {
    }

    public static String serialize(List<ItemStack> items, Logger logger) {
        if (items == null || items.isEmpty()) return "";
        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(byteOut)) {
            out.writeInt(items.size());
            for (ItemStack item : items) {
                out.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(byteOut.toByteArray());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to serialize item rewards", e);
            return "";
        }
    }

    public static List<ItemStack> deserialize(String data, Logger logger) {
        List<ItemStack> result = new ArrayList<>();
        if (data == null || data.isBlank()) return result;
        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream in = new BukkitObjectInputStream(byteIn)) {
            int size = in.readInt();
            for (int i = 0; i < size; i++) {
                Object obj = in.readObject();
                if (obj instanceof ItemStack stack) {
                    result.add(stack);
                }
            }
        } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
            logger.log(Level.WARNING, "Failed to deserialize item rewards", e);
        }
        return result;
    }

    static {
        // no-op: гарантирует, что ItemStack зарегистрирован как ConfigurationSerializable
        ConfigurationSerialization.registerClass(ItemStack.class);
    }
}
