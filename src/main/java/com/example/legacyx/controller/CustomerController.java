package com.example.legacyx.controller;

import com.example.legacyx.model.xrplAccount;
import com.example.legacyx.service.DIDMgmt;
import com.example.legacyx.service.GovernmentEntity;
import com.example.legacyx.service.MultisigMgmt;
import com.example.legacyx.utils.FunctionParameters;
import com.example.legacyx.utils.LedgerUtility;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.primitives.UnsignedLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.xrpl.xrpl4j.client.JsonRpcClientErrorException;
import org.xrpl.xrpl4j.model.client.accounts.AccountTransactionsResult;
import org.xrpl.xrpl4j.model.client.accounts.AccountTransactionsTransactionResult;
import org.xrpl.xrpl4j.model.transactions.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.example.legacyx.service.ClientService;

//import static com.example.legacyx.service.MultisigMgmt.transferBalanceToInheritor;
import static com.example.legacyx.utils.LedgerUtility.createInheritancePaymentMemo;

@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    @Value("${PLATFORM_XRPL_PK}")
    private String platformPk;
    private final xrplAccount platformAccount;

    private final ClientService client;

    // Temporary cache to store multisig accounts pending activation
    private static final Map<String, xrplAccount> pendingMultisigAccounts = new ConcurrentHashMap<>();

    @Autowired
    public CustomerController(ClientService client, @Value("${PLATFORM_XRPL_PK}") String platformPk) {
        this.client = client;
        this.platformAccount = xrplAccount.importAccount(platformPk);
    }

    @PostMapping("/generateMultisigAddress")
    public ResponseEntity<Map<String, String>> generateMultisigAddress() {
        try {
            // Create a multisig account
            xrplAccount customerMultisigAccount = MultisigMgmt.createMultisigAccount();
            String multisigAddress = customerMultisigAccount.getrAddress().toString();

            pendingMultisigAccounts.put(multisigAddress, customerMultisigAccount);

            // Return the address to the frontend
            Map<String, String> response = new HashMap<>();
            response.put("multisigAddress", multisigAddress);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Remove the multisig address and customerMultisigAccount from pendingMultisigAccounts
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean isServiceFeeReceivedFromTestator(String testatorAddress) {
        try {
            AccountTransactionsResult transactionsResult = client.getAccountTransactions(platformAccount.getrAddress());

            return transactionsResult.transactions().stream()
                    .anyMatch(txResult -> {
                        Transaction tx = txResult.resultTransaction().transaction();
                        // Verify that the payment is 5 XRP and comes from the testator
                        return tx instanceof Payment &&
                                ((Payment) tx).amount().equals(XrpCurrencyAmount.ofDrops(5000000)) &&
                                tx.account().equals(Address.of(testatorAddress));
                    });
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @PostMapping("/verifyServiceFee")
    public ResponseEntity<Boolean> verifyServiceFee(@RequestBody Map<String, String> request) {
        String testatorAddress = request.get("testatorAddress");
        boolean feeReceived = isServiceFeeReceivedFromTestator(testatorAddress);
        return ResponseEntity.ok(feeReceived);
    }

//    @PostMapping("/verifyServiceFee")
//    public ResponseEntity<Boolean> verifyServiceFee() {
//        try {
//            AccountTransactionsResult transactionsResult = client.getAccountTransactions(platformAccount.getrAddress());
//
//            boolean feeReceived = transactionsResult.transactions().stream()
//                    .anyMatch(txResult -> {
//                        Transaction tx = txResult.resultTransaction().transaction();
//                        return tx instanceof Payment && ((Payment) tx).amount().equals(XrpCurrencyAmount.ofDrops(5000000));
//                    });
//
//            return ResponseEntity.ok(feeReceived);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(false);
//        }
//    }

    @PostMapping("/activateInheritanceContract")
    public ResponseEntity<String> activateInheritanceContract(@RequestBody Map<String, String> request) {
        String testatorAddress = request.get("testatorAddress");
        String inheritorAddress = request.get("inheritorAddress");
        String multisigAddress = request.get("multisigAddress");

        try {
            xrplAccount customerMultisigAccount = pendingMultisigAccounts.get(multisigAddress);
            if (customerMultisigAccount == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Multisig account not found");
            }

            boolean feeReceived = isServiceFeeReceivedFromTestator(testatorAddress);
            if (!feeReceived) {
                return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                        .body("Service fees (5 XRP) not received yet.");
            }

            BigDecimal balance = client.getAccountXrpBalance(customerMultisigAccount.getrAddress(), FunctionParameters.TOTAL_BALANCE);
            BigDecimal requiredBalanceInXrp = new BigDecimal("20");
            if (balance.compareTo(requiredBalanceInXrp) >= 0) {
                MultisigMgmt.createMultisigProposal(testatorAddress, inheritorAddress, customerMultisigAccount,
                        platformAccount, client);

                String memoContent = "inheritance:" + testatorAddress + ":" + multisigAddress;
                MemoWrapper multisigMemo = createInheritancePaymentMemo(memoContent);
                // Send 1 XRP with memo containing kind of mapping testator:multisigaddress to multisig account
                ClientService.sendPayment(platformAccount, Address.of(multisigAddress),
                        XrpCurrencyAmount.ofXrp(new BigDecimal("1")), multisigMemo);

                pendingMultisigAccounts.remove(multisigAddress);

                return ResponseEntity.ok("Inheritance account created successfully.");
            } else {
                return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                        .body("Multisig account not funded with the required balance. Required: 20 XRP, Available: " + balance + " XRP");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error while activating inheritance contract: " + e.getMessage());
        }
    }

    @PostMapping("/initiateTransferIfValid")
    public Map<String, Object> initiateTransferIfValid(
            @RequestParam String testatorAddress,
            @RequestParam String inheritorAddress
    ) {
        Map<String, Object> response = new HashMap<>();
        try {

            if (!LedgerUtility.isValidXrpAddress(inheritorAddress) || !LedgerUtility.isValidXrpAddress(testatorAddress)) {
                response.put("error", "Invalid XRPL Address");
                throw new Exception("Invalid XRPL Address");
            }

            String multisigAddress = MultisigMgmt.getInheritorAddressFromMemo(platformAccount,
                    Address.of(testatorAddress), Address.of(inheritorAddress), client);

            if (multisigAddress == null) {
                response.put("error", "Active inheritance account not found for testator: " + testatorAddress);
                return response;
            }

            boolean result = MultisigMgmt.transferBalanceToInheritor(platformAccount, Address.of(multisigAddress),
                    Address.of(inheritorAddress), client);

            if (result) {
                response.put("success", "Balance transferred successfully");
            } else {
                throw new Exception("Error while transferring balance, please try again");
            }
        } catch (Exception e) {
            response.put("error", e.getMessage());
        }
        return response;
    }
}
