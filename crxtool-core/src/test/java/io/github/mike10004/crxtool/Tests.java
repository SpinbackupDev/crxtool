package io.github.mike10004.crxtool;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.primitives.Longs;
import io.github.mike10004.crxtool.testing.Unzippage;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class Tests {

    private Tests() {}

    /**
     * Chops the header off a CRX file and writes the zip bytes to a file.
     *
     * <p>We compare the packer's output with a reference CRX file generated by
     * Chrome on the command line with {@code --pack-extension}. In order for the
     * signatures to match, we need the exact zip byte that are produced by that method.
     * Whatever method it is, it's different from how Java's standard library zips things
     * and E. Gordon's Zip 3.0. There are many ways to zip a directory.
     * @return the zip file
     */
    public static File chopZipFromCrx(File referenceCrxFile) throws IOException {
        File choppedFile = File.createTempFile("chopped-extension-file", ".zip");
        try (InputStream in = new FileInputStream(referenceCrxFile)) {
            CrxParser.getDefault().parseMetadata(in);
            Files.asByteSink(choppedFile).writeFrom(in);
        }
//        System.out.format("%s (%d bytes)%n", referenceCrxFile, referenceCrxFile.length());
//        System.out.format("%s (%d bytes)%n", choppedFile, choppedFile.length());
//        System.out.format("header length %d bytes%n", referenceCrxFile.length() - choppedFile.length());
        return choppedFile;
    }

    public static File getAddFooterExtensionFile(CrxVersion version) {
        try {
            return new File(getAddFooterCrxResource(version).toURI());
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    public static URL getMakePageRedCrxResource(CrxVersion version) {
        return getCrxResource("make_page_red", version);
    }

    public static URL getAddFooterCrxResource(CrxVersion version) {
        return getCrxResource("add_footer", version);
    }

    public static URL getCrxResource(String name, CrxVersion version) {
        String resourcePath = String.format("/%s.%s", name, version.name().toLowerCase());
        return Objects.requireNonNull(Tests.class.getResource(resourcePath), () -> "not found: " + resourcePath);
    }

    public static File getMakePageRedCrxFile(CrxVersion version) {
        try {
            return new File(getMakePageRedCrxResource(version).toURI());
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    public static Path getAddFooterExtensionDir(CrxVersion version) {
        return getAddFooterManifestFile(version).getParentFile().toPath();
    }

    public static File getAddFooterManifestFile(CrxVersion version) {
        try {
            return new File(Tests.class.getResource("/add_footer/manifest.json").toURI());
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    public static KeyPair generateRsaKeyPair(long seed) throws NoSuchAlgorithmException {
        byte[] seedBytes = Longs.toByteArray(seed);
        SecureRandom random = new SecureRandom(seedBytes);
        return KeyPairs.generateRsaKeyPair(random);
    }

    public static class DirDiff {
        public final ImmutableMap<String, Optional<ByteSource>> referenceOnly;
        public final ImmutableMap<String, Optional<ByteSource>> queryOnly;
        public final List<java.lang.String> differents;

        public DirDiff(Map<String, Optional<ByteSource>> referenceOnly, Map<String, Optional<ByteSource>> queryOnly, List<java.lang.String> differents) {
            this.referenceOnly = ImmutableMap.copyOf(referenceOnly);
            this.queryOnly = ImmutableMap.copyOf(queryOnly);
            this.differents = ImmutableList.copyOf(differents);
        }

        public boolean isEmpty() {
            return referenceOnly.isEmpty() && queryOnly.isEmpty() && differents.isEmpty();
        }

        public void dump(PrintStream out) {
            referenceOnly.forEach((path, bs) -> out.format("only in reference: %s%n", path));
            queryOnly.forEach((path, bs) -> out.format("only in query: %s%n", path));
            differents.forEach(path -> out.format("different contents: %s%n", path));
            org.apache.commons.io.FilenameUtils.class.getName();
        }
    }

    private abstract static class DiffingVisitor implements FileVisitor<Path> {

        private final Path reference, query;

        protected DiffingVisitor(Path reference, Path query) {
            this.reference = reference;
            this.query = query;
        }


        @Override
        public final FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            visitDirectory(dir, resolveQuery(dir));
            return FileVisitResult.CONTINUE;
        }

        protected abstract void visitDirectory(Path referenceDir, Path queryDir) throws IOException;

        protected abstract void visitFile(Path referenceFile, Path queryFile) throws IOException;

        protected Path resolveQuery(Path referencePath) {
            Path relativePath = reference.relativize(referencePath);
            return query.resolve(relativePath);
        }

        @Override
        public final FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            visitFile(file, resolveQuery(file));
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            throw exc;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (exc != null) {
                throw exc;
            }
            return FileVisitResult.CONTINUE;
        }
    }

    private static String relativize(Path parent, Path child) {
        return parent.relativize(child).toString();
    }

    public static DirDiff diffDirectories(Path reference, Path query) throws IOException {
        Map<String, Optional<ByteSource>> referenceOnly = new HashMap<>(), queryOnly = new HashMap<>();
        List<String> differents = new ArrayList<>();
        java.nio.file.Files.walkFileTree(reference, new DiffingVisitor(reference, query) {

            @Override
            protected void visitDirectory(Path referenceDir, Path queryDir) {
                if (queryDir.toFile().exists()) {
                    if (!queryDir.toFile().isDirectory()) {
                        differents.add(relativize(reference, referenceDir));
                    }
                } else {
                    referenceOnly.put(relativize(reference, referenceDir), Optional.empty());
                }
            }

            @Override
            protected void visitFile(Path referenceFile, Path queryFile) throws IOException {
                if (queryFile.toFile().isDirectory()) {
                    differents.add(relativize(query, queryFile));
                    return;
                }
                if (!Files.asByteSource(referenceFile.toFile()).contentEquals(Files.asByteSource(queryFile.toFile()))) {
                    differents.add(relativize(reference, referenceFile));
                }
            }
        });
        // we've already checked whether any mutually existing files are different, so now
        // we just need to gather the names of files only in the query directory
        java.nio.file.Files.walkFileTree(query, new DiffingVisitor(query, reference) {
            @Override
            protected void visitDirectory(Path referenceDir, Path queryDir) {
                checkExists(relativize(query, referenceDir), queryDir.toFile());
            }

            protected void checkExists(String relativePath, File file) {
                if (!file.exists()) {
                    @Nullable ByteSource bs = null;
                    if (file.isFile()) {
                        bs = Files.asByteSource(file);
                    }
                    queryOnly.put(relativePath, Optional.ofNullable(bs));
                }
            }

            @Override
            protected void visitFile(Path referenceFile, Path queryFile) {
                checkExists(relativize(query, referenceFile), queryFile.toFile());
            }
        });
        return new DirDiff(referenceOnly, queryOnly, differents);
    }

    public static MultiversionCrx getMultiversionCrxExample() {
        return new MultiversionCrx();
    }

    public static class MultiversionCrx {

        private static final String ID = "bpfnehkjjffiihnbadbgpfpmedcpojjl";

        private MultiversionCrx() {}

        public String getId() {
            return ID;
        }

        public URL getResource(int version) throws IOException {
            String resourcePath = String.format("/%s/example.crx%d", getId(), version);
            URL resource = getClass().getResource(resourcePath);
            if (resource == null) {
                throw new FileNotFoundException("classpath:" + resourcePath);
            }
            return resource;
        }

    }

    public static void dumpCrxInfo(File crxFile, PrintStream out) throws IOException {
        out.format("crx file: %s%n", crxFile);
        dumpCrxInfo(Files.asByteSource(crxFile), out);
    }

    public static void dumpCrxInfo(ByteSource crxByteSource, PrintStream out) throws IOException {
        try (InputStream in = crxByteSource.openStream()) {
            CrxMetadata md = CrxParser.getDefault().parseMetadata(in);
            out.format("version:    %s%n", md.getCrxVersion().identifier());
            out.format("id:         %s%n", md.getId());
            out.format("magic:      %s%n", md.getMagicNumber());
            CrxFileHeader header = md.getFileHeader();
            out.format("header len: %s%n", header.numBytes());
            for (CrxProofAlgorithm proofAlgo : CrxProofAlgorithm.allKnown()) {
                List<AsymmetricKeyProof> proofs = header.getAsymmetricKeyProofs(proofAlgo);
                for (int i = 0; i < proofs.size(); i++) {
                    AsymmetricKeyProof proof = proofs.get(i);
                    out.format("proof: %s %s len=%s%n", proofAlgo, i, proof.getCombinedLength());
                }
            }
        }
    }

    public static void dumpZipInfo(ByteSource zipByteSource, PrintStream out) throws IOException {
        Unzippage unzippage;
        try (InputStream in = zipByteSource.openStream()) {
            unzippage = Unzippage.unzip(in);
        }
        List<String> zipEntries = Ordering.natural().immutableSortedCopy(unzippage.fileEntries());
        for (String zipEntry : zipEntries) {
            out.println(zipEntry);
        }
    }
}
