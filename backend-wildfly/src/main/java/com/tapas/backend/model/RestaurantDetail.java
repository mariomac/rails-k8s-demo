package com.tapas.backend.model;

import java.util.List;

public class RestaurantDetail extends RestaurantSummary {
    public String description;
    public String address;
    public List<ReviewJson> reviews;
}
