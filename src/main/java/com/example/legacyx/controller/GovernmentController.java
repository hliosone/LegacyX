package com.example.legacyx.controller;

import com.example.legacyx.service.DIDMgmt;
import com.example.legacyx.service.GovernmentEntity;
import com.example.legacyx.utils.LedgerUtility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/government")
public class GovernmentController {

    private final GovernmentEntity governmentEntity;
    private final DIDMgmt didMgmt;

    @Autowired
    private CustomerController customerController;

    @Autowired
    public GovernmentController(GovernmentEntity governmentEntity, DIDMgmt didMgmt) {
        this.governmentEntity = governmentEntity;
        this.didMgmt = didMgmt;
    }

    @PostMapping("/createDeathCertificate")
    public String createDeathCertificate(@RequestBody Map<String, String> request) {
        String deceasedDid = request.get("deceasedDid");
        String userAddress = request.get("userAddress");
        try {
            System.out.println("Creating death certificate for " + deceasedDid + " for user " + userAddress);
            String vc = governmentEntity.createDeathCertificateVC(deceasedDid);
            System.out.println("Death certificate created : " + vc);
            return vc;
        } catch (Exception e) {
            return "Error while creating death certificate : " + e.getMessage();
        }
    }

    @PostMapping("/prepareDeathCertificateTransaction")
    public ResponseEntity<Map<String, Object>> prepareDeathCertificateTransaction(@RequestBody Map<String, String> request) {
        String deceasedDid = request.get("deceasedDid");
        String inheritor = request.get("inheritor");
        try {

            if (!LedgerUtility.isValidXrpAddress(inheritor)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid XRPL Address");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }

            System.out.println("Creating Death Certificate VC for deceasedDid: " + deceasedDid);
            String vc = governmentEntity.createDeathCertificateVC(deceasedDid);
            System.out.println("Preparing transaction for inheritor: " + inheritor);

            Map<String, Object> transactionPayload = didMgmt.createDidDocumentInheritor(vc, inheritor);
            System.out.println("Transaction Payload: " + transactionPayload);
            return ResponseEntity.ok(transactionPayload);
        } catch (Exception e) {
            System.out.println("Exception caught: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error while preparing the transaction : " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/verifyDeathCertificate")
    public ResponseEntity<Map<String, Object>> verifyDeathCertificate(
            @RequestParam String vc,
            @RequestParam String testatorDid,
            @RequestParam String inheritorAddress
    ) {
        Map<String, Object> response = new HashMap<>();
        try {
            // Cerificate verification
            vc = LedgerUtility.convertHexToString(vc);
            String result = governmentEntity.verifyAndFormatDeathCertificate(vc, testatorDid, governmentEntity.getPublicKey());
            System.out.println("Death certificate verification result: " + result);

            if (result != null && !result.isEmpty()) {
                // Valid certificate, extract testator address
                String testatorAddress = testatorDid.substring(16); // Extract address from DID
                System.out.println("Testator Address: " + testatorAddress);

                Map<String, Object> transferResponse = customerController.initiateTransferIfValid(testatorAddress, inheritorAddress);
                System.out.println("Transfer Response: " + transferResponse);
                response.put("message", "Inheritance claimed successfully");
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Invalid certificate");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        } catch (Exception e) {
            response.put("error", "Invalid certificate or already claimed inheritance : " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
