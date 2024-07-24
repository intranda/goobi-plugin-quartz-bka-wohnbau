package io.goobi.api.job.jsonmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BkaFileTest {

    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testSerialization() throws JsonProcessingException {
        BkaFile bkaFile = new BkaFile();
        bkaFile.setScanId(123);
        bkaFile.setFuehrendAkt("Akt123");
        bkaFile.setDokumentArt("Report");
        bkaFile.setOrdnungszahl("OZ123");
        bkaFile.setOrdnungszahlMappe("OZMappe123");
        bkaFile.setFilename("document.pdf");
        bkaFile.setFoldername("folder1");
        bkaFile.setFilesize(1048576);
        bkaFile.setMd5("9e107d9d372bb6826bd81d3542a419d6");
        bkaFile.setMimetype("application/pdf");

        String json = objectMapper.writeValueAsString(bkaFile);

        assertNotNull(json);
        System.out.println(json);
    }

    @Test
    public void testDeserialization() throws JsonMappingException, JsonProcessingException {
        String json =
                "{ \"scanId\": 123, \"fuehrendAkt\": \"Akt123\", \"dokumentArt\": \"Report\", \"ordnungszahl\": \"OZ123\", \"ordnungszahlMappe\": \"OZMappe123\", \"filename\": \"document.pdf\", \"foldername\": \"folder1\", \"filesize\": 1048576, \"md5\": \"9e107d9d372bb6826bd81d3542a419d6\", \"mimetype\": \"application/pdf\" }";

        BkaFile bkaFile = objectMapper.readValue(json, BkaFile.class);

        assertNotNull(bkaFile);
        assertEquals(123, bkaFile.getScanId());
        assertEquals("Akt123", bkaFile.getFuehrendAkt());
        assertEquals("Report", bkaFile.getDokumentArt());
        assertEquals("OZ123", bkaFile.getOrdnungszahl());
        assertEquals("OZMappe123", bkaFile.getOrdnungszahlMappe());
        assertEquals("document.pdf", bkaFile.getFilename());
        assertEquals("folder1", bkaFile.getFoldername());
        assertEquals(1048576, bkaFile.getFilesize());
        assertEquals("9e107d9d372bb6826bd81d3542a419d6", bkaFile.getMd5());
        assertEquals("application/pdf", bkaFile.getMimetype());
    }
}
