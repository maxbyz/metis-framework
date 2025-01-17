package eu.europeana.metis.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CompressedFileHandler}
 */

public class CompressedFileHandlerGzTest {

    private final static String DESTINATION_DIR = String.format("src%1$stest%1$sresources%1$s__files%1$s", File.separator);
    private final static int XML_FILES_COUNT = 13;
    private final static String FILE_NAME = "gzFile";
    private final static String FILE_NAME2 = "gzFileWithCompressedGZFiles";
    private final static String FILE_NAME3 = "gzFilesWithMixedCompressedFiles";
    private final static String XML_TYPE = "xml";
    public static final String FILE_EXTENSION = ".tar.gz";
    
    @Test
    void shouldUnpackTheTarGzFilesRecursively() throws IOException {
        CompressedFileHandler.extractFile(Path.of(DESTINATION_DIR + FILE_NAME + FILE_EXTENSION), Path.of(DESTINATION_DIR));
        Collection<File> files = getXMLFiles(DESTINATION_DIR + FILE_NAME);
        assertNotNull(files);
        assertEquals(XML_FILES_COUNT, files.size());
    }

    @Test
    void shouldUnpackTheTarGzFilesRecursivelyWithCompressedXMLFiles() throws IOException {
        CompressedFileHandler.extractFile(Path.of(DESTINATION_DIR + FILE_NAME2 + FILE_EXTENSION), Path.of(DESTINATION_DIR));
        Collection<File> files = getXMLFiles(DESTINATION_DIR + FILE_NAME2);
        assertNotNull(files);
        assertEquals(XML_FILES_COUNT, files.size());
    }

    @Test
    void shouldUnpackTheTGZFilesRecursivelyWithCompressedXMLFiles() throws IOException {
        CompressedFileHandler.extractFile(Path.of(DESTINATION_DIR + FILE_NAME2 + FILE_EXTENSION), Path.of(DESTINATION_DIR));
        Collection<File> files = getXMLFiles(DESTINATION_DIR + FILE_NAME2);
        assertNotNull(files);
        assertEquals(XML_FILES_COUNT, files.size());
    }

    @Test
    void shouldUnpackTheTarGzFilesRecursivelyWithMixedNestedCompressedFiles() throws IOException {
        CompressedFileHandler.extractFile(Path.of(DESTINATION_DIR + FILE_NAME3 + FILE_EXTENSION), Path.of(DESTINATION_DIR));
        Collection<File> files = getXMLFiles(DESTINATION_DIR + FILE_NAME3);
        assertNotNull(files);
        assertEquals(XML_FILES_COUNT, files.size());
    }

    private Collection<File> getXMLFiles(String folderLocation) {
        return FileUtils.listFiles(new File(folderLocation), new String[]{XML_TYPE}, true);
    }

    @AfterAll
    public static void cleanUp() throws IOException {
        FileUtils.forceDelete(new File(DESTINATION_DIR + FILE_NAME));
        FileUtils.forceDelete(new File(DESTINATION_DIR + FILE_NAME2));
        FileUtils.forceDelete(new File(DESTINATION_DIR + FILE_NAME3));
    }
}
