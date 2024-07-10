package io.goobi.api.job.jsonmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DeliveryMetadataTest {

    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testSerialization() throws JsonProcessingException {
        DeliveryMetadata deliveryMetadata = new DeliveryMetadata();
        deliveryMetadata.setFondname("FondName");
        deliveryMetadata.setBundesland("Bundesland");
        deliveryMetadata.setGeschaeftszahl("12345");
        deliveryMetadata.setBezugszahlen("Bezugszahlen");
        deliveryMetadata.setAnmerkung("Anmerkung");

        Grundbuch grundbuch = new Grundbuch();
        grundbuch.setKg("KG123");
        grundbuch.setEz("EZ456");
        deliveryMetadata.setGrundbuch(grundbuch);

        Adresse adresse = new Adresse();
        adresse.setGemeindKZ("12345");
        adresse.setGemeindename("Musterstadt");
        deliveryMetadata.setAdresse(adresse);

        Details details = new Details();
        details.setAnmerkungen("Testanmerkungen");
        details.setKategorie("Testkategorie");
        details.setAuffaelligkeiten("Testauffaelligkeiten");
        details.setDarlehensNehmer("TestdarlehensNehmer");
        details.setDarlehensSchuld("TestdarlehensSchuld");
        details.setRueckzahlung("Testrueckzahlung");
        details.setBksAnmerkung("TestbksAnmerkung");
        deliveryMetadata.setDetails(details);

        BkaFile bkaFile = new BkaFile();
        bkaFile.setScanId(123);
        deliveryMetadata.setFiles(new ArrayList<>());
        deliveryMetadata.getFiles().add(bkaFile);

        deliveryMetadata.setDeliveryDate("2023-05-23");
        deliveryMetadata.setDeliveryNumber("DN12345");

        String json = objectMapper.writeValueAsString(deliveryMetadata);

        assertNotNull(json);
        System.out.println(json);
    }

    @Test
    public void testDeserialization() throws JsonMappingException, JsonProcessingException {
        String json =
                "{ \"fondname\": \"FondName\", \"bundesland\": \"Bundesland\", \"geschaeftszahl\": \"12345\", \"bezugszahlen\": \"Bezugszahlen\", \"anmerkung\": \"Anmerkung\", \"grundbuch\": { \"kg\": \"KG123\", \"ez\": \"EZ456\" }, \"adresse\": { \"gemeindKZ\": \"12345\", \"gemeindename\": \"Musterstadt\" }, \"details\": { \"anmerkungen\": \"Testanmerkungen\", \"kategorie\": \"Testkategorie\", \"auffaelligkeiten\": \"Testauffaelligkeiten\", \"darlehensNehmer\": \"TestdarlehensNehmer\", \"darlehensSchuld\": \"TestdarlehensSchuld\", \"rueckzahlung\": \"Testrueckzahlung\", \"bksAnmerkung\": \"TestbksAnmerkung\" }, \"files\": [ { \"scanId\": 123 } ], \"deliveryDate\": \"2023-05-23\", \"deliveryNumber\": \"DN12345\" }";

        DeliveryMetadata deliveryMetadata = objectMapper.readValue(json, DeliveryMetadata.class);

        assertNotNull(deliveryMetadata);
        assertEquals("FondName", deliveryMetadata.getFondname());
        assertEquals("Bundesland", deliveryMetadata.getBundesland());
        assertEquals("12345", deliveryMetadata.getGeschaeftszahl());
        assertEquals("Bezugszahlen", deliveryMetadata.getBezugszahlen());
        assertEquals("Anmerkung", deliveryMetadata.getAnmerkung());

        assertNotNull(deliveryMetadata.getGrundbuch());
        assertEquals("KG123", deliveryMetadata.getGrundbuch().getKg());
        assertEquals("EZ456", deliveryMetadata.getGrundbuch().getEz());

        assertNotNull(deliveryMetadata.getAdresse());
        assertEquals("12345", deliveryMetadata.getAdresse().getGemeindKZ());
        assertEquals("Musterstadt", deliveryMetadata.getAdresse().getGemeindename());

        assertNotNull(deliveryMetadata.getDetails());
        assertEquals("Testanmerkungen", deliveryMetadata.getDetails().getAnmerkungen());
        assertEquals("Testkategorie", deliveryMetadata.getDetails().getKategorie());
        assertEquals("Testauffaelligkeiten", deliveryMetadata.getDetails().getAuffaelligkeiten());
        assertEquals("TestdarlehensNehmer", deliveryMetadata.getDetails().getDarlehensNehmer());
        assertEquals("TestdarlehensSchuld", deliveryMetadata.getDetails().getDarlehensSchuld());
        assertEquals("Testrueckzahlung", deliveryMetadata.getDetails().getRueckzahlung());
        assertEquals("TestbksAnmerkung", deliveryMetadata.getDetails().getBksAnmerkung());

        assertNotNull(deliveryMetadata.getFiles());
        assertEquals(1, deliveryMetadata.getFiles().size());
        assertEquals(123, deliveryMetadata.getFiles().get(0).getScanId());

        assertEquals("2023-05-23", deliveryMetadata.getDeliveryDate());
        assertEquals("DN12345", deliveryMetadata.getDeliveryNumber());
    }
}
