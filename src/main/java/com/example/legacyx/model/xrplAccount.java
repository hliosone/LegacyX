package com.example.legacyx.model;

import org.xrpl.xrpl4j.codec.addresses.AddressCodec;
import org.xrpl.xrpl4j.crypto.keys.KeyPair;
import org.xrpl.xrpl4j.crypto.keys.Seed;
import org.xrpl.xrpl4j.model.transactions.Address;
import org.xrpl.xrpl4j.model.transactions.XAddress;

import java.util.Base64;
import java.util.regex.Pattern;

public class xrplAccount {

    public xrplAccount() {
        System.out.println("Creating new account ...");
        this.randomKeyPair = Seed.ed25519Seed().deriveKeyPair();
        this.rAddress = randomKeyPair.publicKey().deriveAddress();
        this.xAddress = AddressCodec.getInstance().classicAddressToXAddress(this.rAddress, true);
        System.out.println("New account rAddress is : " + this.rAddress);
    }

    public xrplAccount(KeyPair importedKeyPair) {
        System.out.println("Importing account...");
        this.randomKeyPair = importedKeyPair;
        this.rAddress = randomKeyPair.publicKey().deriveAddress();
        this.xAddress = AddressCodec.getInstance().classicAddressToXAddress(this.rAddress, true);
        System.out.println("Account " + this.rAddress + " have been successfully imported !");
    }

    public Address getrAddress(){
        return this.rAddress;
    }

    public static xrplAccount importAccount(String privateKey) {
        try {
            org.xrpl.xrpl4j.crypto.keys.Seed seed = org.xrpl.xrpl4j.crypto.keys.Seed.fromBase58EncodedSecret
                    ((org.xrpl.xrpl4j.crypto.keys.Base58EncodedSecret.of(privateKey)));
            KeyPair keyPair = seed.deriveKeyPair();
            return new xrplAccount(keyPair);
        } catch (org.xrpl.xrpl4j.codec.addresses.exceptions.EncodingFormatException e) {
            System.err.println("Key format error : " + e.getMessage());
        }
        return null;
    }

    public XAddress getxAddress(){
        return this.xAddress;
    }

    public KeyPair getRandomKeyPair(){
        return this.randomKeyPair;
    }

    public static Pattern getAddressPattern(){
        return ADDRESS_PATTERN;
    }

    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^[rR][1-9A-HJ-NP-Za-km-z]{24,34}$");
    private final XAddress xAddress;
    private final Address rAddress;
    private final KeyPair randomKeyPair;
}
