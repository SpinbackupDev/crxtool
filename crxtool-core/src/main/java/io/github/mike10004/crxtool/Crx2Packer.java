package io.github.mike10004.crxtool;

import com.google.common.io.ByteSource;
import com.google.common.io.LittleEndianDataOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Implementation that packs a Chrome extension in CRX2 format.
 */
public class Crx2Packer implements CrxPacker {

    private static final Crx2Packer DEFAULT_INSTANCE = new Crx2Packer();

    private static final String MAGIC_NUMBER = "Cr24";
    static final CrxVersion CRX_VERSION = CrxVersion.CRX2;
    private static final int MAX_SANE_PUBLIC_KEY_LENGTH = 1024 * 32;
    private static final int MAX_SANE_SIGNATURE_LENGTH = 1024 * 128;

    /**
     * Constructs a new instance.
     */
    public Crx2Packer() {
    }

    static CrxPacker getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    @Override
    public void packExtension(ByteSource zipBytes, KeyPair keyPair, OutputStream output) throws IOException, InvalidKeyException, NoSuchAlgorithmException, SignatureException {
        writeExtensionHeader(zipBytes, keyPair, output);
        zipBytes.copyTo(output);
    }

    protected void writeExtensionHeader(ByteSource zipBytes, KeyPair keyPair, OutputStream output) throws IOException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
        byte[] signature = sign(zipBytes, keyPair);
        writeExtensionHeader(publicKeyBytes, signature, output);
    }

    protected void writeExtensionHeader(byte[] publicKeyBytes, byte[] signature, OutputStream output) throws IOException {
        checkArgument(publicKeyBytes.length <= MAX_SANE_PUBLIC_KEY_LENGTH, "public key length is insane: %s", publicKeyBytes.length);
        checkArgument(signature.length <= MAX_SANE_SIGNATURE_LENGTH, "signature length is insane: %s", signature.length);
        LittleEndianDataOutputStream leOutput = new LittleEndianDataOutputStream(output);
        writeMagicNumber(leOutput);
        writeFormatVersion(leOutput);
        leOutput.writeInt(publicKeyBytes.length);
        leOutput.writeInt(signature.length);
        leOutput.flush();
        output.write(publicKeyBytes);
        output.write(signature);
    }

    protected void writeMagicNumber(LittleEndianDataOutputStream leOutput) throws IOException {
        leOutput.write(MAGIC_NUMBER.getBytes(StandardCharsets.US_ASCII));
    }

    protected void writeFormatVersion(LittleEndianDataOutputStream leOutput) throws IOException {
        leOutput.writeInt(CRX_VERSION.identifier());
    }

    protected byte[] sign(ByteSource zipBytes, KeyPair keyPair) throws IOException, SignatureException, InvalidKeyException, NoSuchAlgorithmException {
        Signature sig = Signature.getInstance("SHA1WithRSA");
        sig.initSign(keyPair.getPrivate());
        sig.update(zipBytes.read());
        byte[] signatureBytes = sig.sign();
        return signatureBytes;
    }

    @Override
    public CrxVersion getCrxVersion() {
        return CRX_VERSION;
    }
}

