package io.goobi.api.job;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Institution;
import org.goobi.beans.Process;
import org.goobi.beans.Processproperty;
import org.goobi.beans.Project;
import org.goobi.beans.Step;
import org.goobi.production.flow.jobs.AbstractGoobiJob;
import org.goobi.production.flow.statistics.hibernate.FilterHelper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.BeanHelper;
import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.ScriptThreadWithoutHibernate;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.persistence.managers.ProcessManager;
import de.sub.goobi.persistence.managers.ProjectManager;
import de.sub.goobi.persistence.managers.PropertyManager;
import io.goobi.api.job.jsonmodel.BkaFile;
import io.goobi.api.job.jsonmodel.DeliveryMetadata;
import lombok.extern.log4j.Log4j2;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.Prefs;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.fileformats.mets.MetsMods;

@Log4j2
public class BkaWohnbauQuartzPlugin extends AbstractGoobiJob {

    private static final DateTimeFormatter formatterDateTime = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final String PROPERTY = "DeliveryHistory";
    private ObjectMapper om = new ObjectMapper();
    private XMLConfiguration config;

    /**
     * When called, this method gets executed
     */
    @Override
    public void execute() {
        // initialize configuration and run through each content block to analyze
        List<BkaWohnbauCollection> collections = parseConfiguration();
        for (BkaWohnbauCollection collection : collections) {
            analyseContent(collection);
        }
        log.debug("BkaWohnbau Plugin executed");
    }

    /**
     * open folder and search inside of it to find new or updated content
     * 
     * @param project
     * @param source
     */
    private void analyseContent(BkaWohnbauCollection collection) {
        log.debug("Analysing content for: " + collection.getProject() + " from " + collection.getSource());

        // run through sub-folders
        // TODO: Adapt this to list the files from an S3 bucket
        List<Path> folders = StorageProvider.getInstance().listFiles(collection.getSource(), NIOFileUtils.folderFilter);
        for (Path folder : folders) {

            // get base name (geschäftszahl) and delivery count (nachlieferung)
            String baseName = StringUtils.substringBefore(folder.getFileName().toString(), "_");
            String deliveryNumber = StringUtils.substringAfter(folder.getFileName().toString(), "_");
            if (StringUtils.isBlank(deliveryNumber)) {
                deliveryNumber = "00";
            }
            String identifier = collection.getName() + "_" + baseName;
            log.debug("Import from folder " + folder + " into process " + baseName + " as delivery " + deliveryNumber);

            // find out if a process for this item exists already
            List<Process> processes = findProcesses(baseName);
            if (processes.size() == 0) {
                log.debug("no process found that matches, create a new one");
                createNewProcess(collection, identifier, folder, deliveryNumber);
            } else {
                log.debug("process does exist already, trying to update it");
                updateExistingProcess(collection, identifier, folder, deliveryNumber, processes.get(0));
            }
        }
    }

    /**
     * Update an existing process for a new delivery
     * 
     * @param collection
     * @param identifier
     * @param folder
     * @param deliveryNumber
     * @param process
     */
    private void updateExistingProcess(BkaWohnbauCollection collection, String identifier, Path folder, String deliveryNumber, Process process) {
        try {

            // add a new delivery into the json-Property
            for (Processproperty pp : process.getEigenschaften()) {
                if (PROPERTY.equals(pp.getTitel())) {
                    List<Delivery> dlist;
                    dlist = om.readValue(pp.getWert(), new TypeReference<List<Delivery>>() {
                    });
                    dlist.add(new Delivery(deliveryNumber, LocalDateTime.now().format(formatterDateTime)));
                    String historyJson = om.writeValueAsString(dlist);
                    pp.setWert(historyJson);
                    PropertyManager.saveProcessProperty(pp);
                    break;
                }
            }
        } catch (JsonProcessingException e) {
            log.error("Error occured while updating an existing process property for process " + process.getTitel(), e);
        }

    }

    /**
     * create a new process for the given folder
     * 
     * @param project
     * @param source
     */
    private void createNewProcess(BkaWohnbauCollection collection, String identifier, Path s3folder, String deliveryNumber) {
        try {

            // Download the full folder of json, pdf and fulltext files
            // TODO: Implement the download of all files into a temporary folder here
            Path folder = s3folder;

            // first try to read the json file
            List<Path> jsonfiles = StorageProvider.getInstance().listFiles(folder.toString(), wohnbauJsonFilter);
            if (jsonfiles.size() == 0) {
                throw new IOException("No JSON file found in folder " + folder + "to import.");
            }
            Path jsonFile = jsonfiles.get(0);
            DeliveryMetadata dm = om.readValue(jsonFile.toFile(), DeliveryMetadata.class);

            // create a new process
            Process workflow = ProcessManager.getProcessByExactTitle(collection.getTemplate());
            Prefs prefs = workflow.getRegelsatz().getPreferences();
            Fileformat fileformat;
            fileformat = new MetsMods(prefs);
            DigitalDocument dd = new DigitalDocument();
            fileformat.setDigitalDocument(dd);

            // add the physical basics
            DocStruct physical = dd.createDocStruct(prefs.getDocStrctTypeByName("BoundBook"));
            dd.setPhysicalDocStruct(physical);
            Metadata mdForPath = new Metadata(prefs.getMetadataTypeByName("pathimagefiles"));
            mdForPath.setValue("file:///");
            physical.addMetadata(mdForPath);

            // add the logical basics
            DocStruct logical = dd
                    .createDocStruct(prefs.getDocStrctTypeByName(collection.getPublicationType()));
            dd.setLogicalDocStruct(logical);

            // identifier and collection
            addMetadata(logical, prefs, "CatalogIDDigital", identifier);
            addMetadata(logical, prefs, "singleDigCollection", collection.getName());
            addMetadata(logical, prefs, "BkaGeschaeftszahl", dm.getGeschaeftszahl());
            if (dm.getGrundbuch() != null) {
                addMetadata(logical, prefs, "BkaGrundbuchKg", dm.getGrundbuch().getKg());
            }

            log.debug("==========================================");
            log.debug(dm.getGeschaeftszahl());
            log.debug(dm.getFondname());

            // add the current delivery
            DocStruct bkaDelivery = dd
                    .createDocStruct(prefs.getDocStrctTypeByName(collection.getDeliveryType()));
            addMetadata(bkaDelivery, prefs, "BkaDeliveryNumber", deliveryNumber);
            addMetadata(bkaDelivery, prefs, "BkaDeliveryDate", getCreationTime(jsonFile));
            logical.addChild(bkaDelivery);

            for (BkaFile f : dm.getFiles()) {
                log.debug("-------------------------------------------");
                log.debug(f.getDokumentArt());
                log.debug(f.getFilename());
                DocStruct bkaDocument = dd
                        .createDocStruct(prefs.getDocStrctTypeByName(collection.getDocumentType()));
                addMetadata(bkaDocument, prefs, "BkaFileFilename", f.getFilename());
                addMetadata(bkaDocument, prefs, "BkaFileDokumentArt", f.getDokumentArt());
                bkaDelivery.addChild(bkaDocument);
            }
            log.debug("==========================================");

            // extract pdf files and create one document per PDF-file
            List<File> pdfFiles = StorageProvider.getInstance()
                    .listFiles(folder.toString(), (path) -> path.toString().matches(".*.(pdf|PDF)"))
                    .stream()
                    .map(Path::toFile)
                    .collect(Collectors.toList());

            // create the process
            BeanHelper bh = new BeanHelper();
            Process process = bh.createAndSaveNewProcess(workflow, identifier,
                    fileformat);

            // set the project
            Project proj = ProjectManager.getProjectByName(collection.getProject());
            process.setProjekt(proj);
            process.setProjectId(proj.getId());

            // set delivery as json-property
            List<Delivery> dlist = new ArrayList<Delivery>();
            dlist.add(new Delivery(deliveryNumber, LocalDateTime.now().format(formatterDateTime)));
            String historyJson = om.writeValueAsString(dlist);

            Processproperty pp = new Processproperty();
            pp.setTitel(PROPERTY);
            pp.setWert(historyJson);
            pp.setProzess(process);
            PropertyManager.saveProcessProperty(pp);

            // update process
            ProcessManager.saveProcess(process);

            // copy media files into the process images folder
            List<Path> files = StorageProvider.getInstance().listFiles(folder.toString(), wohnbauPdfFilter);
            for (Path path : files) {
                StorageProvider.getInstance().copyFile(path, Paths.get(process.getImagesOrigDirectory(false), path.getFileName().toString()));
            }

            // start any open automatic tasks for the created process
            for (Step s : process.getSchritteList()) {
                if (StepStatus.OPEN.equals(s.getBearbeitungsstatusEnum()) && s.isTypAutomatisch()) {
                    ScriptThreadWithoutHibernate myThread = new ScriptThreadWithoutHibernate(s);
                    myThread.startOrPutToQueue();
                }
            }

        } catch (Exception e) {
            log.error("Error while creating a new process for content " + collection.getSource(), e);
        }
    }

    /**
     * add metadata to given docstruct element
     *
     * @param ds
     * @param prefs
     * @param name
     * @param value
     * @throws MetadataTypeNotAllowedException
     * @throws DocStructHasNoTypeException
     */
    private void addMetadata(DocStruct ds, Prefs prefs, String name, String value)
            throws MetadataTypeNotAllowedException, DocStructHasNoTypeException {
        if (StringUtils.isNotBlank(value)) {
            Metadata id = new Metadata(prefs.getMetadataTypeByName(name));
            id.setValue(value);
            ds.addMetadata(id);
        }
    }

    /**
     * find processes with a given search string
     *
     * @return
     */
    private List<Process> findProcesses(String filter) {
        String criteria = FilterHelper.criteriaBuilder(filter, false, null, null, null, true, false);
        if (!criteria.isEmpty()) {
            criteria += " AND ";
        }
        criteria += " istTemplate = false ";
        Institution inst = null;
        List<Process> processes = ProcessManager.getProcesses("prozesse.titel", criteria, 0, 1000, inst);
        return processes;
    }

    @Override
    public String getJobName() {
        return "intranda_quartz_bka_wohnbau";
    }

    /**
     * Parse the configuration file
     */
    public List<BkaWohnbauCollection> parseConfiguration() {
        config = ConfigPlugins.getPluginConfig(getJobName());
        config.setExpressionEngine(new XPathExpressionEngine());
        config.setReloadingStrategy(new FileChangedReloadingStrategy());
        List<HierarchicalConfiguration> collectionConfigs = config.configurationsAt("./collection");

        // read parameters from config and create a list of collections
        List<BkaWohnbauCollection> collections = new ArrayList<BkaWohnbauCollection>();
        for (HierarchicalConfiguration cc : collectionConfigs) {
            BkaWohnbauCollection col = new BkaWohnbauCollection();
            col.setName(cc.getString("./name", "my name"));
            col.setSource(cc.getString("./source", "my source"));
            col.setProject(cc.getString("./project", "my project"));
            col.setTemplate(cc.getString("./template", "my template"));
            col.setPublicationType(cc.getString("./publicationType", "my publicationType"));
            col.setDeliveryType(cc.getString("./deliveryType", "my deliveryType"));
            col.setDocumentType(cc.getString("./documentType", "my documentType"));
            collections.add(col);
        }
        return collections;
    }

    public static final DirectoryStream.Filter<Path> wohnbauPdfFilter = path -> {
        String name = path.getFileName().toString();
        boolean isAllowed = name.toLowerCase().endsWith(".pdf");
        return isAllowed;
    };

    public static final DirectoryStream.Filter<Path> wohnbauJsonFilter = path -> {
        String name = path.getFileName().toString();
        boolean isAllowed = name.toLowerCase().endsWith(".json");
        return isAllowed;
    };

    public static String getCreationTime(Path path) throws IOException {
        FileTime fileTime = (FileTime) Files.getAttribute(path, "creationTime");
        LocalDateTime localDateTime = fileTime
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        return localDateTime.format(formatterDateTime);
    }

    public static void main(String[] args) throws StreamReadException, DatabindException, IOException {

    }
}
