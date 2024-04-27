package io.goobi.api.job;

import static org.junit.Assert.assertEquals;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ConfigurationHelper.class, Helper.class })
@PowerMockIgnore({ "javax.management.*", "javax.net.ssl.*", "jdk.internal.reflect.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*",
        "org.w3c.*", "javax.crypto.*", "javax.crypto.JceSecurity" })
public class BkaWohnbauQuartzPluginTest {

    private static String resourcesFolder;

    @BeforeClass
    public static void setUpClass() throws Exception {
        resourcesFolder = "src/test/resources/"; // for junit tests in eclipse

        if (!Files.exists(Paths.get(resourcesFolder))) {
            resourcesFolder = "target/test-classes/"; // to run mvn test from cli or in jenkins
        }

        String log4jFile = resourcesFolder + "log4j2.xml"; // for junit tests in eclipse
        System.setProperty("log4j.configurationFile", log4jFile);
    }

    @Before
    public void setUp() throws Exception {
        PowerMock.mockStatic(ConfigurationHelper.class);
        ConfigurationHelper configurationHelper = EasyMock.createMock(ConfigurationHelper.class);
        EasyMock.expect(ConfigurationHelper.getInstance()).andReturn(configurationHelper).anyTimes();
        EasyMock.expect(configurationHelper.getConfigurationFolder()).andReturn(resourcesFolder).anyTimes();
        EasyMock.expect(configurationHelper.useS3()).andReturn(false).anyTimes();

        EasyMock.replay(configurationHelper);
        PowerMock.replay(ConfigurationHelper.class);

        PowerMock.mockStatic(Helper.class);
        EasyMock.expect(Helper.getCurrentUser()).andReturn(null).anyTimes();
        PowerMock.replay(Helper.class);
    }

    @Test
    public void testTitle() throws Exception {
        BkaWohnbauQuartzPlugin job = new BkaWohnbauQuartzPlugin();
        assertEquals("intranda_quartz_bka_wohnbau", job.getJobName());
    }

    @Test
    public void testConfiguration() throws Exception {
        BkaWohnbauQuartzPlugin plugin = new BkaWohnbauQuartzPlugin();
        plugin.parseConfiguration();
        assertEquals("test", plugin.getValue());
    }
}
