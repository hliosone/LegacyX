package com.example.legacyx.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.xrpl.xrpl4j.crypto.keys.PrivateKey;
import org.xrpl.xrpl4j.crypto.signing.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Lists;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;
import org.apache.commons.codec.binary.Hex;
import org.xrpl.xrpl4j.client.JsonRpcClientErrorException;
import org.xrpl.xrpl4j.crypto.keys.KeyPair;
import org.xrpl.xrpl4j.crypto.keys.PublicKey;
import org.xrpl.xrpl4j.crypto.signing.TransactionSigner;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoResult;
import org.xrpl.xrpl4j.model.client.accounts.AccountTransactionsResult;
import org.xrpl.xrpl4j.model.client.accounts.AccountTransactionsTransactionResult;
import org.xrpl.xrpl4j.model.client.common.LedgerIndex;
import org.xrpl.xrpl4j.model.client.fees.FeeResult;
import org.xrpl.xrpl4j.model.client.fees.FeeUtils;
import org.xrpl.xrpl4j.model.client.transactions.SubmitMultiSignedResult;
import org.xrpl.xrpl4j.model.client.transactions.SubmitResult;
import org.xrpl.xrpl4j.model.client.transactions.TransactionResult;
import org.xrpl.xrpl4j.model.jackson.ObjectMapperFactory;
import org.xrpl.xrpl4j.model.ledger.SignerEntry;
import org.xrpl.xrpl4j.model.ledger.SignerEntryWrapper;
import org.xrpl.xrpl4j.model.ledger.SignerListObject;
import org.xrpl.xrpl4j.model.transactions.*;
import org.xrpl.xrpl4j.crypto.signing.bc.BcSignatureService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import org.xrpl.xrpl4j.model.transactions.AccountSet;

import com.example.legacyx.model.xrplAccount;
import com.example.legacyx.utils.FunctionParameters;

import static com.example.legacyx.utils.LedgerUtility.convertHexToString;
import static com.example.legacyx.utils.LedgerUtility.convertStringToHex;

// https://github.com/XRPLF/xrpl4j/blob/main/V3_MIGRATION.md
// https://github.com/Aaditya-T/xrpl-wallet-connect
// https://www.youtube.com/watch?app=desktop&v=RYAHzvUqM64
// https://github.com/XRPLF/xrpl4j/blob/main/xrpl4j-integration-tests/src/test/java/org/xrpl/xrpl4j/tests/SubmitMultisignedIT.java#L120C1-L122C14
// https://github.com/XRPLF/xrpl4j/blob/eaf05aa55b0dc2b9246faba4d64686cd59760f0a/xrpl4j-integration-tests/src/test/java/org/xrpl/xrpl4j/tests/AccountSetIT.java#L58

public class MultisigMgmt {

    private static final BcSignatureService signatureService = new BcSignatureService();

    public static SignerEntry createSignerEntry(Address account, UnsignedInteger weight) {
        //CHECK QUE CEST PAS NULL ACCOUNT
        return SignerEntry.builder()
                .account(account)
                .signerWeight(weight)
                .build();
    }

    public static xrplAccount createMultisigAccount() {
        return new xrplAccount();
    }

    public static void createMultisigProposal(final String testator, final String inheritor, final xrplAccount multisig, final xrplAccount platformAccount, ClientService client) throws JsonProcessingException,
            JsonRpcClientErrorException, InterruptedException {

        Address inheritorAddress = Address.of(inheritor);
        Address testatorAddress = Address.of(testator);

        SignerEntry signerEntry1 = createSignerEntry(inheritorAddress, UnsignedInteger.valueOf(1));
        SignerEntry signerEntry2 = createSignerEntry(testatorAddress, UnsignedInteger.valueOf(1));
        // Had to put a weight of 2 for the platform account for the demo to work as I was missing multisigning xumm requests
        SignerEntry signerEntry3 = createSignerEntry(platformAccount.getrAddress(), UnsignedInteger.valueOf(2));
        Set<SignerEntry> signerEntries = Set.of(signerEntry1, signerEntry2, signerEntry3);

        FeeResult feeResult = client.getClient().fee();

        // Get the latest validated ledger index
        LedgerIndex latestIndex = ClientService.getLatestLedgerIndex();
        // LastLedgerSequence is the current ledger index + 4
        UnsignedInteger lastLedgerSequence = latestIndex.plus(UnsignedInteger.valueOf(4)).unsignedIntegerValue();

        final AccountInfoResult multisigAccountInfo = client.getAccountInfos(multisig.getrAddress());

        // english comments  everywhere
        // Create the SignerListSet transaction to set the signers and quorum
        SignerListSet signerListSet = SignerListSet.builder()
                .account(multisig.getrAddress())
                .fee(FeeUtils.computeNetworkFees(feeResult).recommendedFee())
                .sequence(multisigAccountInfo.accountData().sequence())
                .signerQuorum(UnsignedInteger.valueOf(2))  // Quorum of 2 signers
                .addSignerEntries(
                        SignerEntryWrapper.of(signerEntry1
                        ),
                        SignerEntryWrapper.of(signerEntry2
                        ),
                        SignerEntryWrapper.of(signerEntry3)
                )
                .signingPublicKey(multisig.getRandomKeyPair().publicKey())
                .lastLedgerSequence(lastLedgerSequence)
                .build();

        // Sign the transaction with the multisig account
        SingleSignedTransaction<SignerListSet> signedSignerListSet = signatureService.sign(
                multisig.getRandomKeyPair().privateKey(), signerListSet
        );

        client.getClient().submit(signedSignerListSet);

        TransactionResult<SignerListSet> transactionResult = ClientService.getTransactionResult(signedSignerListSet.hash(),
                SignerListSet.class, ClientService.getLatestLedgerIndex().unsignedIntegerValue());

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String resultCode = "Unknown";
        if (transactionResult != null) {
            if (transactionResult.metadata().isPresent()) {
                TransactionMetadata metadata = transactionResult.metadata().get();
                resultCode = metadata.transactionResult();
                System.out.println("Transaction result: " + resultCode);
            } else {
                System.out.println("No metadata available for the transaction.");
            }
        }

        disableMultisigMaster(multisig, client);
    }

    private static void disableMultisigMaster(final xrplAccount multisig, ClientService client) throws JsonRpcClientErrorException, JsonProcessingException, InterruptedException {
        // Récupérer les infos du compte source (testateur)
        final AccountInfoResult sourceAccountInfo = client.getAccountInfos(multisig.getrAddress());

        // Calculer les frais
        FeeResult feeResult = client.getClient().fee();
        // Get the latest validated ledger index
        LedgerIndex latestIndex = ClientService.getLatestLedgerIndex();
        // LastLedgerSequence is the current ledger index + 4
        UnsignedInteger lastLedgerSequence = latestIndex.plus(UnsignedInteger.valueOf(4)).unsignedIntegerValue();

        // Créer la transaction AccountSet pour désactiver la clé maître
        AccountSet accountSet = AccountSet.builder()
                .account(multisig.getrAddress())  // Compte multisig
                .fee(FeeUtils.computeNetworkFees(feeResult).recommendedFee())  // Frais calculés
                .sequence(sourceAccountInfo.accountData().sequence())  // Séquence du compte source
                .signingPublicKey(multisig.getRandomKeyPair().publicKey())
                .setFlag(AccountSet.AccountSetFlag.DISABLE_MASTER)  // Clé publique du compte source
                .lastLedgerSequence(lastLedgerSequence)
                .build();

        SingleSignedTransaction<AccountSet> signedAccountSet = signatureService.sign(
                multisig.getRandomKeyPair().privateKey(), accountSet);

        // Soumettre la transaction signée au réseau
        client.getClient().submit(signedAccountSet);

        TransactionResult<AccountSet> transactionResult = ClientService.getTransactionResult(signedAccountSet.hash(),
                AccountSet.class, ClientService.getLatestLedgerIndex().unsignedIntegerValue());

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String resultCode = "Unknown";
        if (transactionResult != null) {
            if (transactionResult.metadata().isPresent()) {
                TransactionMetadata metadata = transactionResult.metadata().get();
                resultCode = metadata.transactionResult();
                System.out.println("Transaction result: " + resultCode);
            } else {
                System.out.println("No metadata available for the transaction.");
            }
        }
    }

    public static boolean transferBalanceToInheritor(final xrplAccount platformAccount,
                                                  Address multisigAddress, Address inheritorAddress,
                                                  ClientService client) throws JsonRpcClientErrorException,
            InterruptedException {

        // Récupérer les informations du compte multisignature
        FeeResult feeResult = client.getClient().fee();
        AccountInfoResult multisigAccountInfos = client.getAccountInfos(multisigAddress);

        // Calculer le solde disponible en drops
        final BigDecimal availableBalance = client.getAccountXrpBalance(multisigAddress, FunctionParameters.AVAILABLE_BALANCE);
        BigDecimal balanceInDrops = availableBalance.multiply(BigDecimal.valueOf(1_000_000)); // 1 XRP = 1,000,000 drops

        // Calculer les frais pour la transaction multisignature
        XrpCurrencyAmount fees = FeeUtils.computeMultisigNetworkFees(feeResult, multisigAccountInfos.accountData().signerLists().get(0)).recommendedFee();

        // Calculer le montant à transférer (solde - frais)
        BigDecimal amountToTransfer = balanceInDrops.subtract(new BigDecimal(fees.toString()));

        System.out.println("Available balance: " + availableBalance);
        System.out.println("Amount to transfer: " + amountToTransfer);

        // Construire la transaction de paiement non signée
        Payment unsignedPayment = Payment.builder()
                .account(multisigAddress)
                .fee(fees)
                .sequence(multisigAccountInfos.accountData().sequence())
                .amount(XrpCurrencyAmount.ofDrops(UnsignedLong.valueOf(amountToTransfer.toBigInteger())))
                .destination(inheritorAddress)
                .build();

        // Signer la transaction uniquement avec le compte de la plateforme
        Signer platformSigner = signatureService.multiSignToSigner(platformAccount.getRandomKeyPair().privateKey(), unsignedPayment);

        // Construire la transaction multisignature avec la seule signature du compte de la plateforme
        MultiSignedTransaction<Payment> signedTransaction = MultiSignedTransaction.<Payment>builder()
                .unsignedTransaction(unsignedPayment)
                .signerSet(List.of(platformSigner))
                .build();

        client.getClient().submitMultisigned(signedTransaction);

        // Récupérer le résultat de la transaction pour vérifier l'état
        TransactionResult<Payment> transactionResult = ClientService.getTransactionResult(signedTransaction.hash(),
                Payment.class, ClientService.getLatestLedgerIndex().unsignedIntegerValue());

        Thread.sleep(5000);

        String resultCode = "Unknown";
        if (transactionResult != null && transactionResult.metadata().isPresent()) {
            TransactionMetadata metadata = transactionResult.metadata().get();
            resultCode = metadata.transactionResult();
            System.out.println("Transaction result: " + resultCode);
            return true;
        } else {
            System.out.println("No metadata available for the transaction.");
            return false;
        }
    }


    // create function to look in platform account wallet transaction where memo type contains the value LegacyX-Multi
    // and get memoData which is in format testaorAddress:multisigAddress
    // then get the signer list of multisif account, check if platform account is a signer and then get the other signer
    // not the testator but the inheritor which should be the other address then the one in the memoData
    // then return this address otherwise return null

    public static String getInheritorAddressFromMemo(final xrplAccount platformAccount, final Address testatorAddress,
                                                     final Address expectedInheritor, ClientService client)
            throws JsonRpcClientErrorException, JsonProcessingException, InterruptedException {
        AccountTransactionsResult transactionsResult = client.getAccountTransactions(platformAccount.getrAddress());
        List<AccountTransactionsTransactionResult<? extends Transaction>> txList = transactionsResult.transactions();

        for (AccountTransactionsTransactionResult<? extends Transaction> txResult : txList) {
            Transaction tx = txResult.resultTransaction().transaction();

            if (tx instanceof Payment paymentTx && !paymentTx.memos().isEmpty()) {
                for (MemoWrapper memo : paymentTx.memos()) {
                    // Affichage pour le débogage
                    System.out.println("Memo: " + memo.memo().memoData().get());
                    System.out.println("Memo type: " + memo.memo().memoType().get());

                    String convertedMemoType = convertStringToHex("LegacyX-Multi").toUpperCase();

                    if (memo.memo().memoType().get().equalsIgnoreCase(convertedMemoType)) {
                        // Convertir memoData de hexadécimal en chaîne de caractères
                        String memoData = convertHexToString(memo.memo().memoData().get());
                        System.out.println("Memo data: " + memoData);

                        // Récupérer le testatorAddress et multisigAddress du mémo
                        String[] memoDataArray = memoData.split(":");
                        String checkedTestatorAddress = memoDataArray[1];
                        String multisigAddress = memoDataArray[2];

                        if (Objects.equals(checkedTestatorAddress, testatorAddress.toString())) {
                            System.out.println("Testator address in memo: " + checkedTestatorAddress);
                            System.out.println("Multisig address in memo: " + multisigAddress);

                            // Récupérer les infos du compte multisig
                            final AccountInfoResult multisigAccountInfo = client.getAccountInfos(Address.of(multisigAddress));
                            // Vérifier la présence de `SignerListObject` dans `accountData()`
                            List<SignerListObject> signerListObjects = multisigAccountInfo.accountData().signerLists();
                            if (!signerListObjects.isEmpty()) {
                                SignerListObject signerList = signerListObjects.get(0);
                                List<SignerEntryWrapper> signerEntries = signerList.signerEntries();
                                System.out.println("Signer entries: " + signerEntries);

                                // Rechercher l'héritier parmi les signataires
                                for (SignerEntryWrapper signerEntry : signerEntries) {
                                    String signerAddress = signerEntry.signerEntry().account().value();
                                    System.out.println("Signer address: " + signerAddress);
                                    if (!Objects.equals(signerAddress, platformAccount.getrAddress().toString()) &&
                                            !Objects.equals(signerAddress, testatorAddress.toString())) {
                                        if (Objects.equals(signerAddress, expectedInheritor.toString())) {
                                            System.out.println("True multisig found: " + multisigAddress);
                                            return multisigAddress;
                                        }
                                    }
                                }
                            } else {
                                System.out.println("Aucune liste de signataires trouvée pour ce compte multisignature.");
                            }

                        }
                    }
                }
            }
        }
        return null;
    }
}
