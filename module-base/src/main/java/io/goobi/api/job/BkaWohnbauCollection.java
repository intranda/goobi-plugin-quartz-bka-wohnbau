package io.goobi.api.job;

import lombok.Data;

@Data
public class BkaWohnbauCollection {
    private String source;
    private String project;
    private String template;
    private String publicationType;
    private String deliveryType;
    private String documentType;
    private String name;
}
