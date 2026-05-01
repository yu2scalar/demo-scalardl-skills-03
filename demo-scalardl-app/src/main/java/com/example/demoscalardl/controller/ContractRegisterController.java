package com.example.demoscalardl.controller;

import com.example.demoscalardl.dto.ContractDefinition;
import com.example.demoscalardl.service.CodeManagementService;
import com.example.demoscalardl.service.ContractGenerator;
import com.example.demoscalardl.service.JavaCompilerService;
import com.example.demoscalardl.service.ScalarDLService;
import com.example.demoscalardl.util.VersionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * REST endpoint for registering a Contract.
 *
 * <p>{@code POST /api/contracts/register} — generate Java from a {@link ContractDefinition},
 * compile it with {@link JavaCompilerService}, and register it on the Ledger.
 *
 * <p>Kept in its own controller so the Swagger UI group surfaces only the register schema
 * ({@code ContractDefinition}); execution lives in {@code ContractExecuteController}.
 */
@Slf4j
@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractRegisterController {

    private final ContractGenerator contractGenerator;
    private final JavaCompilerService compiler;
    private final CodeManagementService codeManagement;
    private final ScalarDLService scalarDL;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody ContractDefinition definition) {
        Map<String, Object> response = new HashMap<>();
        try {
            String className = VersionUtil.getVersionedName(definition.getName(), definition.getVersion());
            response.put("className", className);

            codeManagement.saveContractDefinition(definition);
            String source = contractGenerator.generate(definition);
            codeManagement.saveContractSource(className, source);

            JavaCompilerService.CompilationResult compileResult = compiler.compileContract(className);
            response.put("compileSuccess", compileResult.isSuccess());
            response.put("compileMessage", compileResult.getMessage());
            if (!compileResult.isSuccess()) {
                return ResponseEntity.badRequest().body(response);
            }
            response.put("classFilePath", compileResult.getClassFilePath());

            byte[] bytes = Files.readAllBytes(Path.of(compileResult.getClassFilePath()));
            scalarDL.registerContract(className, compileResult.getFullyQualifiedClassName(), bytes, null);
            response.put("registered", true);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Contract registration failed", e);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
