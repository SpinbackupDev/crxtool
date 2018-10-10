package io.github.mike10004.crxtool;

import com.google.common.hash.HashCode;
import com.google.common.io.ByteStreams;
import com.google.common.io.LittleEndianDataInputStream;
import com.google.common.primitives.Ints;
import com.google.common.primitives.UnsignedInteger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

class Crx2Interpreter extends CrxInterpreterBase {

    protected static final int MAX_SANE_PUBKEY_LENGTH = 1024 * 32;
    protected static final int MAX_SANE_SIGNATURE_LENGTH = 1024 * 64;

    Crx2Interpreter(String magicNumber, int version) {
        super(magicNumber, version);
    }

    @Override
    public CrxMetadata parseMetadataAfterVersion(InputStream crxInput) throws IOException {
        LittleEndianDataInputStream in = new LittleEndianDataInputStream(crxInput);
        int pubkeyLength = Ints.checkedCast(UnsignedInteger.fromIntBits(in.readInt()).longValue());
        int signatureLength = Ints.checkedCast(UnsignedInteger.fromIntBits(in.readInt()).longValue());
        if (pubkeyLength <= 0 || pubkeyLength > MAX_SANE_PUBKEY_LENGTH) {
            throw new CrxParser.CrxParsingException(String.format("public key length is insane: %s", pubkeyLength));
        }
        if (signatureLength <= 0 || signatureLength > MAX_SANE_SIGNATURE_LENGTH) {
            throw new CrxParser.CrxParsingException(String.format("signature length is insane: %s", signatureLength));
        }
        byte[] pubkeyBytes = new byte[pubkeyLength];
        ByteStreams.readFully(crxInput, pubkeyBytes);
        byte[] signatureBytes = new byte[signatureLength];
        ByteStreams.readFully(crxInput, signatureBytes);
        String pubkeyBase64 = BASE_64.encode(pubkeyBytes);
        String signatureBase64 = BASE_64.encode(signatureBytes);
        HashCode pubkeyHash = SHA256.hashBytes(pubkeyBytes);
        String digest = pubkeyHash.toString().toLowerCase(Locale.ROOT);
        StringBuilder idBuilder = new StringBuilder(ID_LEN);
        translateDigestToId(digest, 0, ID_LEN, idBuilder);
        String id = idBuilder.toString();
        return new CrxMetadata(magicNumber, version, pubkeyLength, pubkeyBase64, signatureLength, signatureBase64, id);
    }

}
