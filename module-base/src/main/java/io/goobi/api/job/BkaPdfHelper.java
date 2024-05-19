package io.goobi.api.job;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.mutable.MutableInt;
import org.goobi.beans.Process;
import org.jdom2.JDOMException;

import de.intranda.digiverso.files.naming.PdfFilenameNamer;
import de.intranda.digiverso.pdf.PDFConverter;
import de.intranda.digiverso.pdf.exception.PDFReadException;
import de.intranda.digiverso.pdf.exception.PDFWriteException;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.extern.log4j.Log4j2;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.UGHException;

@Log4j2
public class BkaPdfHelper {
    private Path tifFolder = null;
    private Path importFolder = null;
    private Path pdfFolder = null;
    private Path textFolder = null;
    private Path altoFolder = null;

    /**
     * Converts the PDF files
     * 
     * @param importFiles
     * @param process
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws SwapException
     * @throws DAOException
     * @throws PDFReadException
     * @throws PDFWriteException
     * @throws UGHException
     */
    public Fileformat convertData(Process process, String sourceFolder, List<File> importFiles, Fileformat origFileformat, Prefs prefs,
            VariableReplacer vr, boolean overwriteOldData)
            throws IOException, InterruptedException, SwapException, DAOException, PDFReadException, PDFWriteException, UGHException {

        PDFConverter.setFileNamingStrategy(new PdfFilenameNamer("%03d"));

        tifFolder = Path.of(process.getImagesOrigDirectory(false));
        importFolder = Path.of(process.getImportDirectory());
        pdfFolder = Path.of(process.getOcrPdfDirectory());
        textFolder = Path.of(process.getOcrTxtDirectory());
        altoFolder = Path.of(process.getOcrAltoDirectory());

        //        if (useS3) {
        //            tifFolder = Paths.get(tempFolder.toString(), tifFolder.getFileName().toString());
        //            importFolder = Paths.get(tempFolder.toString(), importFolder.getFileName().toString());
        //            pdfFolder = Paths.get(tempFolder.toString(), pdfFolder.getFileName().toString());
        //            textFolder = Paths.get(tempFolder.toString(), textFolder.getFileName().toString());
        //            altoFolder = Paths.get(tempFolder.toString(), altoFolder.getFileName().toString());
        //        }

        Files.createDirectories(tifFolder);
        Files.createDirectories(importFolder);
        Files.createDirectories(pdfFolder);
        Files.createDirectories(textFolder);
        Files.createDirectories(altoFolder);

        Fileformat ff = origFileformat;
        DocStruct topStruct = getTopStruct(ff);
        DocStruct boundBook = ff.getDigitalDocument().getPhysicalDocStruct();
        int numExistingPages = boundBook.getAllChildren() == null ? 0 : boundBook.getAllChildren().size();

        MutableInt counter = new MutableInt(numExistingPages + 1);
        for (File file : importFiles) {
            //            if (StringUtils.isNotBlank(pdfDocType) && shouldWriteMetsFile()) {
            //                DocStruct ds = addDocStruct(topStruct, ff, prefs, pdfDocType, file);
            //                ff = convertPdf(file, ff, prefs, ds, childDocType, counter);
            //            } else {
            convertPdf(file, ff, prefs, null, "BkaFile", counter);
            //            }
        }
        log.debug("A total of " + (counter.intValue() - 1) + " pages have so far been converted");
        return ff;

    }

    private DocStruct getTopStruct(Fileformat ff) throws PreferencesException {
        DocStruct top = ff.getDigitalDocument().getLogicalDocStruct();
        if (top.getType().isAnchor() && !top.getAllChildren().isEmpty()) {
            top = top.getAllChildren().get(0);
        }
        return top;
    }

    /**
     * @param sourceFolder
     * @param tifFolder
     * @param pdfFolder
     * @param textFolder
     * @param altoFolder
     * @param origFileformat
     * @param prefs
     * @return
     * @throws IOException
     * @throws PDFWriteException
     * @throws UGHException
     * @throws JDOMException
     */
    private void convertPdf(File importFile, Fileformat origFileformat, Prefs prefs, DocStruct parent, String childDocType, MutableInt counter)
            throws PDFReadException, PDFWriteException, IOException, UGHException {
        File importPdfFile = PDFConverter.decryptPdf(importFile, importFolder.toFile());
        if (importPdfFile == null || !importPdfFile.exists()) {
            importPdfFile = getImportPdfFile(importFile, false);
            if (!importPdfFile.equals(importFile)) {
                FileUtils.moveFile(importFile, importPdfFile);
            }
            log.debug("Copied original PDF file to " + importPdfFile);
        } else {
            log.debug("Created decrypted PDF file at " + importPdfFile);
        }

        List<File> imageFiles =
                PDFConverter.writeImages(importPdfFile, tifFolder.toFile(), counter.toInteger(), 300, "tif",
                        getTempFolder(), getImageGenerationMethod(), getImageGenerationParams());
        log.debug("Created " + imageFiles.size() + " TIFF files in " + tifFolder);

        List<File> altoFiles = PDFConverter.writeAltoFiles(importPdfFile, altoFolder.toFile(), imageFiles, false, counter.toInteger());
    }

    private File getImportPdfFile(File importFile, boolean createBackups) {
        File importPdfFile;
        importPdfFile = new File(importFolder.toFile(), importFile.getName());
        if (createBackups) {
            int index = 1;
            while (importPdfFile.exists()) {
                String baseName = FilenameUtils.getBaseName(importFile.getName());
                String extension = FilenameUtils.getExtension(importFile.getName());
                String filename = baseName + "_" + index + "." + extension;
                importPdfFile = new File(importFolder.toFile(), filename);
            }
        }
        return importPdfFile;
    }

    private File getTempFolder() throws IOException {
        String folderpath = ConfigurationHelper.getInstance().getTemporaryFolder();
        if (StringUtils.isNotBlank(folderpath)) {
            return new File(folderpath);
        } else {
            return Files.createTempDirectory("pdf_extraction_").toFile();
        }
    }

    private String getImageGenerationMethod() {
        //        return this.config.getString("imageGenerator", "ghostscript");
        return "ghostscript";
    }

    private String[] getImageGenerationParams() {
        //        return this.config.getStringArray("imageGeneratorParameter");
        String[] params = { "-cropbox" };
        return params;
    }

    public static void main(String[] args) {
        System.out.println("bla");
    }
}
