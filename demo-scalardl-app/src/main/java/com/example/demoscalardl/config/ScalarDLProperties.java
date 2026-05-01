package com.example.demoscalardl.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised settings for the ScalarDL Java Client SDK.
 *
 * Keys are bound from {@code application.properties} (prefix {@code scalardl}).
 * Optional Auditor section is bound from {@code scalardl.auditor.*}.
 */
@Data
@ConfigurationProperties(prefix = "scalardl")
public class ScalarDLProperties {

    /** gRPC host / port of the ScalarDL Ledger envoy. */
    private String serverHost = "localhost";
    private Integer serverPort = 50051;
    private Integer serverPrivilegedPort = 50052;

    /** Client identity (entity id) — used by both PKI and HMAC auth. */
    private String certHolderId = "client";
    private Integer certVersion = 1;

    /** Either certPath OR certPem must be set when using PKI auth. */
    private String certPath;
    private String certPem;

    /** Either privateKeyPath OR privateKeyPem must be set when using PKI auth. */
    private String privateKeyPath;
    private String privateKeyPem;

    /** When true, the SDK uses TLS to talk to the Ledger. */
    private Boolean tlsEnabled = false;
    private String tlsCaRootCertPath;
    private String tlsCaRootCertPem;

    /** Optional: set when an auth proxy in front of the Ledger requires a token. */
    private String authorizationCredential;

    private Auditor auditor = new Auditor();

    @Data
    public static class Auditor {
        /** When true, every Ledger call is mirrored to the Auditor for end-to-end audit. */
        private Boolean enabled = false;
        private String host = "localhost";
        private Integer port = 40051;
        private Integer privilegedPort = 40052;
        private Boolean tlsEnabled = false;
        private String tlsCaRootCertPath;
        private String tlsCaRootCertPem;
        private String authorizationCredential;
        private String linearizableValidationContractId = "validate-ledger";
    }
}
