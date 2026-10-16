package ru.nsu.dizmestev;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.util.Date;

public class Issuer {
    private final PrivateKey caKey;
    private final X500Name issuerName;
    private final SecureRandom random = new SecureRandom();

    public Issuer(Path keyFile, String issuer) throws IOException {
        try (PEMParser parser = new PEMParser(Files.newBufferedReader(keyFile))) {
            Object pem = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            if (pem instanceof PEMKeyPair pair) {
                caKey = converter.getKeyPair(pair).getPrivate();
            } else {
                caKey = converter.getPrivateKey((PrivateKeyInfo) pem);
            }
        }
        issuerName = new X500Name(issuer);
    }

    public byte[] issue(String name) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(8192);
        KeyPair pair = generator.generateKeyPair();

        Date from = new Date();
        Date to = new Date(from.getTime() + 365L * 24 * 60 * 60 * 1000);
        X500Name subject = new X500NameBuilder().addRDN(BCStyle.CN, name).build();
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuerName, new BigInteger(64, random), from, to, subject, pair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(caKey);
        X509CertificateHolder certificate = builder.build(signer);

        byte[] key = toPem(pair.getPrivate());
        byte[] crt = toPem(certificate);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(key.length);
        out.write(key);
        out.writeInt(crt.length);
        out.write(crt);
        return bytes.toByteArray();
    }

    private static byte[] toPem(Object object) throws IOException {
        StringWriter text = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(text)) {
            writer.writeObject(object);
        }
        return text.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
