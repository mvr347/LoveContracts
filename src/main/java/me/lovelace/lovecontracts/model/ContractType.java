package me.lovelace.lovecontracts.model;

public enum ContractType {
    REPEATING,
    ONE_TIME;

    public static ContractType fromString(String value) {
        if (value == null) return REPEATING;
        try {
            return ContractType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return REPEATING;
        }
    }
}
