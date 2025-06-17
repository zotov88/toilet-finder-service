package com.example.toiletfinderservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RequestLocationDto {

    private double lat;
    private double lon;
    private int radius;
}
