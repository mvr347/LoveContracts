package me.lovelace.lovecontracts.listener;

import me.lovelace.lovecontracts.util.Notify;
import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.integration.NpcBridge;
import me.lovelace.lovecontracts.model.Contract;
import me.lovelace.lovecontracts.player.integration.LoveCoreBridge;
import net.kyori.adventure.text.minimessage.MiniMessage;
import dev.lovelace.lovecore.api.npc.NpcClick;
import dev.lovelace.lovecore.api.npc.NpcClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Random;

public class ContractNpcListener implements Listener {

    private final LoveContracts plugin;
    private final NpcBridge npcs;
    private final LoveCoreBridge bridge = new LoveCoreBridge();
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Random random = new Random();

    public ContractNpcListener(LoveContracts plugin, NpcBridge npcs) {
        this.plugin = plugin;
        this.npcs = npcs;
    }

    /**
     * Fired by LoveCore for every NPC provider, so clicks on NPCs of other providers (or other NPCs) are ignored here.
     * Right click hands in / cancels the active contract or opens the board; left click only opens the board.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcClick(NpcClickEvent event) {
        if (!npcs.isBoundNpc(event.npc()))
            return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (event.click() == NpcClick.LEFT) {
            if (!tryReject(player)) {
                maybeSayAmbient(player);
                plugin.getContractGUI().open(player);
            }
            return;
        }

        Contract active = plugin.getContractManager().getActiveContract(player.getUniqueId());

        if (active != null) {
            boolean isCompleted = active.getCondition() != null && active.getCondition().isCompleted(player);
            if (isCompleted) {
                plugin.getContractManager().completeContract(player, active);
                Notify.important(player, mm.deserialize(
                        "<green>✔ Вы отлично справились! Контракт успешно сдан NPC, награда получена.</green>"));
            } else {
                plugin.getContractManager().cancelContract(player, active);
                Notify.event(player, mm.deserialize("<yellow>Контракт отменен по вашему запросу у NPC.</yellow>"));
            }
        } else if (!tryReject(player)) {
            maybeSayAmbient(player);
            plugin.getContractGUI().open(player);
        }
    }

    /**
     * "Живая" реакция на реальные вежливость/стиль игры (LoveBehavior): ужасная вежливость
     * или агрессивный стиль игры может отказать в открытии доски новых контрактов. Возвращает
     * true, если отказано (доску открывать не надо) — только если реально нашлась фраза для
     * показа игроку: пустой список (например, старый config.yml без новых ключей) не должен
     * молча блокировать доску без объяснения причины. Без LoveBehavior/выключенной секции —
     * всегда false.
     */
    private boolean tryReject(Player player) {
        if (!plugin.getConfig().getBoolean("npc.npc-dialogue.reject.enabled", true)) {
            return false;
        }
        int politeness = bridge.politenessLevel(player.getUniqueId());
        int playstyle = bridge.playstyleLevel(player.getUniqueId());
        String key;
        if (politeness == 0) {
            key = "npc.npc-dialogue.reject.terrible-politeness";
        } else if (playstyle == 0) {
            key = "npc.npc-dialogue.reject.aggressive-playstyle";
        } else {
            return false;
        }
        return say(player, key);
    }

    /** С настроенным шансом говорит фразу под настроение, не блокируя взаимодействие. */
    private void maybeSayAmbient(Player player) {
        if (!plugin.getConfig().getBoolean("npc.npc-dialogue.ambient.enabled", true)) {
            return;
        }
        double chance = plugin.getConfig().getDouble("npc.npc-dialogue.ambient.chance", 0.35);
        if (random.nextDouble() >= chance) {
            return;
        }
        int politeness = bridge.politenessLevel(player.getUniqueId());
        int playstyle = bridge.playstyleLevel(player.getUniqueId());
        String key;
        if (politeness == 0) {
            key = "npc.npc-dialogue.ambient.terrible-politeness";
        } else if (playstyle == 0) {
            key = "npc.npc-dialogue.ambient.aggressive-playstyle";
        } else if (politeness >= 5 || bridge.isKindPlaystyle(player.getUniqueId())) {
            key = "npc.npc-dialogue.ambient.friendly";
        } else {
            return;
        }
        say(player, key);
    }

    /** @return true, если фраза реально была отправлена (список в конфиге не пуст). */
    private boolean say(Player player, String configPath) {
        List<String> messages = plugin.getConfig().getStringList(configPath);
        if (messages.isEmpty()) {
            return false;
        }
        player.sendMessage(mm.deserialize(messages.get(random.nextInt(messages.size()))));
        return true;
    }
}
