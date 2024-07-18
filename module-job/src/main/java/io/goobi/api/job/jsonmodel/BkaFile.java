package io.goobi.api.job.jsonmodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonRootName(value = "File")
public class BkaFile {

    @JsonProperty("scanId")
    private int scanId;

    @JsonProperty("fuehrendAkt")
    private String fuehrendAkt;

    @JsonProperty("dokumentArt")
    private String dokumentArt;

    @JsonProperty("ordnungszahl")
    private String ordnungszahl;

    @JsonProperty("ordnungszahlMappe")
    private String ordnungszahlMappe;

    @JsonProperty("filename")
    private String filename;

    @JsonProperty("foldername")
    private String foldername;

    @JsonProperty("filesize")
    private int filesize;

    @JsonProperty("md5")
    private String md5;

    @JsonProperty("mimetype")
    private String mimetype;
}
