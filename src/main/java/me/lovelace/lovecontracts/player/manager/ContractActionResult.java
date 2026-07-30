package me.lovelace.lovecontracts.player.manager;

/** Результат действия над контрактом — готовая к показу MiniMessage-строка. */
public record ContractActionResult(boolean success, String message) {

    public static ContractActionResult ok(String message) {
        return new ContractActionResult(true, message);
    }

    public static ContractActionResult fail(String message) {
        return new ContractActionResult(false, message);
    }
}
