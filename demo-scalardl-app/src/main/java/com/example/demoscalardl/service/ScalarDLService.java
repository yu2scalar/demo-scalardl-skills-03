package com.example.demoscalardl.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalar.dl.client.config.ClientConfig;
import com.scalar.dl.client.exception.ClientException;
import com.scalar.dl.client.service.ClientService;
import com.scalar.dl.client.service.ClientServiceFactory;
import com.scalar.dl.ledger.model.ContractExecutionResult;
import com.scalar.dl.ledger.util.JacksonSerDe;
import com.example.demoscalardl.config.ScalarDLProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Thin wrapper around {@link ClientService} that builds a {@link ClientConfig} from the
 * Spring-bound {@link ScalarDLProperties} and offers register / execute helpers used by the
 * REST controllers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScalarDLService {

    private final ScalarDLProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JacksonSerDe serde = new JacksonSerDe(new ObjectMapper());

    public void registerCertificate() throws ClientException, IOException {
        runWithClient(ClientService::registerCertificate);
    }

    public void registerContract(
            String contractId,
            String contractBinaryName,
            byte[] contractBytes,
            String contractProperties)
            throws ClientException, IOException {

        Path tempFile = Files.createTempFile("contract-", ".class");
        try {
            Files.write(tempFile, contractBytes);
            registerContractFromFile(contractId, contractBinaryName, tempFile.toString(), contractProperties);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    public void registerContractFromFile(
            String contractId,
            String contractBinaryName,
            String contractClassFile,
            String contractProperties)
            throws ClientException, IOException {

        JsonNode jsonProperties = (contractProperties == null || contractProperties.isEmpty())
                ? null
                : serde.deserialize(contractProperties);

        runWithClient(client ->
                client.registerContract(contractId, contractBinaryName, contractClassFile, jsonProperties));
        log.info("Contract registered: {}", contractId);
    }

    public void registerFunction(
            String functionId,
            String functionBinaryName,
            byte[] functionBytes)
            throws ClientException, IOException {

        Path tempFile = Files.createTempFile("function-", ".class");
        try {
            Files.write(tempFile, functionBytes);
            registerFunctionFromFile(functionId, functionBinaryName, tempFile.toString());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    public void registerFunctionFromFile(
            String functionId,
            String functionBinaryName,
            String functionClassFile)
            throws ClientException, IOException {

        runWithClient(client ->
                client.registerFunction(functionId, functionBinaryName, functionClassFile));
        log.info("Function registered: {}", functionId);
    }

    public Map<String, Object> executeContract(String contractId, JsonNode contractArgument)
            throws ClientException, IOException {
        return executeContract(contractId, contractArgument, null, null);
    }

    public Map<String, Object> executeContract(
            String contractId,
            JsonNode contractArgument,
            String functionId,
            JsonNode functionArgument)
            throws ClientException, IOException {

        ClientServiceFactory factory = new ClientServiceFactory();
        try {
            ClientService client = factory.create(buildClientConfig());
            ContractExecutionResult result = (functionId == null)
                    ? client.executeContract(contractId, contractArgument)
                    : client.executeContract(contractId, contractArgument, functionId, functionArgument);

            Map<String, Object> response = new HashMap<>();
            result.getContractResult().ifPresent(r -> response.put("contractResult", r));
            result.getFunctionResult().ifPresent(r -> response.put("functionResult", r));
            response.put("ledgerProofs", result.getLedgerProofs());
            response.put("auditorProofs", result.getAuditorProofs());
            log.info("Contract executed: {}", contractId);
            return response;
        } finally {
            factory.close();
        }
    }

    private ClientConfig buildClientConfig() throws IOException {
        Properties props = new Properties();

        props.setProperty("scalar.dl.client.server.host", properties.getServerHost());
        props.setProperty("scalar.dl.client.server.port", String.valueOf(properties.getServerPort()));
        props.setProperty("scalar.dl.client.server.privileged_port",
                String.valueOf(properties.getServerPrivilegedPort()));

        props.setProperty("scalar.dl.client.cert_holder_id", properties.getCertHolderId());
        props.setProperty("scalar.dl.client.cert_version", String.valueOf(properties.getCertVersion()));
        if (properties.getCertPath() != null) {
            props.setProperty("scalar.dl.client.cert_path", properties.getCertPath());
        }
        if (properties.getCertPem() != null) {
            props.setProperty("scalar.dl.client.cert_pem", properties.getCertPem());
        }
        if (properties.getPrivateKeyPath() != null) {
            props.setProperty("scalar.dl.client.private_key_path", properties.getPrivateKeyPath());
        }
        if (properties.getPrivateKeyPem() != null) {
            props.setProperty("scalar.dl.client.private_key_pem", properties.getPrivateKeyPem());
        }

        if (Boolean.TRUE.equals(properties.getTlsEnabled())) {
            props.setProperty("scalar.dl.client.tls.enabled", "true");
            if (properties.getTlsCaRootCertPath() != null) {
                props.setProperty("scalar.dl.client.tls.ca_root_cert_path", properties.getTlsCaRootCertPath());
            }
        }

        ScalarDLProperties.Auditor auditor = properties.getAuditor();
        if (Boolean.TRUE.equals(auditor.getEnabled())) {
            props.setProperty("scalar.dl.client.auditor.enabled", "true");
            props.setProperty("scalar.dl.client.auditor.host", auditor.getHost());
            props.setProperty("scalar.dl.client.auditor.port", String.valueOf(auditor.getPort()));
            props.setProperty("scalar.dl.client.auditor.privileged_port",
                    String.valueOf(auditor.getPrivilegedPort()));
            if (Boolean.TRUE.equals(auditor.getTlsEnabled())) {
                props.setProperty("scalar.dl.client.auditor.tls.enabled", "true");
            }
        }

        return new ClientConfig(props);
    }

    @FunctionalInterface
    private interface ClientAction {
        void run(ClientService client) throws ClientException, IOException;
    }

    private void runWithClient(ClientAction action) throws ClientException, IOException {
        ClientServiceFactory factory = new ClientServiceFactory();
        try {
            action.run(factory.create(buildClientConfig()));
        } finally {
            factory.close();
        }
    }
}
