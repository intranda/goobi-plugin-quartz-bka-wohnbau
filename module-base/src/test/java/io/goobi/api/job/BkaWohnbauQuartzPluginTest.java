package io.goobi.api.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.easymock.EasyMock;
import org.goobi.beans.Docket;
import org.goobi.beans.Process;
import org.goobi.beans.Project;
import org.goobi.beans.Ruleset;
import org.goobi.production.flow.jobs.HistoryAnalyserJob;
import org.goobi.production.flow.statistics.hibernate.FilterHelper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;

import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.BeanHelper;
import de.sub.goobi.persistence.managers.ProcessManager;
import io.goobi.extension.S3ClientHelper;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ConfigurationHelper.class, S3ClientHelper.class, S3Client.class, FilterHelper.class, ProcessManager.class, BeanHelper.class,
        HistoryAnalyserJob.class })
@PowerMockIgnore({ "javax.management.*", "javax.net.ssl.*", "jdk.internal.reflect.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*",
        "org.w3c.*", "javax.crypto.*", "javax.crypto.JceSecurity" })
@SuppressStaticInitializationFor("org.goobi.production.flow.statistics.hibernate.FilterHelper")
public class BkaWohnbauQuartzPluginTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static String resourcesFolder;

    @BeforeClass
    public static void setUpClass() {
        resourcesFolder = "src/test/resources/"; // for junit tests in eclipse
        if (!Files.exists(Paths.get(resourcesFolder))) {
            resourcesFolder = "target/test-classes/"; // to run mvn test from cli or in jenkins
        }
        String log4jFile = resourcesFolder + "log4j2.xml"; // for junit tests in eclipse
        System.setProperty("log4j.configurationFile", log4jFile);
    }

    @Before
    public void setUp() throws Exception {
        // mock configuration
        PowerMock.mockStatic(ConfigurationHelper.class);
        ConfigurationHelper configurationHelper = EasyMock.createMock(ConfigurationHelper.class);
        EasyMock.expect(ConfigurationHelper.getInstance()).andReturn(configurationHelper).anyTimes();
        EasyMock.expect(configurationHelper.getConfigurationFolder()).andReturn(resourcesFolder).anyTimes();
        EasyMock.expect(configurationHelper.getRulesetFolder()).andReturn(resourcesFolder).anyTimes();

        EasyMock.expect(configurationHelper.getMetsEditorLockingTime()).andReturn(1000l).anyTimes();

        EasyMock.expect(configurationHelper.getDatabaseLeftTruncationCharacter()).andReturn("").anyTimes();
        EasyMock.expect(configurationHelper.getDatabaseRightTruncationCharacter()).andReturn("").anyTimes();

        Path tempFolder = folder.newFolder().toPath();
        EasyMock.expect(configurationHelper.getTemporaryFolder()).andReturn(tempFolder.toString()).anyTimes();
        EasyMock.expect(configurationHelper.getMetadataFolder()).andReturn(tempFolder.toString()).anyTimes();

        EasyMock.expect(configurationHelper.useS3()).andReturn(false).anyTimes();

        // mock database access
        PowerMock.mockStatic(FilterHelper.class);
        EasyMock.expect(FilterHelper.criteriaBuilder(EasyMock.anyString(), EasyMock.anyBoolean(), EasyMock.anyBoolean(), EasyMock.anyBoolean(),
                EasyMock.anyBoolean(), EasyMock.anyBoolean(), EasyMock.anyBoolean())).andReturn("").anyTimes();
        PowerMock.mockStatic(ProcessManager.class);
        EasyMock.expect(
                ProcessManager.getProcesses(EasyMock.anyString(), EasyMock.anyString(), EasyMock.anyInt(), EasyMock.anyInt(), EasyMock.anyObject()))
                .andReturn(Collections.emptyList())
                .anyTimes();

        // process
        Process process = EasyMock.createMock(Process.class);
        Project proj = new Project();

        Ruleset r = new Ruleset();
        r.setDatei("ruleset.xml");
        process.setTitel(EasyMock.anyString());
        process.setIstTemplate(false);
        process.setInAuswahllisteAnzeigen(false);
        process.setProjekt(EasyMock.anyObject());
        process.setRegelsatz(EasyMock.anyObject());
        process.setDocket(EasyMock.anyObject());
        process.setExportValidator(EasyMock.anyObject());
        process.setSchritte(EasyMock.anyObject());
        process.setVorlagen(EasyMock.anyObject());
        process.setWerkstuecke(EasyMock.anyObject());
        process.setEigenschaften(EasyMock.anyObject());

        EasyMock.expect(process.getRegelsatz()).andReturn(r).anyTimes();
        EasyMock.expect(process.getProjekt()).andReturn(proj).anyTimes();
        EasyMock.expect(process.getDocket()).andReturn(new Docket()).anyTimes();
        EasyMock.expect(process.getExportValidator()).andReturn(null);
        EasyMock.expect(process.getSchritteList()).andReturn(new ArrayList<>()).anyTimes();
        EasyMock.expect(process.getVorlagen()).andReturn(new ArrayList<>()).anyTimes();
        EasyMock.expect(process.getWerkstuecke()).andReturn(new ArrayList<>()).anyTimes();
        EasyMock.expect(process.getEigenschaftenList()).andReturn(new ArrayList<>()).anyTimes();
        EasyMock.expect(process.getEigenschaften()).andReturn(new ArrayList<>()).anyTimes();
        EasyMock.expect(process.getTitel()).andReturn("title").anyTimes();
        EasyMock.expect(process.getId()).andReturn(1).anyTimes();
        EasyMock.expect(process.getProcessDataDirectoryIgnoreSwapping()).andReturn(Paths.get(tempFolder.toString(), "1").toString());
        EasyMock.expect(process.getProcessDataDirectory()).andReturn(Paths.get(tempFolder.toString(), "1").toString());
        EasyMock.expect(process.getImagesOrigDirectory(false))
                .andReturn(Paths.get(tempFolder.toString(), "1", "images", "master_folder").toString())
                .anyTimes();
        EasyMock.expect(process.getOcrAltoDirectory())
                .andReturn(Paths.get(tempFolder.toString(), "1", "ocr", "folder_alto").toString())
                .anyTimes();
        EasyMock.expect(process.writeMetadataFile(EasyMock.anyObject())).andReturn(true);
        EasyMock.expect(ProcessManager.getProcessByExactTitle(EasyMock.anyString())).andReturn(process).anyTimes();

        PowerMock.expectNew(Process.class).andReturn(process);

        ProcessManager.saveProcess(EasyMock.anyObject());
        ProcessManager.saveProcess(EasyMock.anyObject());
        PowerMock.mockStatic(HistoryAnalyserJob.class);
        EasyMock.expect(HistoryAnalyserJob.updateHistoryForProzess(EasyMock.anyObject())).andReturn(true).anyTimes();

        // s3 connection
        S3ClientHelper s3 = EasyMock.createMock(S3ClientHelper.class);
        PowerMock.expectNew(S3ClientHelper.class, EasyMock.anyString(), EasyMock.anyString(), EasyMock.anyString()).andReturn(s3).anyTimes();
        List<String> s3Content = new ArrayList<>();
        s3Content.add("sample/content_01/");
        EasyMock.expect(s3.getContentList("bwsf", "")).andReturn(s3Content).anyTimes();

        s3.downloadAllFiles(EasyMock.anyString(), EasyMock.anyString(), EasyMock.anyObject());
        s3.close();
        s3.close();

        // prepare sample data

        Path downloadFolder = Paths.get(tempFolder.toString(), "BWSF_sample_00");
        if (!Files.exists(downloadFolder)) {
            Files.createDirectories(downloadFolder);
        }
        // copy json file
        Path jsonFile = Paths.get(resourcesFolder, "ST-1431_01.json");
        Files.copy(jsonFile, Paths.get(downloadFolder.toString(), "ST-1431_01.json"));
        // copy pdf files
        Path pdfFile = Paths.get(resourcesFolder, "sample.pdf");
        Files.copy(pdfFile, Paths.get(downloadFolder.toString(), "ST-1431_sonstige Dokumente_062.pdf"));
        Files.copy(pdfFile, Paths.get(downloadFolder.toString(), "ST-1431_43.pdf"));

        EasyMock.replay(configurationHelper, s3, process);
        PowerMock.replayAll();
    }

    @Test
    public void testConstructor() {
        BkaWohnbauQuartzPlugin plugin = new BkaWohnbauQuartzPlugin();
        assertNotNull(plugin);
    }

    @Test
    public void testJobName() {
        BkaWohnbauQuartzPlugin plugin = new BkaWohnbauQuartzPlugin();
        assertEquals("intranda_quartz_bka_wohnbau", plugin.getJobName());
    }

    @Test
    public void testParseConfiguration() {
        BkaWohnbauQuartzPlugin plugin = new BkaWohnbauQuartzPlugin();
        List<BkaWohnbauCollection> collection = plugin.parseConfiguration();
        assertNotNull(collection);
        BkaWohnbauCollection col1 = collection.get(0);
        assertEquals("BWSF", col1.getName());
    }

    @Test
    public void testCreationTime() throws Exception {
        Path path = Paths.get(resourcesFolder, "log4j2.xml");

        String creationTime = BkaWohnbauQuartzPlugin.getCreationTime(path);
        assertNotNull(creationTime);
        assertEquals(19, creationTime.length());
    }

    @Test
    public void testAnalyseContent() {
        BkaWohnbauQuartzPlugin plugin = new BkaWohnbauQuartzPlugin();
        List<BkaWohnbauCollection> collection = plugin.parseConfiguration();
        BkaWohnbauCollection col1 = collection.get(0);
        try {
            plugin.analyseContent(col1);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Test
    public void testExecute() throws Exception {

    }

}
