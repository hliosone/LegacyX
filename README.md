# LegacyX - XRPL Inheritance Management

LegacyX is an inheritance management platform built on the XRP Ledger (XRPL) ecosystem. It enables secure and decentralized management of digital assets, incorporating multisignature accounts and Decentralized Identifiers (DIDs) for identity verification. This application is developed with Spring Boot as the backend, utilizing XRPL for secure financial transactions and decentralized identity.
## Process Overview

1. **Service Fee Payment**  
   Users pay a service fee of 5 XRP to the platform. This fee is verified to ensure it was received from the testator's account.

2. **Multisig Account Creation**  
   Once the service fee is confirmed, the platform generates a multisig account for the inheritance, where the testator and platform accounts are configured as signers with specific weights.

3. **Inheritance Contract Activation**  
   After multisig creation, the inheritance contract is activated. The platform sets up the multisig rules and configures the necessary thresholds for fund transfer upon the testator's verification.

4. **Death Certificate Verification**  
   When the testator passes away, the government can issue a death certificate as a Verifiable Credential (VC) containing the DID of the deceased. This certificate is verified before any transfer is initiated.

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
  Specific functions for government operations, such as issuing Verifiable Credentials (VCs) to validate identity-related events (e.g., issuing a death certificate).

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