package io.goobi.api.job.jsonmodel;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Details {

    @JsonProperty("anmerkungen")
    private String anmerkungen;

    @JsonProperty("kategorie")
    private String kategorie;

    @JsonProperty("auffaelligkeiten")
    private String auffaelligkeiten;

    @JsonProperty("darlehensNehmer")
    private String darlehensNehmer;

    @JsonProperty("darlehensSchuld")
    private String darlehensSchuld;

    @JsonProperty("rueckzahlung")
    private String rueckzahlung;

    @JsonProperty("bksAnmerkung")
    private String bksAnmerkung;
}
