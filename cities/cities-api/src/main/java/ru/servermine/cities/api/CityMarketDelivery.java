package ru.servermine.cities.api;

import java.util.Objects;
import java.util.UUID;

/** A purchased item held by the durable buyer mailbox until claimed. */
public record CityMarketDelivery(UUID id, UUID buyerId, byte[] itemData) {
    public CityMarketDelivery {
        Objects.requireNonNull(id); Objects.requireNonNull(buyerId);
        itemData = Objects.requireNonNull(itemData).clone();
        if (itemData.length == 0) throw new IllegalArgumentException("Empty delivery item");
    }
    @Override public byte[] itemData() { return itemData.clone(); }
}
