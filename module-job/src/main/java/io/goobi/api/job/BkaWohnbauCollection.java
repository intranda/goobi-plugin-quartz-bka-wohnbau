package io.goobi.api.job;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BkaWohnbauCollection {
    private String name;
    private String project;
    private String template;

    private String s3endpoint;
    private String s3user;
    private String s3password;
    private String s3bucket;
    private String s3prefix;
    private boolean s3forcePathStyle;
}
