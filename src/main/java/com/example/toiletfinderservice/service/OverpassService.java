package com.example.toiletfinderservice.service;

import com.example.toiletfinderservice.dto.RequestLocationDto;
import com.example.toiletfinderservice.dto.ToiletDto;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class OverpassService {

    @Value("${overpass.url}")
    private String overpassUrl;

    public List<ToiletDto> findNearbyToilets(RequestLocationDto location) {
        List<ToiletDto> toiletDtos = new ArrayList<>();

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String query = String.format(
                    "[out:json]; node[amenity=toilets](around:%d,%f,%f); out;",
                    location.getRadius(), location.getLat(), location.getLon()
            );
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpGet request = new HttpGet(overpassUrl + "?data=" + encodedQuery);

            String jsonResponse = EntityUtils.toString(httpClient.execute(request).getEntity());
            JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonArray elements = json.getAsJsonArray("elements");

            for (int i = 0; i < elements.size(); i++) {
                JsonObject element = elements.get(i).getAsJsonObject();
                ToiletDto toiletDto = new ToiletDto();
                toiletDto.setLat(element.get("lat").getAsDouble());
                toiletDto.setLon(element.get("lon").getAsDouble());
                if (element.has("tags")) {
                    JsonObject tags = element.getAsJsonObject("tags");
                    if (tags.has("name")) {
                        toiletDto.setName(tags.get("name").getAsString());
                    }
                }
                toiletDtos.add(toiletDto);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return toiletDtos;
    }
}
