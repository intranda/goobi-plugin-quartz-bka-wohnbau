package io.goobi.api.job.jsonmodel;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Grundbuch {

    @JsonProperty("kg")
    private String kg;

    @JsonProperty("ez")
    private String ez;
}
