package io.goobi.api.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BkaWohnbauCollectionTest {

    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testSerialization() throws JsonProcessingException {
        BkaWohnbauCollection collection = new BkaWohnbauCollection();
        collection.setName("Wohnbau Project");
        collection.setProject("Project 1");
        collection.setTemplate("Template X");
        collection.setS3endpoint("https://s3.example.com");
        collection.setS3user("user123");
        collection.setS3password("password123");
        collection.setS3bucket("bucket123");
        collection.setS3prefix("prefix123");

        String json = objectMapper.writeValueAsString(collection);

        assertNotNull(json);
        System.out.println(json);
    }

    @Test
    public void testDeserialization() throws JsonMappingException, JsonProcessingException {
        String json =
                "{ \"name\": \"Wohnbau Project\", \"project\": \"Project 1\", \"template\": \"Template X\", \"s3endpoint\": \"https://s3.example.com\", \"s3user\": \"user123\", \"s3password\": \"password123\", \"s3bucket\": \"bucket123\", \"s3prefix\": \"prefix123\" }";

        BkaWohnbauCollection collection = objectMapper.readValue(json, BkaWohnbauCollection.class);

        assertNotNull(collection);
        assertEquals("Wohnbau Project", collection.getName());
        assertEquals("Project 1", collection.getProject());
        assertEquals("Template X", collection.getTemplate());
        assertEquals("https://s3.example.com", collection.getS3endpoint());
        assertEquals("user123", collection.getS3user());
        assertEquals("password123", collection.getS3password());
        assertEquals("bucket123", collection.getS3bucket());
        assertEquals("prefix123", collection.getS3prefix());
    }
}
