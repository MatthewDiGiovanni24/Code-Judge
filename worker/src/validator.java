import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class validator {
    private static final int COMPILE_TIMEOUT_SECONDS = 30;
    private static final int RUN_TIMEOUT_SECONDS = 5;

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 2) {
            System.err.println("Usage: java validator <submission.java> <problem-folder>");
            System.exit(2);
        }

        Path submission = Path.of(args[0]).toAbsolutePath();
        Path problem = Path.of(args[1]).toAbsolutePath();
        if (!Files.isRegularFile(submission) || !Files.isDirectory(problem)) {
            System.err.println("Submission file or problem folder not found");
            System.exit(2);
        }

        List<Path> inputs;
        try (var files = Files.list(problem)) {
            inputs = files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".in"))
                    .sorted()
                    .toList();
        }
        if (inputs.isEmpty()) {
            System.err.println("No .in test files found in " + problem);
            System.exit(2);
        }
        for (Path input : inputs) {
            Path expected = expectedFor(input);
            if (!Files.isRegularFile(expected)) {
                System.err.println("Expected output file not found: " + expected);
                System.exit(2);
            }
        }

        Path work = Path.of("work").toAbsolutePath();
        Files.createDirectories(work);
        Path source = work.resolve("Main.java");
        if (!submission.equals(source)) {
            Files.copy(submission, source, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        Path compilerOutput = work.resolve("compile.log");
        Process compiler = new ProcessBuilder("javac", "-encoding", "UTF-8", "-d",
                work.toString(), source.toString())
                .redirectErrorStream(true)
                .redirectOutput(compilerOutput.toFile())
                .start();
        if (!waitFor(compiler, COMPILE_TIMEOUT_SECONDS)) {
            System.err.println("Compilation timed out");
            System.exit(1);
        }
        if (compiler.exitValue() != 0) {
            System.err.println("Compilation error:");
            System.err.print(Files.readString(compilerOutput));
            System.exit(1);
        }

        int passed = 0;
        for (Path input : inputs) {
            System.out.print(input.getFileName() + ": ");
            if (runTest(work, input, expectedFor(input))) {
                passed++;
            }
        }
        System.out.println("Passed " + passed + "/" + inputs.size() + " tests");
        if (passed != inputs.size()) {
            System.exit(1);
        }
    }

    private static Path expectedFor(Path input) {
        String name = input.getFileName().toString();
        return input.resolveSibling(name.substring(0, name.length() - 3) + ".out");
    }

    private static boolean runTest(Path work, Path input, Path expected)
            throws IOException, InterruptedException {
        String name = input.getFileName().toString();
        Path actual = work.resolve(name + ".actual.out");
        Path errors = work.resolve(name + ".runtime.err");
        Process program = new ProcessBuilder("java", "-cp", work.toString(), "Main")
                .directory(work.toFile())
                .redirectInput(input.toFile())
                .redirectOutput(actual.toFile())
                .redirectError(errors.toFile())
                .start();
        if (!waitFor(program, RUN_TIMEOUT_SECONDS)) {
            System.out.println("Time limit exceeded");
            return false;
        }
        if (program.exitValue() != 0) {
            System.out.println("Runtime error (exit " + program.exitValue() + "):");
            System.err.print(Files.readString(errors));
            return false;
        }

        String expectedOutput = normalize(Files.readString(expected, StandardCharsets.UTF_8));
        String actualOutput = normalize(Files.readString(actual, StandardCharsets.UTF_8));
        if (actualOutput.equals(expectedOutput)) {
            System.out.println("Accepted");
            return true;
        } else {
            System.out.println("Wrong answer");
            System.out.println("Expected: " + expectedOutput);
            System.out.println("Actual:   " + actualOutput);
            return false;
        }
    }

    private static boolean waitFor(Process process, int seconds) throws InterruptedException {
        if (process.waitFor(seconds, TimeUnit.SECONDS)) {
            return true;
        }
        process.destroyForcibly();
        process.waitFor();
        return false;
    }

    // Compare tokens so line endings and extra whitespace do not affect the verdict.
    private static String normalize(String output) {
        return output.trim().replaceAll("\\s+", " ");
    }
}
