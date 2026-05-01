package com.example.demoscalardl.controller;

import com.example.demoscalardl.dto.FunctionDefinition;
import com.example.demoscalardl.service.CodeManagementService;
import com.example.demoscalardl.service.FunctionGenerator;
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
 * REST endpoint for registering a Function.
 *
 * <p>{@code POST /api/functions/register} — generate Java from a {@link FunctionDefinition},
 * compile it, and register it on the Ledger. Functions are then invoked atomically with their
 * linked Contract via {@code POST /api/contracts/execute}.
 */
@Slf4j
@RestController
@RequestMapping("/api/functions")
@RequiredArgsConstructor
public class FunctionRegisterController {

    private final FunctionGenerator functionGenerator;
    private final JavaCompilerService compiler;
    private final CodeManagementService codeManagement;
    private final ScalarDLService scalarDL;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody FunctionDefinition definition) {
        Map<String, Object> response = new HashMap<>();
        try {
            String className = VersionUtil.getVersionedName(definition.getName(), definition.getVersion());
            response.put("className", className);

            codeManagement.saveFunctionDefinition(definition);
            String source = functionGenerator.generate(definition);
            codeManagement.saveFunctionSource(className, source);

            JavaCompilerService.CompilationResult compileResult = compiler.compileFunction(className);
            response.put("compileSuccess", compileResult.isSuccess());
            response.put("compileMessage", compileResult.getMessage());
            if (!compileResult.isSuccess()) {
                return ResponseEntity.badRequest().body(response);
            }
            response.put("classFilePath", compileResult.getClassFilePath());

            byte[] bytes = Files.readAllBytes(Path.of(compileResult.getClassFilePath()));
            scalarDL.registerFunction(className, compileResult.getFullyQualifiedClassName(), bytes);
            response.put("registered", true);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Function registration failed", e);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
