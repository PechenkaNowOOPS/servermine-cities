package ru.servermine.cities.api;

import java.util.Objects;
import java.util.UUID;

public record CityMarketCreateRequest(UUID listingId, UUID sellerId, String itemName, byte[] itemData, long price) {
    public CityMarketCreateRequest {
        Objects.requireNonNull(listingId); Objects.requireNonNull(sellerId); Objects.requireNonNull(itemName);
        itemData = Objects.requireNonNull(itemData).clone();
        if (itemName.isBlank() || itemData.length == 0 || price <= 0) throw new IllegalArgumentException("Invalid listing request");
    }
    @Override public byte[] itemData() { return itemData.clone(); }
}
