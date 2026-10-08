package ru.servermine.cities.gui;

public enum MenuType {
    MAIN('\uE001', "Главное меню"),
    TERRITORY('\uE002', "Территория"),
    PURCHASE('\uE003', "Покупка территории"),
    PURCHASE_CONFIRM('\u0000', "Подтверждение покупки чанка"),
    UPGRADES('\uE004', "Улучшения"),
    TREASURY('\uE005', "Казна"),
    TREASURY_TRANSFER_TARGETS('\u0000', "Перевод в казну города"),
    TREASURY_TRANSFER_CONFIRM('\u0000', "Подтверждение перевода"),
    RESIDENTS('\uE006', "Жители"),
    MEMBER_KICK_CONFIRM('\u0000', "Исключение участника"),
    MEMBER_ROLE_MANAGEMENT('\u0000', "Должность участника"),
    MANAGEMENT('\uE007', "Управление"),
    CITY_LAWS('\u0000', "Законы города"),
    CITY_TAX('\u0000', "Налоги и сборы"),
    DIPLOMACY('\uE008', "Дипломатия"),
    DIPLOMACY_TARGETS('\u0000', "Выбор города"),
    DIPLOMACY_CREATE('\u0000', "Новое соглашение"),
    MARKET('\u0000', "Рынок"),
    MARKET_MY_LISTINGS('\u0000', "Мои объявления"),
    MARKET_CONFIRM('\u0000', "Покупка на рынке"),
    MARKET_CANCEL_CONFIRM('\u0000', "Отмена объявления"),
    MARKET_CREATE_CONFIRM('\u0000', "Новое объявление"),
    FOUNDING('\u0000', "Основание города"),
    FOUNDING_CONFIRM('\u0000', "Подтверждение основания"),
    PROGRESSION('\u0000', "Развитие города"),
    PROMOTION_CONFIRM('\u0000', "Подтверждение перехода"),
    UPGRADE_CONFIRM('\u0000', "Подтверждение улучшения"),
    ADMIN_TOOLS('\u0000', "Админ-возможности"),
    ADMIN_TARGET('\u0000', "Тесты города"),
    ADMIN_RECOVERY('\u0000', "Сверка платежей"),
    ADMIN_RECOVERY_DETAIL('\u0000', "Операция платежа");

    private final char glyph;
    private final String fallbackTitle;

    MenuType(char glyph, String fallbackTitle) {
        this.glyph = glyph;
        this.fallbackTitle = fallbackTitle;
    }

    public char glyph() {
        return glyph;
    }

    public String fallbackTitle() {
        return fallbackTitle;
    }
}
