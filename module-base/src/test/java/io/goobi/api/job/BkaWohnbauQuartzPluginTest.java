package io.goobi.api.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.logging.log4j.Logger;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ConfigurationHelper.class, Helper.class, ConfigPlugins.class, BkaWohnbauQuartzPlugin.class })
@PowerMockIgnore({ "javax.management.*", "javax.net.ssl.*", "jdk.internal.reflect.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*",
        "org.w3c.*", "javax.crypto.*", "javax.crypto.JceSecurity" })

public class BkaWohnbauQuartzPluginTest {

    private BkaWohnbauQuartzPlugin plugin;
    private ObjectMapper objectMapper;
    private static final DateTimeFormatter formatterDateTime = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private Logger mockLogger;

    @Before
    public void setUp() {
        plugin = new BkaWohnbauQuartzPlugin();
        objectMapper = new ObjectMapper();

        plugin = PowerMock.createPartialMock(BkaWohnbauQuartzPlugin.class, "parseConfiguration", "analyseContent");
        mockLogger = PowerMock.createMock(Logger.class);
        setLogger(plugin, mockLogger);
    }

    @Test
    public void testExecute() throws Exception {
        List<BkaWohnbauCollection> mockCollections = new ArrayList<>();
        BkaWohnbauCollection mockCollection = new BkaWohnbauCollection();
        mockCollection.setName("Test Collection");
        mockCollections.add(mockCollection);

        EasyMock.expect(plugin.parseConfiguration()).andReturn(mockCollections).anyTimes();

        plugin.analyseContent(mockCollection);
        EasyMock.expectLastCall().times(1);

        mockLogger.debug("BkaWohnbau Plugin executed");
        EasyMock.expectLastCall();

        PowerMock.replay(plugin, mockLogger);

        plugin.execute();

        PowerMock.verify(plugin, mockLogger);
    }

    private void setLogger(BkaWohnbauQuartzPlugin plugin, Logger mockLogger) {
        try {
            java.lang.reflect.Field field = BkaWohnbauQuartzPlugin.class.getDeclaredField("log");
            field.setAccessible(true);
            field.set(plugin, mockLogger);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //@Test
    public void testParseConfiguration() {
        XMLConfiguration config = PowerMock.createMock(XMLConfiguration.class);
        List<HierarchicalConfiguration> mockCollectionConfigs = mockCollectionConfigs();

        EasyMock.expect(config.configurationsAt("./collection")).andReturn(mockCollectionConfigs);
        config.setExpressionEngine(EasyMock.anyObject(XPathExpressionEngine.class));
        EasyMock.expectLastCall().anyTimes();
        config.setReloadingStrategy(EasyMock.anyObject(FileChangedReloadingStrategy.class));
        EasyMock.expectLastCall().anyTimes();

        PowerMock.replay(config);

        PowerMock.mockStatic(ConfigPlugins.class);
        EasyMock.expect(ConfigPlugins.getPluginConfig(EasyMock.anyString())).andReturn(config).anyTimes();
        PowerMock.replay(ConfigPlugins.class);

        plugin.setConfig(ConfigPlugins.getPluginConfig("intranda_quartz_bka_wohnbau"));
        List<BkaWohnbauCollection> collections = plugin.parseConfiguration();

        assertNotNull(collections);
        assertEquals(1, collections.size());

        BkaWohnbauCollection collection = collections.get(0);
        assertEquals("Test Collection", collection.getName());
        assertEquals("Test Project", collection.getProject());
        assertEquals("Test Template", collection.getTemplate());
        assertEquals("http://test-endpoint", collection.getS3endpoint());
        assertEquals("test-user", collection.getS3user());
        assertEquals("test-password", collection.getS3password());
        assertEquals("test-bucket", collection.getS3bucket());
        assertEquals("test-prefix", collection.getS3prefix());
    }

    private List<HierarchicalConfiguration> mockCollectionConfigs() {
        List<HierarchicalConfiguration> collectionConfigs = new ArrayList<>();
        HierarchicalConfiguration config = new HierarchicalConfiguration();
        config.addProperty("name", "Test Collection");
        config.addProperty("project", "Test Project");
        config.addProperty("template", "Test Template");
        config.addProperty("s3endpoint", "http://test-endpoint");
        config.addProperty("s3user", "test-user");
        config.addProperty("s3password", "test-password");
        config.addProperty("s3bucket", "test-bucket");
        config.addProperty("s3prefix", "test-prefix");
        collectionConfigs.add(config);
        return collectionConfigs;
    }

    @Test
    public void testGetCreationTime() throws IOException {
        Path path = Paths.get("src/test/resources/testfile.txt");

        PowerMock.mockStatic(Files.class);
        FileTime fileTime = FileTime.fromMillis(1577836800000L); // Beispielzeit: 01.01.2020 00:00:00 UTC
        EasyMock.expect(Files.getAttribute(path, "creationTime")).andReturn(fileTime);
        PowerMock.replay(Files.class);

        String creationTime = BkaWohnbauQuartzPlugin.getCreationTime(path);
        LocalDateTime expectedTime = LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.systemDefault());
        assertEquals(expectedTime.format(formatterDateTime), creationTime);
    }

}
