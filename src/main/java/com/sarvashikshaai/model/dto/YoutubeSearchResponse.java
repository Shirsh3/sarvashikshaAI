package com.sarvashikshaai.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class YoutubeSearchResponse {

    public List<YoutubeItem> items;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class YoutubeItem {
        public VideoId id;

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class VideoId {
            public String videoId;
        }
    }
}
