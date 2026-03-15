package com.sarvashikshaai.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WikiMediaResponse {

    public List<WikiMediaItem> items;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WikiMediaItem {
        public Original original;
        public List<Object> srcset;

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Original {
            public String source;
        }
    }
}
