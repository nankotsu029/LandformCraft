package com.github.nankotsu029.landformcraft.cli;

import com.github.nankotsu029.landformcraft.core.GenerationApplicationService;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.GenerationOutcome;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletionException;

/** Offline entry point for schema-validated Phase 1 preview generation. */
public final class LandformCraftCli {
    private LandformCraftCli() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int run(String[] args, PrintStream standardOutput, PrintStream errorOutput) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(standardOutput, "standardOutput");
        Objects.requireNonNull(errorOutput, "errorOutput");
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printHelp(standardOutput);
            return 0;
        }

        if ("generate".equals(args[0])) {
            return generate(args, standardOutput, errorOutput);
        }

        errorOutput.println("Unknown command: " + args[0]);
        errorOutput.println("Run with --help for usage.");
        return 2;
    }

    private static int generate(String[] args, PrintStream standardOutput, PrintStream errorOutput) {
        if (args.length < 3 || args.length > 5) {
            errorOutput.println("Usage: generate <request.yml> <terrain-intent.json> [output-directory] [candidate-index]");
            return 2;
        }
        Path request = Path.of(args[1]);
        Path intent = Path.of(args[2]);
        Path output = args.length >= 4 ? Path.of(args[3]) : Path.of("build", "landformcraft-preview");
        int candidateIndex;
        try {
            candidateIndex = args.length == 5 ? Integer.parseInt(args[4]) : 0;
        } catch (NumberFormatException exception) {
            errorOutput.println("candidate-index must be an integer");
            return 2;
        }

        int parallelism = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 8));
        try (GenerationExecutors executors = GenerationExecutors.createDefault(parallelism)) {
            GenerationApplicationService service = new GenerationApplicationService(executors);
            GenerationOutcome outcome = service.generatePreview(request, intent, output, candidateIndex).join();
            standardOutput.println("Generated Phase 1 terrain preview");
            standardOutput.println("checksum: " + outcome.terrainPlan().checksum());
            standardOutput.println("tiles: " + outcome.terrainPlan().tiles().size());
            standardOutput.println("generationMillis: " + outcome.metrics().generationMillis());
            standardOutput.println("estimatedPeakBytes: " + outcome.metrics().estimatedPeakWorkingBytes());
            standardOutput.println("valid: " + outcome.validation().isValid());
            standardOutput.println("output: " + outcome.outputDirectory());
            return outcome.validation().isValid() ? 0 : 1;
        } catch (CompletionException | IllegalArgumentException exception) {
            Throwable cause = exception instanceof CompletionException && exception.getCause() != null
                    ? exception.getCause() : exception;
            errorOutput.println("Generation failed: " + cause.getMessage());
            return 1;
        }
    }

    private static void printHelp(PrintStream output) {
        output.println("LandformCraft CLI");
        output.println("  generate <request.yml> <terrain-intent.json> [output-directory] [candidate-index]");
        output.println("    Validates the inputs and writes deterministic Phase 1 PNG previews.");
    }
}
