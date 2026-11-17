import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class validator {
    private static final int COMPILE_TIMEOUT_SECONDS = 30;
    private static final int RUN_TIMEOUT_SECONDS = 5;

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 3) {
            System.err.println("Usage: java validator <submission.java> <01.in> <01.out>");
            System.exit(2);
        }

        Path submission = Path.of(args[0]).toAbsolutePath();
        Path input = Path.of(args[1]).toAbsolutePath();
        Path expected = Path.of(args[2]).toAbsolutePath();
        for (Path file : new Path[] {submission, input, expected}) {
            if (!Files.isRegularFile(file)) {
                System.err.println("File not found: " + file);
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

        Path actual = work.resolve("actual.out");
        Path errors = work.resolve("runtime.err");
        Process program = new ProcessBuilder("java", "-cp", work.toString(), "Main")
                .directory(work.toFile())
                .redirectInput(input.toFile())
                .redirectOutput(actual.toFile())
                .redirectError(errors.toFile())
                .start();
        if (!waitFor(program, RUN_TIMEOUT_SECONDS)) {
            System.err.println("Time limit exceeded");
            System.exit(1);
        }
        if (program.exitValue() != 0) {
            System.err.println("Runtime error (exit " + program.exitValue() + "):");
            System.err.print(Files.readString(errors));
            System.exit(1);
        }

        String expectedOutput = normalize(Files.readString(expected, StandardCharsets.UTF_8));
        String actualOutput = normalize(Files.readString(actual, StandardCharsets.UTF_8));
        if (actualOutput.equals(expectedOutput)) {
            System.out.println("Accepted");
        } else {
            System.out.println("Wrong answer");
            System.out.println("Expected: " + expectedOutput);
            System.out.println("Actual:   " + actualOutput);
            System.exit(1);
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
