package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 2A.6 and 6.51: every action and reusable workflow a GitHub workflow uses is pinned to a full commit (40 hex digits), not a
 * tag: a tag can be moved to different code by whoever controls that repository, a commit cannot. The release workflow handles a
 * signing certificate, so this matters most there. A workflow added later is checked too.
 */
public class WorkflowPinningTest {

    private static final Path WORKFLOWS = Path.of(".github", "workflows");
    private static final Pattern USES = Pattern.compile("^\\s*(?:-\\s*)?uses:\\s*(\\S+)", Pattern.MULTILINE);
    private static final Pattern PINNED = Pattern.compile("^[^@\\s]+@[0-9a-f]{40}$");

    private static List<Path> workflows() throws IOException {
        try (Stream<Path> files = Files.list(WORKFLOWS)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".yml") || p.getFileName().toString().endsWith(".yaml")).sorted().toList();
        }
    }

    @Test
    public void everyUsedActionIsPinnedToACommit() throws IOException {
        List<String> loose = new ArrayList<>();
        int seen = 0;
        for (Path workflow : workflows()) {
            Matcher m = USES.matcher(Files.readString(workflow, StandardCharsets.UTF_8));
            while (m.find()) {
                String ref = m.group(1);
                if (ref.startsWith("./")) continue; // a file of this repository
                seen++;
                if (!PINNED.matcher(ref).matches()) loose.add(workflow.getFileName() + ": " + ref);
            }
        }
        assertTrue("the workflows use actions", seen > 0);
        assertTrue("pin these to a full commit (the release goes in a comment): " + loose, loose.isEmpty());
    }

    @Test
    public void theReleaseWorkflowNeverPastesAnInputIntoAScript() throws IOException {
        String text = Files.readString(WORKFLOWS.resolve("release.yml"), StandardCharsets.UTF_8);
        // A workflow input or the branch name inside a run: script can carry commands ("script injection"): go through env instead.
        for (String line : text.split("\n")) {
            if (line.contains("github.event.inputs") || line.contains("github.head_ref")) {
                assertTrue("only under env: " + line.trim(), line.trim().startsWith("INPUT_") || line.contains("env"));
            }
        }
    }

    @Test
    public void theSigningSecretsAreNeverPrintedAndTheCertificateFileIsRemoved() throws IOException {
        String text = Files.readString(WORKFLOWS.resolve("release.yml"), StandardCharsets.UTF_8);
        assertFalse("no echo of a secret", text.matches("(?s).*(echo|Write-Host)[^\\n]*secrets\\..*"));
        assertTrue("the certificate file is deleted in a finally block", text.contains("finally") && text.contains("Remove-Item $pfx"));
        assertTrue("signing is skipped, not failed, when no certificate is configured", text.contains("is NOT signed"));
    }

    @Test
    public void theSigningScriptChecksTheSignatureItMade() throws IOException {
        String script = Files.readString(Path.of("scripts", "Sign-Windows.ps1"), StandardCharsets.UTF_8);
        assertTrue(script.contains("/tr") && script.contains("/td"));       // a time stamp, so the signature outlives the certificate
        assertTrue(script.contains("verify /pa"));                           // and it is checked, not assumed
        assertTrue(script.contains("-Thumbprint") && script.contains("-PfxPath")); // a file or a certificate in the store (hardware token)
    }
}
