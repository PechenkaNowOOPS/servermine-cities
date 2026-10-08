package ru.servermine.cities.api;

public enum CityTreatyType {
    ALLIANCE("Союз"), NON_AGGRESSION("Пакт о ненападении"), TRADE("Торговый договор");
    private final String displayName;
    CityTreatyType(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
