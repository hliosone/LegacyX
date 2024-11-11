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

import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.Base64;
import java.util.Date;

@Service
public class GovernmentEntity {

    private xrplAccount governmentAccount;
    private RSAKey sdJwtKey;
    private String governmentDid;

    // Constructor : Initialize GovernmentEntity using keys from application.properties
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

            // get government xrpl account
            this.governmentAccount = new xrplAccount(govKeyPair);
            this.governmentDid = "did:xrpl:devnet:" + governmentAccount.getrAddress();

            System.out.println("GovernmentEntity successfully initialized.");
        } catch (Exception e) {
            throw new RuntimeException("Error while initializing GovernmentEntity : " + e.getMessage());
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
                throw new RuntimeException("Erreur : DID de la personne décédée invalide.");
            }

            //String deceasedAddress = deceasedDid.substring(16);
//            if (!LedgerUtility.isValidXrpAddress(deceasedAddress)) {
//                throw new RuntimeException("Error : invalid DID.");
//            }

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
            System.out.println("Death Certificate SD-JWT generated : " + vc);
            return vc;

        } catch (JOSEException e) {
            throw new RuntimeException("Error while creating death certificate : " + e.getMessage());
        }
    }


//    // Méthode de vérification et affichage des informations du certificat de décès
//    public boolean verifyAndDisplayDeathCertificate(String sdJwtVc, String expectedDeceasedDid, RSAKey publicKey) {
//        try {
//            // Parse le SD-JWT (VC encodé en JWT)
//            SignedJWT signedJWT = SignedJWT.parse(sdJwtVc);
//
//            // Vérifie que l'émetteur est bien le DID du gouvernement
//            String issuer = signedJWT.getJWTClaimsSet().getIssuer();
//            if (!issuer.equals(governmentDid)) {
//                System.out.println("Erreur : L'émetteur du certificat n'est pas le gouvernement.");
//                return false;
//            } else {
//                System.out.println("L'émetteur du certificat est confirmé comme étant le gouvernement.");
//            }
//
//            // Vérifie que le sujet (personne décédée) correspond au DID attendu
//            String subject = signedJWT.getJWTClaimsSet().getSubject();
//            if (!subject.equals(expectedDeceasedDid)) {
//                System.out.println("Erreur : Le certificat ne correspond pas au DID de la personne décédée spécifiée.");
//                System.out.println("DID du certificat : " + subject);
//                System.out.println("DID attendu : " + expectedDeceasedDid);
//                return false;
//            } else {
//                System.out.println("DID du sujet confirmé : " + subject);
//            }
//
//            // Vérifie la signature avec la clé publique du gouvernement
//            JWSVerifier verifier = new RSASSAVerifier(publicKey);
//            boolean isVerified = signedJWT.verify(verifier);
//
//            if (isVerified) {
//                System.out.println("La signature est valide. Certificat émis par le gouvernement pour le DID de la personne décédée spécifiée.");
//            } else {
//                System.out.println("Échec de la vérification de la signature. Certificat non valide.");
//            }
//
//            decodeJWT(sdJwtVc);
//
//            return isVerified;
//
//        } catch (ParseException | JOSEException e) {
//            throw new RuntimeException("Erreur lors de la vérification du certificat de décès : " + e.getMessage());
//        }
//    }

    // Méthode de vérification et affichage des informations du certificat de décès, retourne une chaîne formatée
    public String verifyAndFormatDeathCertificate(String sdJwtVc, String expectedDeceasedDid, RSAKey publicKey) {
        StringBuilder result = new StringBuilder();
        try {
            // Parse le SD-JWT (VC encodé en JWT)
            SignedJWT signedJWT = SignedJWT.parse(sdJwtVc);

            // Vérifie que l'émetteur est bien le DID du gouvernement
            String issuer = signedJWT.getJWTClaimsSet().getIssuer();
            if (!issuer.equals(governmentDid)) {
                result.append("Erreur : L'émetteur du certificat n'est pas le gouvernement.\n");
                return null;
                //return result.toString();
            } else {
                result.append("L'émetteur du certificat est confirmé comme étant le gouvernement.\n\n");
            }

            System.out.println(result.toString());

            // Vérifie que le sujet (personne décédée) correspond au DID attendu
            String subject = signedJWT.getJWTClaimsSet().getSubject();
            if (!subject.equals(expectedDeceasedDid)) {
                result.append("Erreur : Le certificat ne correspond pas au DID de la personne décédée spécifiée.\n");
                result.append("DID du certificat : ").append(subject).append("\n");
                result.append("DID attendu : ").append(expectedDeceasedDid).append("\n");
                return null;
                //return result.toString();
            } else {
                result.append("DID du sujet confirmé : ").append(subject).append("\n\n");
            }

            // Vérifie la signature avec la clé publique du gouvernement
            JWSVerifier verifier = new RSASSAVerifier(publicKey);
            boolean isVerified = signedJWT.verify(verifier);

            if (isVerified) {
                result.append("La signature est valide. Certificat émis par le gouvernement pour le DID de la personne décédée spécifiée.\n\n");
            } else {
                result.append("Échec de la vérification de la signature. Certificat non valide.\n");
                return null;
                //return result.toString();
            }

            // Ajoute les informations décodées du JWT
            result.append(decodeJWT(sdJwtVc));

            System.out.println(result.toString());

            return result.toString();

        } catch (ParseException | JOSEException e) {
            return null;
            //return "Erreur lors de la vérification du certificat de décès : " + e.getMessage();
        }
    }

    // Méthode pour décoder le JWT et formater le contenu en une chaîne de caractères lisible
    public static String decodeJWT(String sdJwtVc) {
        StringBuilder decodedInfo = new StringBuilder();
        try {
            // Parser le SD-JWT
            SignedJWT signedJWT = SignedJWT.parse(sdJwtVc);

            // Décoder et afficher l'en-tête JSON
            String headerJson = signedJWT.getHeader().toJSONObject().toString();
            decodedInfo.append("En-tête JSON : ").append(headerJson).append("\n");

            // Décoder et afficher le payload JSON
            String payloadJson = signedJWT.getPayload().toJSONObject().toString();
            decodedInfo.append("Payload JSON : ").append(payloadJson).append("\n");

        } catch (ParseException e) {
            decodedInfo.append("Erreur lors du parsing du SD-JWT : ").append(e.getMessage()).append("\n");
        }

        return decodedInfo.toString();
    }

    // Méthode pour obtenir la clé publique (à des fins de vérification)
    public RSAKey getPublicKey() {
        return this.sdJwtKey.toPublicJWK();
    }

    // Méthode pour obtenir la clé privée (pour signer les VCs si nécessaire)
    // Attention : cette méthode ne devrait pas être accessible en dehors des fonctions de signature
    private RSAKey getPrivateKey() {
        return this.sdJwtKey;
    }
}

