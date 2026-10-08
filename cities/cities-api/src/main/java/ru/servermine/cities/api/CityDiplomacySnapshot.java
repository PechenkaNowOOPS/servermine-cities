package ru.servermine.cities.api;

import java.util.List;

public record CityDiplomacySnapshot(List<CityTreatyView> treaties, List<CityDiplomacyEvent> events,
                                    List<CitySummary> otherCities) {
    public CityDiplomacySnapshot {
        treaties = List.copyOf(treaties); events = List.copyOf(events); otherCities = List.copyOf(otherCities);
    }

    public record CitySummary(java.util.UUID id, String name, CityStage stage) { }
}
