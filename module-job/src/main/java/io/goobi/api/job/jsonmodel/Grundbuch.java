package io.goobi.api.job.jsonmodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Grundbuch {

    @JsonProperty("kg")
    private String kg;

    @JsonProperty("ez")
    private String ez;
}
