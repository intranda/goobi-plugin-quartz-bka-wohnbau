package io.goobi.api.job.jsonmodel;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class Adresse {

    @JsonProperty("gemeindKZ")
    private String gemeindKZ;

    @JsonProperty("gemeindename")
    private String gemeindename;

    @JsonProperty("ez")
    private String ez;

    @JsonProperty("ort")
    private String ort;

    @JsonProperty("plz")
    private String plz;

    @JsonProperty("hauptAdresse")
    private String hauptAdresse;

    @JsonProperty("identAdressen")
    private String identAdressen;

    @JsonProperty("strasse")
    private String strasse;

    @JsonProperty("tuer")
    private String tuer;

    @JsonProperty("stiege")
    private String stiege;

    @JsonProperty("historischeAdresse")
    private String historischeAdresse;

    @JsonProperty("anmerkung")
    private String anmerkung;
}
