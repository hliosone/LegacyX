# LegacyX - XRPL Inheritance Platform

LegacyX is an inheritance management platform built on the XRP Ledger ecosystem, designed to address a crucial gap in digital asset management: what happens to our assets and keys if we pass away? While many people store their keys securely, few consider inheritance, leaving families unable to access digital assets. LegacyX bridges this gap by combining digital inheritance with decentralized identity, ensuring that assets are securely passed on.

The platform is also aiming to be compatible with the upcoming eIDAS 2.0 regulation, which will require EU member states to provide digital identity wallets to their citizens starting in 2026. Using SD-JWT-based Verifiable Credentials (VCs) embedded in Decentralized Identifier (DID) documents, LegacyX aligns with these standards, making it a future-proof solution for both identity and inheritance management on the blockchain.

Proof of Concept by [Stan Stelcher](https://www.linkedin.com/in/stan-stelcher/)

## Process Overview

1. **Service Fee Payment**  
   After inserting the inheritor address, the user pays a service fee of 5 XRP to the platform. This fee is verified to ensure it was received from the testator's account.

2. **Multisig Account Creation**  
   Once the service fee is confirmed, the platform generates a multisig account for the inheritance, where the testator, inheritor and platform accounts are configured as signers with specific weights. The testator funds the account with 20 XRP (arbitrary for demo purposes).

3. **Inheritance Contract Activation**  
   After multisig creation, the inheritance contract is activated. The platform account will send 1 XRP from the 5 received as service fees to the multisig wallet and put memo data in the transaction and inheritance type (testatorAddress:multisigAddress) to help later with mapping. 
The platform then sets up the multisig rules and the testator is free to fund the account during his lifetime.

4. **Death Certificate Verification**  
   When the testator passes away, the government can issue a death certificate as a SD-JWT Verifiable Credential (VC) containing the DID of the deceased (An exemple function as this is supposed to be issued from a government entity, is provided on the platform where the government issues you, the inheritor, a signed VC using their JWK private key containing the testator DID as a death certificate). This certificate is verified using the government JWK public key and checking the fields before any transfer is initiated.

5. **Fund Transfer to Inheritor**  
   Upon successful verification of the death certificate, the platform prepares the transfer of the multisig account balance to the inheritor’s address.

## Key Classes and Functionalities

- **CustomerController**  
  Handles customer-related API endpoints. Manages endpoints for:
    - Service fee verification
    - Multisig account generation
    - Inheritance contract activation

- **GovernmentController**  
  Manages government-specific API endpoints for:
    - Death certificate issuance and verification
    - DID and VC management for government-verified documents

- **ClientService**  
  Centralizes XRPL client interactions, including submitting and verifying transactions on the XRP Ledger. Also includes helper methods for interacting with account transactions.

- **DIDMgmt**  
  Handles DID creation and management for decentralized identity verification using the XRP Ledger. This includes setting up unique identifiers for users within the XRPL ecosystem.

- **GovernmentEntity**  
  Specific functions for government operations, such as issuing Verifiable Credentials (VCs) to validate identity-related events (ex: issuing a death certificate).

- **MultisigMgmt**  
  Manages all aspects of multisig account setup and transactions. This includes configuring signer weights, setting the multisig account, and preparing transfers from the multisig account upon proper verification.

- **FunctionParameters**  
  Defines various constants used throughout the application, such as balance thresholds, transaction fees, and other parameters that need to be configurable.

- **LedgerUtility**  
  Provides utility methods for interacting with the XRPL ledger, simplifying ledger-specific calculations and data extraction.

## Resources used

### DIDs:
- [W3C DID Core](https://www.w3.org/TR/did-core/)
- [XLS-40 Amendment](https://github.com/XRPLF/XRPL-Standards/discussions/100)

### Verifiable Credentials:
- [W3C VC Data Model 2.0](https://www.w3.org/TR/vc-data-model-2.0/)
- [Criipto Blog on SD-JWT Based Verifiable Credentials](https://www.criipto.com/blog/sd-jwt-based-verifiable-credentials)

### EU Digital Identity Wallet:
- [EU Digital Identity Wallet Home](https://ec.europa.eu/digital-building-blocks/sites/display/EUDIGITALIDENTITYWALLET/EU+Digital+Identity+Wallet+Home)
- [GitHub Repository for EU Digital Identity Wallet Library](https://github.com/eu-digital-identity-wallet/eudi-lib-jvm-sdjwt-kt)


### Problem 1: User is only checked once for service fee payment

**Description:**  
The user is only checked once for service fee payment, which can lead to multiple multisig accounts being created for the same user with only one service fee payment.

**Solution:**  
Generate the multisig account before generating the service fee payment QR code. Then add a Memo in this generated payment QR code, ex: multisig address generated + user address + service fee amount. Check if the user has already paid the service fee by checking the memo in the payment transaction. As the generated multisig address is unique for each inheritance contract, we can ensure that user pays for each service.

---

### Problem 2: Destroy the multisig account after the inheritance contract is completed and get the remaining XRP back to the user

**Description:**  
The multisig account is not destroyed after the inheritance contract is completed, which can lead to multiple claim attempts by the inheritor.

**Solution:**  
A method to destroy the account and get the remaining XRP back to the user has been created (in the repo) but not implemented in the workflow.

---

### Problem 3: Change the inheritor address in case they lose access to their account or pass away

**Description:**  
The inheritor address cannot be changed once the inheritance contract is activated, which can lead to fund loss if the inheritor loses access to their account or passes away.

**Solution:**  
Add a method to change the inheritor address in the signer list of the multisig account. This method should be accessible only by the testator.

---

### Problem 4: Multisig signing with one local signer and one remote signer

**Description:**  
The platform account is a local signer, and the testator account is a remote signer. The platform account cannot sign transactions on behalf of the testator account.

**Solution:**  
Find a way with xrpl4j to sign the transaction using the platform wallet locally and the testator wallet remotely.

## Future Developments

- **Smart Contract Integration**  
  Integrate smart contracts to automate the inheritance process and ensure that funds are transferred according to predefined rules.
- **Legacy Planning Tools**  
  Develop tools to help users plan their inheritance, including asset allocation, document storage, and beneficiary management.
- **Cross-Chain Compatibility**  
  Explore cross-chain compatibility to enable inheritance management across multiple blockchain networks.
- **Regulatory Compliance**  
  Ensure compliance with relevant regulations and standards to provide a secure and legally compliant inheritance management platform.
- **User Interface Enhancements**  
  Improve the user interface to make it more intuitive and user-friendly, enhancing the overall user experience.
- **Community Engagement**  
  Engage with the XRPL community to gather feedback, suggestions, and contributions to enhance the platform further.
- **Education and Awareness**  
  Provide educational resources and awareness campaigns to help users understand the importance of inheritance planning and management.
- **Partnerships and Collaborations**  
  Collaborate with other projects, organizations, and institutions to expand the platform's reach and impact in the inheritance management space.
- **Feedback and Support**  
  Offer responsive customer support and gather feedback from users to continuously improve the platform and address their needs effectively.
- **Global Reach**  
  Expand the platform's reach globally to serve users from different regions.
- **Resilience and Adaptability**  
  Build resilience and adaptability into the platform to withstand challenges, disruptions, and uncertainties in the inheritance management landscape.
