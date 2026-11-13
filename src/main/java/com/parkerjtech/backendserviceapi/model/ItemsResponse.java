package com.parkerjtech.backendserviceapi.model;
import java.util.List;
public record ItemsResponse<T>(List<T> items) {
    public ItemsResponse { items=List.copyOf(items); }
}
