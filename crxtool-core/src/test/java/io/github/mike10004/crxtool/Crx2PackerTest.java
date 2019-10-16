package io.github.mike10004.crxtool;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class Crx2PackerTest extends CrxPackerTestBase {

    @Override
    protected CrxVersion getCrxVersion() {
        return CrxVersion.CRX2;
    }

    @Override
    protected CrxPacker createCrxPacker() {
        return new Crx2Packer();
    }

    @Test
    public void writeExtensionHeader() throws Exception {
        Random random = new Random(getClass().getName().hashCode());
        byte[] publicKeyBytes = new byte[1024];
        random.nextBytes(publicKeyBytes);
        byte[] signature = new byte[2048];
        random.nextBytes(signature);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
        new Crx2Packer().writeExtensionHeader(publicKeyBytes, signature, baos);
        baos.flush();
        byte[] headerBytes = baos.toByteArray();
        CrxMetadata metadata = CrxParser.getDefault().parseMetadata(new ByteArrayInputStream(headerBytes));
        AsymmetricKeyProof proof = metadata.getFileHeader().getAsymmetricKeyProofs(Crx2ProofAlgorithm.sha1_with_rsa).get(0);
        assertEquals("pubkey length", publicKeyBytes.length, proof.getPublicKeyLength());
        assertEquals("sig length", signature.length, proof.getSignatureLength());
    }

    @Override
    protected CrxTestCase loadPackExtensionTestCase() throws InvalidKeySpecException, NoSuchAlgorithmException, URISyntaxException, IOException {
        KeyPair keyPair = KeyPairs.loadRsaKeyPairFromPrivateKeyBytes(TestingKey.getInstance().loadTestingKeyPrivateKeyBytes());
        File referenceCrxFile = new File(Tests.getMakePageRedCrxResource(CrxVersion.CRX2).toURI());
        return new CrxTestCase(referenceCrxFile, keyPair, true);
    }

}
