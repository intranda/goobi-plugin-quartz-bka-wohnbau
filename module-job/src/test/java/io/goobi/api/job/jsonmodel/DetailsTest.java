package io.goobi.api.job.jsonmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DetailsTest {

    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testSerialization() throws JsonProcessingException {
        Details details = new Details();
        details.setAnmerkungen("Testanmerkungen");
        details.setKategorie("Testkategorie");
        details.setAuffaelligkeiten("Testauffaelligkeiten");
        details.setDarlehensNehmer("TestdarlehensNehmer");
        details.setDarlehensSchuld("TestdarlehensSchuld");
        details.setRueckzahlung("Testrueckzahlung");
        details.setBksAnmerkung("TestbksAnmerkung");

        String json = objectMapper.writeValueAsString(details);

        assertNotNull(json);
        System.out.println(json);
    }

    @Test
    public void testDeserialization() throws JsonMappingException, JsonProcessingException {
        String json =
                "{ \"anmerkungen\": \"Testanmerkungen\", \"kategorie\": \"Testkategorie\", \"auffaelligkeiten\": \"Testauffaelligkeiten\", \"darlehensNehmer\": \"TestdarlehensNehmer\", \"darlehensSchuld\": \"TestdarlehensSchuld\", \"rueckzahlung\": \"Testrueckzahlung\", \"bksAnmerkung\": \"TestbksAnmerkung\" }";

        Details details = objectMapper.readValue(json, Details.class);

        assertNotNull(details);
        assertEquals("Testanmerkungen", details.getAnmerkungen());
        assertEquals("Testkategorie", details.getKategorie());
        assertEquals("Testauffaelligkeiten", details.getAuffaelligkeiten());
        assertEquals("TestdarlehensNehmer", details.getDarlehensNehmer());
        assertEquals("TestdarlehensSchuld", details.getDarlehensSchuld());
        assertEquals("Testrueckzahlung", details.getRueckzahlung());
        assertEquals("TestbksAnmerkung", details.getBksAnmerkung());
    }
}
