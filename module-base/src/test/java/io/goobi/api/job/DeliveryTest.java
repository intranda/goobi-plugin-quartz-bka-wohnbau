package io.goobi.api.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DeliveryTest {

    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testSerialization() throws JsonProcessingException {
        Delivery delivery = new Delivery();
        delivery.setLabel("Test Label");
        delivery.setDate("2024-05-23");

        String json = objectMapper.writeValueAsString(delivery);

        assertNotNull(json);
        System.out.println(json);
    }

    @Test
    public void testDeserialization() throws JsonMappingException, JsonProcessingException {
        String json = "{ \"label\": \"Test Label\", \"date\": \"2024-05-23\" }";

        Delivery delivery = objectMapper.readValue(json, Delivery.class);

        assertNotNull(delivery);
        assertEquals("Test Label", delivery.getLabel());
        assertEquals("2024-05-23", delivery.getDate());
    }

    @Test
    public void testConstructor() {
        Delivery d = new Delivery("my label", "my date");
        assertNotNull(d);

        assertEquals("my label", d.getLabel());
        assertEquals("my date", d.getDate());

    }
}
