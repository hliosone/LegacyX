package com.example.legacyx.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.primitives.UnsignedInteger;
import okhttp3.HttpUrl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xrpl.xrpl4j.client.JsonRpcClientErrorException;
import org.xrpl.xrpl4j.client.XrplClient;
import org.xrpl.xrpl4j.crypto.keys.PrivateKey;
import org.xrpl.xrpl4j.crypto.signing.SignatureService;
import org.xrpl.xrpl4j.crypto.signing.SingleSignedTransaction;
import org.xrpl.xrpl4j.crypto.signing.bc.BcSignatureService;
import org.xrpl.xrpl4j.model.client.accounts.*;
import org.xrpl.xrpl4j.model.client.common.LedgerIndex;
import org.xrpl.xrpl4j.model.client.common.LedgerSpecifier;
import org.xrpl.xrpl4j.model.client.ledger.LedgerRequestParams;
import org.xrpl.xrpl4j.model.client.serverinfo.ServerInfo;
import org.xrpl.xrpl4j.model.client.serverinfo.ServerInfoResult;
import org.xrpl.xrpl4j.model.client.transactions.TransactionRequestParams;
import org.xrpl.xrpl4j.model.client.transactions.TransactionResult;
import org.xrpl.xrpl4j.model.immutables.FluentCompareTo;
import org.xrpl.xrpl4j.model.transactions.*;
import org.xrpl.xrpl4j.model.transactions.Address;
import com.example.legacyx.utils.FunctionParameters;

import com.example.legacyx.model.xrplAccount;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class ClientService {
    private static XrplClient rippledClient;

    public ClientService(@Value("${XRPL_CLIENT_URL}") String url) {
        this.rippledClient = new XrplClient(HttpUrl.get(url));
    }

    public XrplClient getClient() {
        return rippledClient;
    }

    public ServerInfoResult getServerInfo() throws JsonRpcClientErrorException {
        return rippledClient.serverInformation();
    }

    public XrpCurrencyAmount getBaseIncrementXrp() throws JsonRpcClientErrorException {
        ServerInfo serverInfoData = getServerInfo().info();
        Optional<ServerInfo.ValidatedLedger> validatedLedgerOptional = serverInfoData.validatedLedger();
        if (validatedLedgerOptional.isEmpty()) {
            System.out.println("Error: Validated ledger information is not available.");
            return null;
        }
        return validatedLedgerOptional.get().reserveIncXrp();
    }

    public static AccountInfoResult getAccountInfos(Address accountAddress) throws JsonRpcClientErrorException {
        AccountInfoRequestParams requestParams = AccountInfoRequestParams.builder()
                .account(accountAddress)
                .ledgerSpecifier(LedgerSpecifier.VALIDATED)
                .build();
        AccountInfoResult selectedAccountInfos = null;
        try {
            selectedAccountInfos = rippledClient.accountInfo(requestParams);
        } catch (JsonRpcClientErrorException e) {
            System.out.println("Error while fetching account data: " + e.getMessage());
            return null;
        }
        return selectedAccountInfos;
    }

    public AccountTransactionsResult getAccountTransactions(Address accountAddress) throws JsonRpcClientErrorException {
        AccountTransactionsRequestParams requestParams = AccountTransactionsRequestParams
                .unboundedBuilder()
                .account(accountAddress)
                .build();
        return rippledClient.accountTransactions(requestParams);
    }

    public BigDecimal getAccountXrpBalance(Address accountAddress, FunctionParameters type) throws JsonRpcClientErrorException {
        if(!(type instanceof FunctionParameters)){
            System.out.println("Invalid balance type !");
            return null;
        }
        AccountInfoResult infos = null;
        try{
            infos = getAccountInfos(accountAddress);
        } catch (JsonRpcClientErrorException e){
            System.out.println("Problem while fetching account data : " + e.getMessage());
            return null;
        }
        ServerInfoResult rippledServer = getServerInfo();
        if(type == FunctionParameters.TOTAL_BALANCE){
            return infos.accountData().balance().toXrp();
        }

        // Check reserve balance
        XrpCurrencyAmount baseReserveInDrops = rippledServer.info().validatedLedger().get().reserveBaseXrp();
        Long numberOfOwnedObjects = infos.accountData().ownerCount().longValue();

        BigDecimal amountReserve;
        BigDecimal reserveIncrementInDrops = rippledServer.info().validatedLedger().get().reserveIncXrp().toXrp();
        if(numberOfOwnedObjects < 1){
            amountReserve = baseReserveInDrops.toXrp();
        } else {
            // Caculate account amount reserve
            amountReserve = reserveIncrementInDrops
                    .multiply(BigDecimal.valueOf(numberOfOwnedObjects))
                    .add(baseReserveInDrops.toXrp());
        }

        if(type == FunctionParameters.RESERVED_BALANCE){
            return amountReserve;
        } else if(type == FunctionParameters.AVAILABLE_BALANCE){
            return infos.accountData().balance().toXrp().subtract(amountReserve);
        } else {
            return null;
        }
    }

    public static LedgerIndex getLatestLedgerIndex() throws JsonRpcClientErrorException {
        try {
            return rippledClient.ledger(
                            LedgerRequestParams.builder()
                                    .ledgerSpecifier(LedgerSpecifier.VALIDATED)
                                    .build()
                    )
                    .ledgerIndex()
                    .orElseThrow(() -> new RuntimeException("LedgerIndex not available."));
        } catch (JsonRpcClientErrorException e) {
            System.out.println("Failed to get the latest ledger index (JsonRpcClientErrorException): " + e.getMessage());
            return null;
        } catch (RuntimeException e) {
            System.out.println("Failed to get the latest ledger index (RuntimeException): " + e.getMessage());
            return null;
        }
    }

    public static <T extends Transaction> TransactionResult<T> getTransactionResult(Hash256 txHash, Class<T> transactionType, UnsignedInteger lastLedgerSequence) throws InterruptedException, JsonRpcClientErrorException {

        TransactionResult<T> transactionResult = null;

        boolean transactionValidated = false;
        boolean transactionExpired = false;
        while (!transactionValidated && !transactionExpired) {
            Thread.sleep(4 * 1000);

            //Check return !!!
            LedgerIndex latestValidatedLedgerIndex = getLatestLedgerIndex();

            transactionResult = rippledClient.transaction(TransactionRequestParams.of(txHash), transactionType);

            if (transactionResult.validated()) {
                System.out.println(transactionType.getSimpleName() + " was validated with result code "
                        + transactionResult.metadata().get().transactionResult());
                transactionValidated = true;
            } else if (lastLedgerSequence != null) {
                boolean lastLedgerSequenceHasPassed = FluentCompareTo.
                        is(latestValidatedLedgerIndex.unsignedIntegerValue())
                        .greaterThan(UnsignedInteger.valueOf(lastLedgerSequence.intValue()));
                if (lastLedgerSequenceHasPassed) {
                    System.out.println("LastLedgerSequence has passed. Last tx response: " +
                            transactionResult);
                    return null;
                } else {
                    System.out.println(transactionType.getSimpleName() + " not yet validated.");
                }
            } else {
                System.out.println("Transaction not found !");
                return null;
            }
        }
        return transactionResult;
    }

    public static String sendPayment(xrplAccount account, final Address destination, final XrpCurrencyAmount amount)
            throws JsonRpcClientErrorException, JsonProcessingException, InterruptedException {
        return sendPayment(account, destination, amount, null);
    }

    public static String sendPayment(xrplAccount account, final Address destination, final XrpCurrencyAmount amount,
                                     final MemoWrapper txMemo) throws JsonRpcClientErrorException,
            JsonProcessingException, InterruptedException {

        AccountInfoResult accountInfoResult = getAccountInfos(account.getrAddress());
        UnsignedInteger sequence = accountInfoResult.accountData().sequence();

        // Request current fee information from rippled
        XrpCurrencyAmount openLedgerFee = rippledClient.fee().drops().openLedgerFee();

        // Get the latest validated ledger index
        LedgerIndex latestIndex = getLatestLedgerIndex();

        // LastLedgerSequence is the current ledger index + 4
        UnsignedInteger lastLedgerSequence = latestIndex.plus(UnsignedInteger.valueOf(4)).unsignedIntegerValue();

        Payment payment;
        //I know it's ugly, but I had no time to check the null conditions for wrappers to factorize the code
        if(txMemo != null){
            payment = Payment.builder()
                    .account(account.getrAddress())
                    .amount(amount)
                    .destination(destination)
                    .sequence(sequence)
                    .fee(openLedgerFee)
                    .signingPublicKey(account.getRandomKeyPair().publicKey())
                    .lastLedgerSequence(lastLedgerSequence)
                    .addMemos(txMemo)
                    .build();
        } else {
            payment = Payment.builder()
                    .account(account.getrAddress())
                    .amount(amount)
                    .destination(destination)
                    .sequence(sequence)
                    .fee(openLedgerFee)
                    .signingPublicKey(account.getRandomKeyPair().publicKey())
                    .lastLedgerSequence(lastLedgerSequence)
                    .build();
        }

        SignatureService<PrivateKey> signatureService = new BcSignatureService();
        SingleSignedTransaction<Payment> signedPayment =
                signatureService.sign(account.getRandomKeyPair().privateKey(), payment);

        rippledClient.submit(signedPayment);

        // Wait for validation
        TransactionResult<Payment> transactionResult = getTransactionResult(signedPayment.hash(), Payment.class, lastLedgerSequence);

        String resultCode = "Unknown";
        if (transactionResult != null) {
            if (transactionResult.metadata().isPresent()) {
                TransactionMetadata metadata = transactionResult.metadata().get();
                resultCode = metadata.transactionResult();
                metadata.deliveredAmount().ifPresent(deliveredAmount ->
                        System.out.println("XRP Delivered: " + amount.toXrp()));
            } else {
                System.out.println("No metadata available for the transaction.");
            }
        }

        return resultCode;
    }

    // goal was to delete an account so that the inheritor can claim also the reserved balance
    // it is working but was not implemented in the final version (lack of time)
    public String deleteAccount(xrplAccount account, Address destination, XrpCurrencyAmount reserveIncrementPrice)
            throws JsonRpcClientErrorException, JsonProcessingException, InterruptedException {

        AccountInfoResult accountInfoResult = getAccountInfos(account.getrAddress());
        UnsignedInteger sequence = accountInfoResult.accountData().sequence();

        // Request current fee information from rippled
        XrpCurrencyAmount openLedgerFee = rippledClient.fee().drops().openLedgerFee();

        LedgerIndex latestIndex = getLatestLedgerIndex();
        UnsignedInteger lastLedgerSequence = latestIndex.plus(UnsignedInteger.valueOf(4)).unsignedIntegerValue();

        AccountDelete deleteAcc = AccountDelete.builder()
                .account(account.getrAddress())
                .sequence(sequence)
                .fee(openLedgerFee.plus(reserveIncrementPrice))
                .destination(destination)
                .signingPublicKey(account.getRandomKeyPair().publicKey())
                .build();

        SignatureService<PrivateKey> signatureService = new BcSignatureService();
        SingleSignedTransaction<AccountDelete> deletePayment =
                signatureService.sign(account.getRandomKeyPair().privateKey(), deleteAcc);
        //Can be generic
        System.out.println("Delete Result: " + deletePayment.signedTransaction());

        rippledClient.submit(deletePayment);

        // Wait for validation
        TransactionResult<AccountDelete> transactionResult = getTransactionResult(deletePayment.hash(), AccountDelete.class, lastLedgerSequence);

        String resultCode = "Unknown";
        if (transactionResult != null) {
            if (transactionResult.metadata().isPresent()) {
                TransactionMetadata metadata = transactionResult.metadata().get();
                resultCode = metadata.transactionResult();
                if(resultCode == "tesSUCCESS"){
                    System.out.println("Account " + destination + " was successfully deleted !");
                } else {
                    System.out.println("Account deletion failed !");
                }
            } else {
                System.out.println("No metadata available for the transaction.");
            }
        }
        return resultCode;
    }


}