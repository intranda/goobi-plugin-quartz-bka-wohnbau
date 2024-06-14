package io.goobi.api.job;

import lombok.Data;

@Data
public class BkaWohnbauCollection {
    private String name;
    private String source;
    private String project;
    private String template;

    private String s3endpoint;
    private String s3user;
    private String s3password;
    private String s3bucket;
    private String s3prefix;
}
