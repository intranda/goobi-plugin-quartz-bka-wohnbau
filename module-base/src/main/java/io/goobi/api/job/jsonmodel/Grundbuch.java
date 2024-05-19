package io.goobi.api.job.jsonmodel;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class Grundbuch {

    @JsonProperty("kg")
    private String kg;

    @JsonProperty("ez")
    private String ez;
}
