package io.goobi.api.job.jsonmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AdresseTest {

    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testSerialization() throws JsonProcessingException {
        Adresse adresse = new Adresse();
        adresse.setGemeindKZ("12345");
        adresse.setGemeindename("Musterstadt");
        adresse.setEz("EZ123");
        adresse.setOrt("Ortsteil");
        adresse.setPlz("54321");
        adresse.setHauptAdresse("Hauptstrasse 1");
        adresse.setIdentAdressen("ID123");
        adresse.setStrasse("Strasse 2");
        adresse.setTuer("1A");
        adresse.setStiege("2B");
        adresse.setHistorischeAdresse("Altstrasse 3");
        adresse.setAnmerkung("Testanmerkung");

        String json = objectMapper.writeValueAsString(adresse);

        assertNotNull(json);
        System.out.println(json);
    }

    @Test
    public void testDeserialization() throws JsonMappingException, JsonProcessingException {
        String json =
                "{ \"gemeindKZ\": \"12345\", \"gemeindename\": \"Musterstadt\", \"ez\": \"EZ123\", \"ort\": \"Ortsteil\", \"plz\": \"54321\", \"hauptAdresse\": \"Hauptstrasse 1\", \"identAdressen\": \"ID123\", \"strasse\": \"Strasse 2\", \"tuer\": \"1A\", \"stiege\": \"2B\", \"historischeAdresse\": \"Altstrasse 3\", \"anmerkung\": \"Testanmerkung\" }";

        Adresse adresse = objectMapper.readValue(json, Adresse.class);

        assertNotNull(adresse);
        assertEquals("12345", adresse.getGemeindKZ());
        assertEquals("Musterstadt", adresse.getGemeindename());
        assertEquals("EZ123", adresse.getEz());
        assertEquals("Ortsteil", adresse.getOrt());
        assertEquals("54321", adresse.getPlz());
        assertEquals("Hauptstrasse 1", adresse.getHauptAdresse());
        assertEquals("ID123", adresse.getIdentAdressen());
        assertEquals("Strasse 2", adresse.getStrasse());
        assertEquals("1A", adresse.getTuer());
        assertEquals("2B", adresse.getStiege());
        assertEquals("Altstrasse 3", adresse.getHistorischeAdresse());
        assertEquals("Testanmerkung", adresse.getAnmerkung());
    }
}
