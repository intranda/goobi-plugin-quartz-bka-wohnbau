package io.goobi.api.job.jsonmodel;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class DeliveryMetadata {

    @JsonProperty("fondname")
    private String fondname;

    @JsonProperty("bundesland")
    private String bundesland;

    @JsonProperty("geschaeftszahl")
    private String geschaeftszahl;

    @JsonProperty("bezugszahlen")
    private String bezugszahlen;

    @JsonProperty("anmerkung")
    private String anmerkung;

    @JsonProperty("grundbuch")
    private Grundbuch grundbuch;

    @JsonProperty("adresse")
    private Adresse adresse;

    @JsonProperty("details")
    private Details details;

    @JsonProperty("files")
    private ArrayList<BkaFile> files;

    private String deliveryDate;
    private String deliveryNumber;
}
