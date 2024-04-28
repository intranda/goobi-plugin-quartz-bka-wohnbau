package io.goobi.api.job;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Institution;
import org.goobi.beans.Process;
import org.goobi.beans.Project;
import org.goobi.beans.Step;
import org.goobi.production.flow.jobs.AbstractGoobiJob;
import org.goobi.production.flow.statistics.hibernate.FilterHelper;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.BeanHelper;
import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.ScriptThreadWithoutHibernate;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.persistence.managers.ProcessManager;
import de.sub.goobi.persistence.managers.ProjectManager;
import lombok.extern.log4j.Log4j2;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.Prefs;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.fileformats.mets.MetsMods;

@Log4j2
public class BkaWohnbauQuartzPlugin extends AbstractGoobiJob {

    /**
     * When called, this method gets executed
     */
    @Override
    public void execute() {
        // initialize configuration and run through each content block to analyse
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
        System.out.println("Analysing content for: " + collection.getProject() + " from " + collection.getSource());
        log.debug("Analysing content for: " + collection.getProject() + " from " + collection.getSource());

        // run through sub-folders
        List<Path> folders = StorageProvider.getInstance().listFiles(collection.getSource(), NIOFileUtils.folderFilter);
        for (Path folder : folders) {

            // get base name (geschäftszahl) and delivery count (nachlieferung)
            String baseName = StringUtils.substringBefore(folder.getFileName().toString(), "_");
            String deliveryNumber = StringUtils.substringAfter(folder.getFileName().toString(), "_");
            if (StringUtils.isBlank(deliveryNumber)) {
                deliveryNumber = "00";
            }
            String identifier = collection.getName() + "_" + baseName;
            System.out.println("Import from folder " + folder + " into process " + baseName + " as delivery " + deliveryNumber);
            log.debug("Import from folder " + folder + " into process " + baseName + " as delivery " + deliveryNumber);

            // find out if a process for this item exists already
            List<Process> processes = findProcesses(baseName);
            if (processes.size() == 0) {
                System.out.println("no process found that matches, create a new one");
                log.debug("no process found that matches, create a new one");
                createNewProcess(collection, identifier, folder, deliveryNumber);
            } else {
                System.out.println("process does exist already, trying to update it");
                log.debug("process does exist already, trying to update it");
                //updateExistingProcess(content, identifier, folder, deliveryNumber, processes.get(0));
            }
        }
    }

    /**
     * create a new process for the given folder
     * 
     * @param project
     * @param source
     */
    private void createNewProcess(BkaWohnbauCollection collection, String identifier, Path folder, String deliveryNumber) {
        try {
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
                    .createDocStruct(prefs.getDocStrctTypeByName(collection.getType()));
            dd.setLogicalDocStruct(logical);

            // identifier and collection
            addMetadata(logical, prefs, "CatalogIDDigital", identifier);
            addMetadata(logical, prefs, "singleDigCollection", collection.getName());

            // create the process
            Process process = new BeanHelper().createAndSaveNewProcess(workflow, identifier,
                    fileformat);

            // set the project and update process
            Project proj = ProjectManager.getProjectByName(collection.getProject());
            process.setProjekt(proj);
            process.setProjectId(proj.getId());
            ProcessManager.saveProcess(process);

            // start any open automatic tasks for the created process
            for (Step s : process.getSchritteList()) {
                if (StepStatus.OPEN.equals(s.getBearbeitungsstatusEnum()) && s.isTypAutomatisch()) {
                    ScriptThreadWithoutHibernate myThread = new ScriptThreadWithoutHibernate(s);
                    myThread.startOrPutToQueue();
                }
            }

        } catch (PreferencesException | TypeNotAllowedForParentException | MetadataTypeNotAllowedException | DAOException e) {
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
        Metadata id = new Metadata(prefs.getMetadataTypeByName(name));
        id.setValue(value);
        ds.addMetadata(id);
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
        XMLConfiguration config = ConfigPlugins.getPluginConfig(getJobName());
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
            col.setType(cc.getString("./type", "my type"));
            collections.add(col);
        }
        return collections;
    }

}
