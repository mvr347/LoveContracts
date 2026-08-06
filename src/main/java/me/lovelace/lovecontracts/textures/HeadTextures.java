package me.lovelace.lovecontracts.textures;

/**
 * Централизованное хранилище base64 текстур голов (skull textures), используемых в GUI LoveContracts.
 * <p>
 * Все base64-литералы текстур голов должны объявляться здесь, а не хардкодиться по месту
 * использования — так плагин следует единой точке правды для GUI-текстур, вместо дублирования
 * одних и тех же строк в разных классах ({@code ContractGUI}, {@code ContractCreateGUI},
 * {@code ContractConfirmGUI}, {@code NpcDialogueGUI}, {@code PlayerContractBoardGUI},
 * {@code PlayerContractMyGUI}). Многие из этих констант используются как жёсткий fallback,
 * когда в {@code heads.yml} нет соответствующего ключа — сам {@code heads.yml} остаётся
 * основным, настраиваемым источником текстур ({@link me.lovelace.lovecontracts.util.HeadUtil}).
 */
public final class HeadTextures {

    private HeadTextures() {
        // Утилитарный класс-константа, инстанцирование не предполагается
    }

    /** Кнопка «Закрыть» — используется во всех GUI плагина. */
    public static final String CLOSE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWZkMjQwMDAwMmFkOWZiYmJkMDA2Njk0MWViNWIxYTM4NGFiOWIwZTQ4YTE3OGVlOTZlNGQxMjlhNTIwODY1NCJ9fX0=";

    /** Кнопка сортировки на доске контрактов. */
    public static final String SORT =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjU3YzdlOTZhODAyYzI3MDgwYzdmODA1MzgxNDM2OGVhOTRkZjg2NDQ1OTEyMGU1MTU1NzE4YjUwM3MzZWQ3In19fQ==";

    /** Кнопка фильтра по типу контракта. */
    public static final String FILTER =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGViODFlZjg5MDIzNzk2NTBiYTc5ZjQ1NzIzZDZiOWM4ODgzODhhMDBmYzRlMTkyZjM0NTRmZTE5Mzg4MmVlMSJ9fX0=";

    /** Заблокированный контракт (у игрока уже есть активный). */
    public static final String LOCKED =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmM1MGUzZTYxNTBkMDdjY2EwOWVkNzA3YjI0NDA0M2M5NDM3ZGJkMWJlOThlZTA4YWUwMzQwY2NiNmQ1OGM4OSJ9fX0=";

    /**
     * Стартовый контракт (сложность {@code STARTER}) — та же текстура используется и для
     * значка «текущий взятый контракт» ({@code active-quest}), как и в {@code heads.yml}, где
     * ключи {@code starter} и {@code taken-contract} указывают на одно и то же значение.
     */
    public static final String STARTER_QUEST =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjA2MGZmMmZlOTM2Y2Q5YTdmNDJkMWQ3MDMyNjgxYzYwOGE2MTRkMmU0MGQ0ZDE5NGRlZTk5NTQ1OTA0ZSJ9fX0=";

    /** Сложность «Лёгкая». */
    public static final String DIFFICULTY_EASY =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWYyM2YxMTVjYjk1MjBkZDRkNGNiMjkxMjRkYWJhYzVlNjg0NGY5NmNjZTI0MWEzZWM5Y2E2ZjdhMjk2MjQ3In19fQ==";

    /** Сложность «Средняя». */
    public static final String DIFFICULTY_MEDIUM =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjM3Y2FlNWM1MWViMTU1OGVhODI4ZjU4ZTBkZmY4ZTZiN2IwYjFhMTgzZDczN2VlY2Y3MTQ2NjE3NjEifX19";

    /** Сложность «Сложная». */
    public static final String DIFFICULTY_HARD =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmRhOTExNDM3YjRlY2ZhYTNjMTg5NDE2MjIxN2MwMWI6OGE1NWM4OWJiMmY0ZDQ5MjczNDVjZTVjNzk0In19fQ==";

    /** Кнопка «Создать контракт» — совпадает со значением {@code Gui-Buttons.create} в heads.yml. */
    public static final String CREATE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2VkZDIwYmU5MzUyMDk0OWU2Y2U3ODlkYzRmNDNlZmFlYjI4YzcxN2VlNmJmY2JiZTAyNzgwMTQyZjcxNiJ9fX0=";

    /**
     * Второй, байт-в-байт отличный (на один символ) fallback-литерал для кнопки «Создать»,
     * который ранее жил только в {@code ContractGUI}. Сохранён как есть, а не объединён с
     * {@link #CREATE}, чтобы централизация не меняла тихо рантайм-поведение — расхождение было
     * в коде до этого рефакторинга и не в его рамках.
     */
    public static final String CREATE_LEGACY_FALLBACK =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM3VkZDIwYmU5MzUyMDk0OWU2Y2U3ODlkYzRmNDNlZmFlYjI4YzcxN2VlNmJmY2JiZTAyNzgwMTQyZjcxNiJ9fX0=";

    /** Стрелка «назад» / «предыдущая страница». */
    public static final String PAGINATION_PREVIOUS =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODY1MmUyYjkzNmNhODAyNmJkMjg2NTFkN2M5ZjI4MTlkMmU5MjM2OTc3MzRkMThkZmRiMTM1NTBmOGZkYWQ1ZiJ9fX0=";

    /** Стрелка «следующая страница». */
    public static final String PAGINATION_NEXT =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTEyODVjZDRlZDRlYWIwOGVjN2QyN2IxYTA4M2FiMjVjOTMwZDg0MGIwNDM2MDhhZTc5MzFkOTc2Njg1NmQ3ZSJ9fX0=";

    /** Кнопка подтверждения («Принять контракт»). */
    public static final String CONFIRM =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTMwZjQ1MzdkMjE0ZDM4NjY2ZTYzMDRlOWM4NTFjZDZmN2U0MWEwZWI3YzI1MDQ5YzlkMjJjOGM1ZjY1NDVkZiJ9fX0=";

    /** Кнопка отмены. */
    public static final String CANCEL =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWE2Nzg3YmEzMjU2NGU3YzJmM2EwY2U2NDQ5OGVjYmIyM2I4OTg0NWU1YTY2YjVjZWM3NzM2ZjcyOWVkMzcifX19";

    /** Кнопка выбора шаблона квеста в меню создания контракта. */
    public static final String PRESET =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmFkYzA0OGE3Y2U3OGY3ZGFkNzJhMDdkYTI3ZDg1YzA5MTY4ODFlNTUyMmVlZWQxZTNkYWYyMTdhMzhjMWEifX19";

    /** Кнопка выбора количества в меню создания контракта. */
    public static final String COUNT =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzFiYzJiY2ZiMmJkMzc1OWU2YjFlODZmYzdhNzk1ODVlMTEyN2RkMzU3ZmMyMDI4OTNmOWRlMjQxYmM5ZTUzMCJ9fX0=";

    /** Кнопка выбора награды в меню создания контракта. */
    public static final String REWARD =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTVmZDY3ZDU2ZmZjNTNmYjM2MGExNzg3OWQ5YjUzMzhkNzMzMmQ4ZjEyOTQ5MWE1ZTE3ZThkNmU4YWVhNmMzYSJ9fX0=";

    /** Дефолтная иконка выполненного/проваленного контракта (используется для обоих статусов). */
    public static final String CONTRACT_STATUS_DEFAULT =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWY5M2VkM2YxOTY4NzYxMTNiMmU3NDYwOTMzNDkzYjgxZGE5MWI4ZjM0ZGIzYzUyODhhNjllZWI5NmRlNDBmYiJ9fX0=";
}
