package com.example.legacyx.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.example.legacyx.model.xrplAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xrpl.xrpl4j.crypto.keys.KeyPair;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import java.text.ParseException;
import java.util.Base64;
import java.util.Date;

@Service
public class GovernmentEntity {

    private xrplAccount governmentAccount;
    private RSAKey sdJwtKey;
    private String governmentDid;

    // Constructor: Initialize GovernmentEntity using keys from application.properties
    public GovernmentEntity(
            @Value("${GOVERNMENT_XRPL_PK}") String importedKeyPair, // Government XRPL private key
            @Value("${GOVERNMENT_SD_JWT_PRIVATE_KEY}") String privateKeyString, // SD-JWT private key
            @Value("${GOVERNMENT_SD_JWT_PUBLIC_KEY}") String publicKeyString // SD-JWT public key
    ) {
        try {
            // Convert the base64 encoded private and public keys to RSAKey objects
            PrivateKey privateKey = getPrivateKeyFromBase64(privateKeyString);
            PublicKey publicKey = getPublicKeyFromBase64(publicKeyString);

            // Key configuration for SD-JWT
            this.sdJwtKey = new RSAKey.Builder((RSAPublicKey) publicKey)
                    .privateKey(privateKey)
                    .keyID("gov-key")
                    .build();

            org.xrpl.xrpl4j.crypto.keys.Seed seed = org.xrpl.xrpl4j.crypto.keys.Seed.fromBase58EncodedSecret
                    ((org.xrpl.xrpl4j.crypto.keys.Base58EncodedSecret.of(importedKeyPair)));
            KeyPair govKeyPair = seed.deriveKeyPair();

            // get government XRPL account
            this.governmentAccount = new xrplAccount(govKeyPair);
            this.governmentDid = "did:xrpl:devnet:" + governmentAccount.getrAddress();

            System.out.println("GovernmentEntity successfully initialized.");
        } catch (Exception e) {
            throw new RuntimeException("Error while initializing GovernmentEntity: " + e.getMessage());
        }
    }

    private PrivateKey getPrivateKeyFromBase64(String base64PrivateKey) throws Exception {
        byte[] decodedKey = Base64.getDecoder().decode(base64PrivateKey);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    private PublicKey getPublicKeyFromBase64(String base64PublicKey) throws Exception {
        byte[] decodedKey = Base64.getDecoder().decode(base64PublicKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }

    public String createDeathCertificateVC(String deceasedDid) {
        try {

            if (!deceasedDid.startsWith("did:xrpl:devnet:")) {
                throw new RuntimeException("Error: Invalid deceased person's DID.");
            }

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .issuer(governmentDid)               // Issuer (Government DID)
                    .subject(deceasedDid)                // Deceased person's DID
                    .issueTime(new Date())               // Issuance date
                    .claim("type", "DeathCertificate")   // VC type
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(sdJwtKey.getKeyID())
                    .build();

            // Create the JWT
            SignedJWT signedJWT = new SignedJWT(header, claimsSet);

            // Sign the JWT
            RSASSASigner signer = new RSASSASigner(sdJwtKey.toPrivateKey());
            signedJWT.sign(signer);

            // Return the serialized JWT
            String vc = signedJWT.serialize();
            System.out.println("Death Certificate SD-JWT generated: " + vc);
            return vc;

        } catch (JOSEException e) {
            throw new RuntimeException("Error while creating death certificate: " + e.getMessage());
        }
    }

    // Method to verify and display death certificate information, returns a formatted string
    public String verifyAndFormatDeathCertificate(String sdJwtVc, String expectedDeceasedDid, RSAKey publicKey) {
        StringBuilder result = new StringBuilder();
        try {
            // Parse the SD-JWT (VC encoded in JWT)
            SignedJWT signedJWT = SignedJWT.parse(sdJwtVc);

            // Verify that the issuer is indeed the government's DID
            String issuer = signedJWT.getJWTClaimsSet().getIssuer();
            if (!issuer.equals(governmentDid)) {
                result.append("Error: The certificate issuer is not the government.\n");
                return null;
            } else {
                result.append("The certificate issuer is confirmed to be the government.\n\n");
            }

            System.out.println(result.toString());

            // Verify that the subject (deceased person) matches the expected DID
            String subject = signedJWT.getJWTClaimsSet().getSubject();
            if (!subject.equals(expectedDeceasedDid)) {
                result.append("Error: The certificate does not match the specified deceased person's DID.\n");
                result.append("Certificate DID: ").append(subject).append("\n");
                result.append("Expected DID: ").append(expectedDeceasedDid).append("\n");
                return null;
            } else {
                result.append("Subject DID confirmed: ").append(subject).append("\n\n");
            }

            // Verify the signature with the government's public key
            JWSVerifier verifier = new RSASSAVerifier(publicKey);
            boolean isVerified = signedJWT.verify(verifier);

            if (isVerified) {
                result.append("The signature is valid. Certificate issued by the government for the specified deceased person's DID.\n\n");
            } else {
                result.append("Signature verification failed. Invalid certificate.\n");
                return null;
            }

            // Add decoded information from the JWT
            result.append(decodeJWT(sdJwtVc));

            System.out.println(result.toString());

            return result.toString();

        } catch (ParseException | JOSEException e) {
            return null;
        }
    }

    // Method to decode the JWT and format the content into a readable string
    public static String decodeJWT(String sdJwtVc) {
        StringBuilder decodedInfo = new StringBuilder();
        try {
            // Parse the SD-JWT
            SignedJWT signedJWT = SignedJWT.parse(sdJwtVc);

            // Decode and display the JSON header
            String headerJson = signedJWT.getHeader().toJSONObject().toString();
            decodedInfo.append("JSON Header: ").append(headerJson).append("\n");

            // Decode and display the JSON payload
            String payloadJson = signedJWT.getPayload().toJSONObject().toString();
            decodedInfo.append("JSON Payload: ").append(payloadJson).append("\n");

        } catch (ParseException e) {
            decodedInfo.append("Error parsing SD-JWT: ").append(e.getMessage()).append("\n");
        }

        return decodedInfo.toString();
    }

    // Method to get the public key (for verification purposes)
    public RSAKey getPublicKey() {
        return this.sdJwtKey.toPublicJWK();
    }

    // Method to get the private key (for signing VCs if needed)
    private RSAKey getPrivateKey() {
        return this.sdJwtKey;
    }
}
