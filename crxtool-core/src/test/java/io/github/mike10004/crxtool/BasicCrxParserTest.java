package io.github.mike10004.crxtool;

import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import io.github.mike10004.crxtool.testing.Unzippage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static com.google.common.base.Preconditions.checkArgument;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BasicCrxParserTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Random random = new Random(getClass().getName().hashCode());

    @Test
    public void parseMetadata_crx2() throws Exception {
        CrxMetadata metadata = test_parseMetadata(CrxVersion.CRX2, TestingKey.getInstance().getIdPmdecimal());
        List<AsymmetricKeyProofContainer> keyProofContainers = metadata.getFileHeader().getAllAsymmetricKeyProofs();
        assertEquals("num proofs", 1, keyProofContainers.size());
        AsymmetricKeyProofContainer container = keyProofContainers.get(0);
        System.out.format("algorithm = %s%n", container.algorithm());
    }

    @Test
    public void parseMetadata_crx3() throws Exception {
        test_parseMetadata(CrxVersion.CRX3, TestingKey.getInstance().getIdPmdecimal());
    }

    private CrxMetadata test_parseMetadata(CrxVersion version, String expectedExtensionId) throws Exception {
        ParsedCrx parsedCrx = parse(Resources.asByteSource(Tests.getMakePageRedCrxResource(version)));
        CrxMetadata metadata = parsedCrx.metadata;
        assertEquals("version", metadata.getCrxVersion(), version);
        Unzippage unzippage = parsedCrx.unzippage;
        System.out.format("headerLength=%s%nid=%s%n%n", metadata.getFileHeader().numBytes(), metadata.getId());
        assertEquals("id", expectedExtensionId, metadata.getId());
        unzippage.allEntries().forEach(entry -> {
            System.out.format("%s%n", entry);
        });
        for (String filename : Arrays.asList("background.js", "manifest.json")) {
            ByteSource entryBytes = unzippage.getFileBytes(filename);
            assertNotNull("entry bytes present expected", entryBytes);
            URL reference = getClass().getResource("/make_page_red/" + filename);
            byte[] actualBytes = entryBytes.read();
            byte[] expectedBytes = Resources.toByteArray(reference);
            assertEqualsAsTextFiles("file bytes", expectedBytes, actualBytes, StandardCharsets.UTF_8);
        }
        return metadata;
    }

    private static class ParsedCrx {
        public final CrxMetadata metadata;
        public final Unzippage unzippage;

        private ParsedCrx(CrxMetadata metadata, Unzippage unzippage) {
            this.metadata = metadata;
            this.unzippage = unzippage;
        }
    }

    private ParsedCrx parse(ByteSource crxSource) throws IOException {
        BasicCrxParser parser = new BasicCrxParser();
        CrxMetadata metadata;
        Unzippage unzippage;
        try (InputStream in = crxSource.openStream()) {
            metadata = parser.parseMetadata(in);
            unzippage = Unzippage.unzip(in);
        }
        return new ParsedCrx(metadata, unzippage);
    }

    /*
     * Git changes the line endings of text files, but the crx has the original unix line endings, so without this
     * extra attention, comparing a crx newly created on Windows to the original one created on Linux will fail.
     * This method compares the files line-by-line, using {@link CharSource#readLines()} to do the normalization.
     */
    @SuppressWarnings("SameParameterValue")
    private static void assertEqualsAsTextFiles(String message, byte[] expected, byte[] actual, Charset charset) {
        String expectedStr = null, actualStr = null;
        try {
            expectedStr = charset.newDecoder().decode(ByteBuffer.wrap(expected)).toString();
        } catch (CharacterCodingException e) {
            System.err.println("failed to decode expected due to " + e);
        }
        try {
            actualStr = charset.newDecoder().decode(ByteBuffer.wrap(actual)).toString();
        } catch (CharacterCodingException e) {
            System.err.println("failed to decode expected due to " + e);
        }
        if (expectedStr == null || actualStr == null) {
            assertArrayEquals(message, expected, actual);
        } else {
            try {
                List<String> expectedLines = CharSource.wrap(expectedStr).readLines();
                List<String> actualLines = CharSource.wrap(expectedStr).readLines();
                assertEquals(message, expectedLines, actualLines);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test(expected = CrxParsingException.class)
    public void parseMetadata_randomBytes() throws Exception {
        byte[] bytes = new byte[10 * 1024];
        random.nextBytes(bytes);
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            new BasicCrxParser().parseMetadata(in);
        }
    }

    @Test(expected = CrxParsingException.class)
    public void parseMetadata_zipFile() throws Exception {
        File zipFile = createRandomZipFile();
        try (InputStream in = new FileInputStream(zipFile)) {
            new BasicCrxParser().parseMetadata(in);
        }
    }

    private static final String MAGIC_NUMBER_ASCII = "Cr24";

    @Test(expected = EOFException.class)
    public void parseMetadata_prematureEof() throws Exception {
        byte[] bytes = MAGIC_NUMBER_ASCII.getBytes(StandardCharsets.US_ASCII);
        new BasicCrxParser().parseMetadata(new ByteArrayInputStream(bytes));
    }

    @Test(expected = CrxParsingException.class)
    public void parseMetadata_blankAfterMagicNumber() throws Exception {
        byte[] magic = MAGIC_NUMBER_ASCII.getBytes(StandardCharsets.US_ASCII);
        byte[] bytes = new byte[10 * 1024];
        System.arraycopy(magic, 0, bytes, 0, magic.length);
        CrxMetadata md = new BasicCrxParser().parseMetadata(new ByteArrayInputStream(bytes));
        System.out.println(md);
    }

    private File createRandomZipFile() throws IOException {
        File root = temporaryFolder.newFolder();
        File file1 = new File(root, "file1.dat");
        byte[] bytes1 = new byte[1024];
        File file2 = new File(root, "file2.dat");
        byte[] bytes2 = new byte[10 * 1024];

        random.nextBytes(bytes1);
        random.nextBytes(bytes2);
        Files.write(file1.toPath(), bytes1);
        Files.write(file2.toPath(), bytes2);
        byte[] zipData = Zipping.zipDirectory(root.toPath(), null);
        File zipFile = temporaryFolder.newFile();
        Files.write(zipFile.toPath(), zipData);
        return zipFile;
    }

    @Test
    public void parseCrx3() throws Exception {
        testParseCrx("crx3", MAGIC_NUMBER_ASCII);
    }

    @Test
    public void parseCrx2() throws Exception {
        testParseCrx("crx2", MAGIC_NUMBER_ASCII);
    }

    @SuppressWarnings("SameParameterValue")
    private void testParseCrx(String filenameExtension, String expectedMagicNumber) throws IOException {
        String resourcePath = "/page-timer-1.7.0.0." + filenameExtension;
        URL resource = getClass().getResource(resourcePath);
        checkArgument(resource != null, "not found: classpath:%s", resourcePath);
        String magicNumber;
        try (InputStream crxInput = resource.openStream()) {
            magicNumber = new BasicCrxParser().readMagicNumber(crxInput, EmptyParsingState.getInstance());
        }
        System.out.format("magic number %s from %s%n", magicNumber, resource);
        assertEquals("expected magic number", expectedMagicNumber, magicNumber);
        ParsedCrx parsed = parse(Resources.asByteSource(resource));
        System.out.format("id %s from %s%n", parsed.metadata.getId(), parsed.metadata);
        assertNotNull(parsed.metadata); // we mostly just care that this doesn't throw an exception
        assertEquals("expected magic number", expectedMagicNumber, parsed.metadata.getMagicNumber());
    }
}