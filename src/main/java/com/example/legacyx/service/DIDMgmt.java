package com.example.legacyx.service;
import org.apache.commons.codec.binary.Hex;

import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.primitives.UnsignedInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.xrpl.xrpl4j.client.JsonRpcClientErrorException;
import org.xrpl.xrpl4j.crypto.signing.bc.BcSignatureService;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoResult;
import org.xrpl.xrpl4j.model.transactions.Address;

import com.fasterxml.jackson.databind.ObjectMapper;


import java.util.*;

@Service
public class DIDMgmt {

    private static BcSignatureService signatureService = new BcSignatureService();
    private final ClientService client;
    private final ObjectMapper objectMapper;

    @Autowired
    public DIDMgmt(ClientService clientService) {
        this.client = clientService;
        this.objectMapper = new ObjectMapper(); // Ajoute le support pour Optional
    }

    public Map<String, Object> createDidDocumentInheritor(String vc, String inheritor) throws JsonProcessingException,
            JsonRpcClientErrorException {

        Address inheritorAddress = Address.of(inheritor);
        AccountInfoResult accountInfoResult = client.getAccountInfos(inheritorAddress);
        assert accountInfoResult != null;
        UnsignedInteger sequence = accountInfoResult.accountData().sequence();

        // Convert VC to hex
        String memoData = Hex.encodeHexString(vc.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> memoMap = new HashMap<>();
        memoMap.put("MemoType", Hex.encodeHexString("inheritance/death-certificate".getBytes(StandardCharsets.UTF_8)));
        memoMap.put("MemoFormat", Hex.encodeHexString("text/plain".getBytes(StandardCharsets.UTF_8)));
        memoMap.put("MemoData", memoData);

        Map<String, Object> txjson = new HashMap<>();
        txjson.put("TransactionType", "DIDSet");
        txjson.put("Account", inheritor);
        txjson.put("Fee", "10");
        txjson.put("Sequence", sequence);
        txjson.put("Data", "4C656761637958"); // LegacyX in hexadécimal
        txjson.put("DIDDocument", "474F"); // DIDDocument  in hexadécimal
        txjson.put("Memos", List.of(Map.of("Memo", memoMap))); // Add memo

        return Map.of("txjson", txjson);
    }

}
