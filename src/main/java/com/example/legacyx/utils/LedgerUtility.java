package com.example.legacyx.utils;

import io.github.novacrypto.base58.Base58;
import com.example.legacyx.model.xrplAccount;
import org.bouncycastle.util.encoders.Hex;
import org.xrpl.xrpl4j.model.transactions.Memo;
import org.xrpl.xrpl4j.model.transactions.MemoWrapper;

public class LedgerUtility {

    public static boolean isValidXrpAddress(String address) {
        if (!xrplAccount.getAddressPattern().matcher(address).matches()) {
            return false;
        }
        try {
            byte[] decoded = Base58.base58Decode(address);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String convertAddressToHex(String address) {
        StringBuilder hexString = new StringBuilder();
        for (char c : address.toCharArray()) {
            hexString.append(Integer.toHexString(c));
        }
        return hexString.toString();
    }

    public static String convertHexToString(String hex) {
        byte[] bytes = Hex.decode(hex);
        return new String(bytes);
    }

    public static String convertStringToHex(String str) {
        byte[] bytes = str.getBytes();
        return Hex.toHexString(bytes);
    }

    // create memo
    public static MemoWrapper createInheritancePaymentMemo(String memoData) {
        //memo data should be converted to hex and type also

        Memo memo = Memo.builder()
                .memoData(convertStringToHex(memoData)) //
                .memoType("4c6567616379582d4d756c7469") // Legacy-Multi in hexadécimal
                .memoFormat("6170706C6963") // applic in hexadécimal
                .build();

        return MemoWrapper.builder()
                .memo(memo)
                .build();
    }

    public static String extractAddressFromDID(String did) {
        // Extract the address from the DID
        if (!did.startsWith("did:xrpl:devnet:")) {
            return null;
        }
        return did.substring(16);
    }

}
