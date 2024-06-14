package io.goobi.api.job.jsonmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GrundbuchTest {

    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testSerialization() throws JsonProcessingException {
        Grundbuch grundbuch = new Grundbuch();
        grundbuch.setKg("KG123");
        grundbuch.setEz("EZ456");

        String json = objectMapper.writeValueAsString(grundbuch);

        assertNotNull(json);
        System.out.println(json);
    }

    @Test
    public void testDeserialization() throws JsonMappingException, JsonProcessingException {
        String json = "{ \"kg\": \"KG123\", \"ez\": \"EZ456\" }";

        Grundbuch grundbuch = objectMapper.readValue(json, Grundbuch.class);

        assertNotNull(grundbuch);
        assertEquals("KG123", grundbuch.getKg());
        assertEquals("EZ456", grundbuch.getEz());
    }
}
