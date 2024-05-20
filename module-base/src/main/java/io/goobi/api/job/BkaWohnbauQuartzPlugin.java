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
import org.goobi.production.enums.LogType;
import org.goobi.production.flow.jobs.AbstractGoobiJob;
import org.goobi.production.flow.statistics.hibernate.FilterHelper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.intranda.digiverso.files.naming.PdfFilenameNamer;
import de.intranda.digiverso.pdf.PDFConverter;
import de.intranda.digiverso.pdf.exception.PDFReadException;
import de.intranda.digiverso.pdf.exception.PDFWriteException;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.BeanHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.ScriptThreadWithoutHibernate;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.ProcessManager;
import de.sub.goobi.persistence.managers.ProjectManager;
import de.sub.goobi.persistence.managers.PropertyManager;
import io.goobi.api.job.jsonmodel.BkaFile;
import io.goobi.api.job.jsonmodel.DeliveryMetadata;
import lombok.extern.log4j.Log4j2;
import ugh.dl.ContentFile;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.Prefs;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;
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
            log.debug("Analysing folder " + folder + " for process " + baseName + " as delivery " + deliveryNumber);

            // find out if a process for this item exists already
            List<Process> processes = findProcesses(baseName);
            if (processes.size() == 0) {
                createNewProcess(collection, identifier, folder, deliveryNumber);
            } else {
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
    private void updateExistingProcess(BkaWohnbauCollection collection, String identifier, Path s3folder, String deliveryNumber, Process process) {
        try {

            // read existing json-Property
            for (Processproperty pp : process.getEigenschaften()) {
                if (PROPERTY.equals(pp.getTitel())) {
                    List<Delivery> dlist = om.readValue(pp.getWert(), new TypeReference<List<Delivery>>() {
                    });

                    // check if the same delivery happened already and cancel import then
                    for (Delivery d : dlist) {
                        if (d.getLabel().equals(deliveryNumber)) {
                            log.debug("The delivery " + deliveryNumber + " for record " + identifier
                                    + " gets skipped as it was already imported previously.");
                            return;
                        }
                    }

                    // if this delivery is new download the full folder of json, pdf and fulltext files
                    // TODO: Implement the download of all files into a temporary folder here
                    Path folder = s3folder;

                    // first try to read the json file
                    List<Path> jsonfiles = StorageProvider.getInstance().listFiles(folder.toString(), wohnbauJsonFilter);
                    if (jsonfiles.size() == 0) {
                        throw new IOException("No JSON file found in folder " + folder + "to import.");
                    }
                    Path jsonFile = jsonfiles.get(0);
                    DeliveryMetadata dm = om.readValue(jsonFile.toFile(), DeliveryMetadata.class);
                    dm.setDeliveryDate(getCreationTime(jsonFile));
                    dm.setDeliveryNumber(deliveryNumber);

                    // load mets file of existing process
                    Prefs prefs = process.getRegelsatz().getPreferences();
                    Fileformat fileformat = process.readMetadataFile();
                    DigitalDocument dd = fileformat.getDigitalDocument();

                    // add the new delivery
                    addDelivery(process, folder, dm, prefs, dd);
                    process.writeMetadataFile(fileformat);

                    // keep a copy of the json file inside the source folder
                    Path importfolder = Paths.get(process.getImportDirectory());
                    if (!StorageProvider.getInstance().isFileExists(importfolder)) {
                        StorageProvider.getInstance().createDirectories(importfolder);
                    }
                    StorageProvider.getInstance().copyFile(jsonFile, Paths.get(process.getImportDirectory(), jsonFile.getFileName().toString()));

                    // update the property to include this new delivery
                    dlist.add(new Delivery(deliveryNumber, LocalDateTime.now().format(formatterDateTime)));
                    String historyJson = om.writeValueAsString(dlist);
                    pp.setWert(historyJson);
                    PropertyManager.saveProcessProperty(pp);

                    Helper.addMessageToProcessJournal(process.getId(), LogType.DEBUG,
                            "Process was updated using the quartz-wohnbau-plugin successfully");

                    break;
                }
            }
        } catch (Exception e) {
            Helper.addMessageToProcessJournal(process.getId(), LogType.ERROR,
                    "Process could not updated using the quartz-wohnbau-plugin: " + e.getMessage());
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
            dm.setDeliveryDate(getCreationTime(jsonFile));
            dm.setDeliveryNumber(deliveryNumber);

            // create a new process
            Process workflow = ProcessManager.getProcessByExactTitle(collection.getTemplate());
            Prefs prefs = workflow.getRegelsatz().getPreferences();
            Fileformat fileformat = new MetsMods(prefs);
            DigitalDocument dd = new DigitalDocument();
            fileformat.setDigitalDocument(dd);

            // add the physical basics
            DocStruct physical = dd.createDocStruct(prefs.getDocStrctTypeByName("BoundBook"));
            dd.setPhysicalDocStruct(physical);
            Metadata mdForPath = new Metadata(prefs.getMetadataTypeByName("pathimagefiles"));
            mdForPath.setValue("file:///");
            physical.addMetadata(mdForPath);

            // add the logical basics for record
            DocStruct logical = dd
                    .createDocStruct(prefs.getDocStrctTypeByName(getMapping("recordType")));
            dd.setLogicalDocStruct(logical);

            // get basic metadata for record
            addMetadata(logical, prefs, "identifier", identifier);
            addMetadata(logical, prefs, "collection", collection.getName());
            addMetadata(logical, prefs, "fondname", dm.getFondname());
            addMetadata(logical, prefs, "bundesland", dm.getBundesland());
            addMetadata(logical, prefs, "geschaeftszahl", dm.getGeschaeftszahl());
            addMetadata(logical, prefs, "bezugszahlen", dm.getBezugszahlen());
            addMetadata(logical, prefs, "anmerkungRecord", dm.getAnmerkung());
            addMetadata(logical, prefs, "title", dm.getFondname() + " - " + dm.getGeschaeftszahl());

            // get grundbuch information for record
            if (dm.getGrundbuch() != null) {
                addMetadata(logical, prefs, "grundbuchKg", dm.getGrundbuch().getKg());
                addMetadata(logical, prefs, "grundbuchEz", dm.getGrundbuch().getEz());
            }

            // get adresse information for record
            if (dm.getAdresse() != null) {
                addMetadata(logical, prefs, "adresseGemeindKZ", dm.getAdresse().getGemeindKZ());
                addMetadata(logical, prefs, "adresseGemeindename", dm.getAdresse().getGemeindename());
                addMetadata(logical, prefs, "adresseEz", dm.getAdresse().getEz());
                addMetadata(logical, prefs, "adresseOrt", dm.getAdresse().getOrt());
                addMetadata(logical, prefs, "adressePlz", dm.getAdresse().getPlz());
                addMetadata(logical, prefs, "adresseHauptAdresse", dm.getAdresse().getHauptAdresse());
                addMetadata(logical, prefs, "adresseIdentAdressen", dm.getAdresse().getIdentAdressen());
                addMetadata(logical, prefs, "adresseStrasse", dm.getAdresse().getStrasse());
                addMetadata(logical, prefs, "adresseTuer", dm.getAdresse().getTuer());
                addMetadata(logical, prefs, "adresseStiege", dm.getAdresse().getStiege());
                addMetadata(logical, prefs, "adresseHistorischeAdresse", dm.getAdresse().getHistorischeAdresse());
                addMetadata(logical, prefs, "adresseAnmerkung", dm.getAdresse().getAnmerkung());
            }

            // get details information for record
            if (dm.getDetails() != null) {
                addMetadata(logical, prefs, "detailsAnmerkungen", dm.getDetails().getAnmerkungen());
                addMetadata(logical, prefs, "detailsAuffaelligkeiten", dm.getDetails().getAuffaelligkeiten());
                addMetadata(logical, prefs, "detailsDarlehensNehmer", dm.getDetails().getDarlehensNehmer());
                addMetadata(logical, prefs, "detailsDarlehensSchuld", dm.getDetails().getDarlehensSchuld());
                addMetadata(logical, prefs, "detailsRueckzahlung", dm.getDetails().getRueckzahlung());
                addMetadata(logical, prefs, "detailsBksAnmerkung", dm.getDetails().getBksAnmerkung());
            }

            // create the process
            BeanHelper bh = new BeanHelper();
            Process process = bh.createAndSaveNewProcess(workflow, identifier,
                    fileformat);

            // add the first delivery
            addDelivery(process, folder, dm, prefs, dd);
            process.writeMetadataFile(fileformat);

            // keep a copy of the json file inside the source folder
            Path importfolder = Paths.get(process.getImportDirectory());
            if (!StorageProvider.getInstance().isFileExists(importfolder)) {
                StorageProvider.getInstance().createDirectories(importfolder);
            }
            StorageProvider.getInstance().copyFile(jsonFile, Paths.get(process.getImportDirectory(), jsonFile.getFileName().toString()));

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
            Helper.addMessageToProcessJournal(process.getId(), LogType.DEBUG, "Process was created using the quartz-wohnbau-plugin successfully");

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
     * add a new delivery to an existing file format of a process that was created before
     * 
     * @param process
     * @param folder
     * @param dm
     * @param prefs
     * @param dd
     * @throws TypeNotAllowedForParentException
     * @throws MetadataTypeNotAllowedException
     * @throws IOException
     * @throws TypeNotAllowedAsChildException
     * @throws IllegalArgumentException
     * @throws PDFWriteException
     * @throws PDFReadException
     * @throws SwapException
     * @throws DAOException
     */
    private void addDelivery(Process process, Path folder, DeliveryMetadata dm, Prefs prefs, DigitalDocument dd)
            throws TypeNotAllowedForParentException, MetadataTypeNotAllowedException, IOException, TypeNotAllowedAsChildException,
            IllegalArgumentException, PDFWriteException, PDFReadException, SwapException, DAOException {

        PDFConverter.setFileNamingStrategy(new PdfFilenameNamer("%03d", dm.getDeliveryNumber() + "_"));
        DocStructType pageType = prefs.getDocStrctTypeByName("page");

        // add the current delivery with basic metadata
        DocStruct bkaDelivery = dd
                .createDocStruct(prefs.getDocStrctTypeByName(getMapping("deliveryType")));
        addMetadata(bkaDelivery, prefs, "deliveryNumber", dm.getDeliveryNumber());
        addMetadata(bkaDelivery, prefs, "deliveryDate", dm.getDeliveryDate());
        addMetadata(bkaDelivery, prefs, "title", dm.getDeliveryNumber() + " - " + dm.getDeliveryDate());

        dd.getLogicalDocStruct().addChild(bkaDelivery);

        // add one document per provided pdf file and add metadata
        int counter = 1;
        for (BkaFile bf : dm.getFiles()) {

            // if the current file is a PDF file
            if (bf.getFilename().toLowerCase().endsWith(".pdf")) {
                DocStruct bkaDocument = dd
                        .createDocStruct(prefs.getDocStrctTypeByName(getMapping("documentType")));

                // add all metadata
                addMetadata(bkaDocument, prefs, "scanId", String.valueOf(bf.getScanId()));
                addMetadata(bkaDocument, prefs, "fuehrendAkt", bf.getFuehrendAkt());
                addMetadata(bkaDocument, prefs, "dokumentArt", bf.getDokumentArt());
                addMetadata(bkaDocument, prefs, "ordnungszahl", bf.getOrdnungszahl());
                addMetadata(bkaDocument, prefs, "ordnungszahlMappe", bf.getOrdnungszahlMappe());
                addMetadata(bkaDocument, prefs, "filename", bf.getFilename());
                addMetadata(bkaDocument, prefs, "foldername", bf.getFoldername());
                addMetadata(bkaDocument, prefs, "filesize", String.valueOf(bf.getFilesize()));
                addMetadata(bkaDocument, prefs, "md5", bf.getMd5());
                addMetadata(bkaDocument, prefs, "mimetype", bf.getMimetype());
                addMetadata(bkaDocument, prefs, "title", StringUtils.substringBefore(bf.getFilename(), "."));

                // extract pdf file
                File pdf = new File(folder.toFile(), bf.getFilename());
                Path images = Paths.get(process.getImagesOrigDirectory(false));
                Path alto = Paths.get(process.getOcrAltoDirectory());
                if (!StorageProvider.getInstance().isFileExists(alto)) {
                    StorageProvider.getInstance().createDirectories(alto);
                }

                File tempFolderOther = new File(ConfigurationHelper.getInstance().getTemporaryFolder());
                List<File> imageFiles =
                        PDFConverter.writeImages(pdf, images.toFile(), counter, 300, "tif",
                                tempFolderOther, getImageGenerationMethod(), getImageGenerationParams());
                List<File> altoFiles = PDFConverter.writeAltoFiles(pdf, alto.toFile(), imageFiles, false, counter);

                // add all image files to created delivery docstruct
                for (File image : imageFiles) {
                    DocStruct dsPage = dd.createDocStruct(pageType);
                    dd.getPhysicalDocStruct().addChild(dsPage);

                    // add physical information
                    Metadata metaPhysPageNumber = new Metadata(prefs.getMetadataTypeByName("physPageNumber"));
                    metaPhysPageNumber.setValue(String.valueOf(dd.getPhysicalDocStruct().getAllChildren().size()));
                    dsPage.addMetadata(metaPhysPageNumber);

                    // add logical information
                    Metadata metaLogPageNumber = new Metadata(prefs.getMetadataTypeByName("logicalPageNumber"));
                    int count = imageFiles.indexOf(image) + 1;
                    metaLogPageNumber.setValue(String.valueOf(count));
                    dsPage.addMetadata(metaLogPageNumber);

                    // create ContentFile
                    ContentFile cf = new ContentFile();
                    cf.setMimetype("image/tiff");
                    cf.setLocation(image.getAbsolutePath());
                    dsPage.addContentFile(cf);

                    // assign pages to docstructs
                    dd.getLogicalDocStruct().addReferenceTo(dsPage, "logical_physical");
                    bkaDelivery.addReferenceTo(dsPage, "logical_physical");
                    bkaDocument.addReferenceTo(dsPage, "logical_physical");
                }

                bkaDelivery.addChild(bkaDocument);
            }
        }

    }

    /**
     * get the method for pdf extraction from config file
     *
     * @return
     */
    private String getImageGenerationMethod() {
        return config.getString("imageGenerator", "pdftoppm");
    }

    /**
     * get additional parameters for pdf extraction from config file
     *
     * @return
     */
    private String[] getImageGenerationParams() {
        return config.getStringArray("imageGeneratorParameter");
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
            String field = getMapping(name);
            Metadata id = new Metadata(prefs.getMetadataTypeByName(field));
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
    private List<BkaWohnbauCollection> parseConfiguration() {
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
            collections.add(col);
        }
        return collections;
    }

    private String getMapping(String name) {
        return config.getString("./mapping/" + name);
    }

    private static final DirectoryStream.Filter<Path> wohnbauJsonFilter = path -> {
        String name = path.getFileName().toString();
        boolean isAllowed = name.toLowerCase().endsWith(".json");
        return isAllowed;
    };

    private static String getCreationTime(Path path) throws IOException {
        FileTime fileTime = (FileTime) Files.getAttribute(path, "creationTime");
        LocalDateTime localDateTime = fileTime
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        return localDateTime.format(formatterDateTime);
    }

}
