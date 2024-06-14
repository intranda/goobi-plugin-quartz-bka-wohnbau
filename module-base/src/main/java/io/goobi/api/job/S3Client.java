package io.goobi.api.job;

import java.net.URISyntaxException;

import io.goobi.extension.S3ClientHelper;

public class S3Client {

    // moved to separate class in order to mock it in junit tests

    public static S3ClientHelper getInstance(String s3endpoint, String s3user, String s3password) throws URISyntaxException {
        return new S3ClientHelper(s3endpoint, s3user, s3password);
    }

}
