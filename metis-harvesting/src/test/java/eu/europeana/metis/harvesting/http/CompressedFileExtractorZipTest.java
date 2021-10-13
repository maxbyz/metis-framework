package eu.europeana.metis.harvesting.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class CompressedFileExtractorZipTest {

  private final static String DESTINATION_DIR = 
          "src"+File.separator+"test"+File.separator+"resources"+File.separator+"__files"+File.separator;
  private final static int XML_FILES_COUNT = 13;
  private final static String FILE_NAME = "zipFileWithNestedZipFiles";
  private final static String FILE_NAME2 = "zipFileWithNestedFolders";
  private final static String FILE_NAME3 = "ZipFilesWithMixedCompressedFiles";
  private final static String DEFAULT_DESTINATION_NAME = "zipFile";
  private final static String XML_TYPE = "xml";
  public static final String ZIP_EXTENSION = ".zip";

  @Test
  public void shouldUnpackTheZipFilesRecursively() throws IOException {
    CompressedFileExtractor.extractFile(Path.of(DESTINATION_DIR + FILE_NAME + ZIP_EXTENSION),
            Path.of(DESTINATION_DIR));
    Collection<File> files = getXMLFiles(DESTINATION_DIR + DEFAULT_DESTINATION_NAME);
    assertNotNull(files);
    assertEquals(XML_FILES_COUNT, files.size());
  }

  @Test
  public void shouldUnpackTheZipFilesWithNestedFoldersRecursively() throws IOException {
    CompressedFileExtractor.extractFile(Path.of(DESTINATION_DIR + FILE_NAME2 + ZIP_EXTENSION),
            Path.of(DESTINATION_DIR));
    Collection<File> files = getXMLFiles(DESTINATION_DIR + DEFAULT_DESTINATION_NAME);
    assertNotNull(files);
    assertEquals(XML_FILES_COUNT, files.size());
  }

  @Test
  public void shouldUnpackTheZipFilesWithNestedMixedCompressedFiles() throws IOException {
    CompressedFileExtractor.extractFile(Path.of(DESTINATION_DIR + FILE_NAME3 + ZIP_EXTENSION),
            Path.of(DESTINATION_DIR));
    Collection<File> files = getXMLFiles(DESTINATION_DIR + DEFAULT_DESTINATION_NAME);
    assertNotNull(files);
    assertEquals(XML_FILES_COUNT, files.size());
  }

  private Collection<File> getXMLFiles(String folderLocation) {
    return FileUtils.listFiles(new File(folderLocation), new String[]{XML_TYPE}, true);
  }

  @AfterEach
  public void cleanUp() throws IOException {
    FileUtils.forceDelete(new File(DESTINATION_DIR + DEFAULT_DESTINATION_NAME));
  }
}