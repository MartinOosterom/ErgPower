package work.zing.ergpower.pm5.source;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the native BLE bridge executable for the current platform. Binaries are bundled on the
 * classpath under {@code native/<os>-<arch>/ergpower-bridge[.exe]} (built by the Maven {@code cargo}
 * step); the matching one is extracted to a temp file and marked executable on first use. A configured
 * override path ({@code ergpower.ble.bridge.binary}) bypasses bundling entirely.
 *
 * <p>v1 ships macOS only, but selection is by {@code os.name}/{@code os.arch} from the {@code
 * native/<os>-<arch>/} layout, so adding Linux/Windows later is a binary-only change (no code here).
 */
public final class BridgeBinary {

    private static final ConcurrentHashMap<String, Path> EXTRACTED = new ConcurrentHashMap<>();

    private BridgeBinary() {
    }

    /** The executable to launch: the configured override if set, else the bundled binary for this host. */
    public static Path resolve(String override) {
        if (override != null && !override.isBlank()) {
            Path p = Path.of(override);
            if (!Files.isExecutable(p)) {
                throw new IllegalStateException("configured bridge binary is missing or not executable: " + p);
            }
            return p;
        }
        String classifier = classifier();
        String resource = "native/" + classifier + "/" + binaryName();
        return EXTRACTED.computeIfAbsent(classifier, c -> extract(resource, c));
    }

    /** e.g. {@code darwin-arm64}, {@code darwin-x64}, {@code linux-x64}, {@code windows-x64}. */
    public static String classifier() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String o = (os.contains("mac") || os.contains("darwin")) ? "darwin"
                : os.contains("win") ? "windows"
                : os.contains("nux") ? "linux"
                : "unknown";
        String a = (arch.equals("aarch64") || arch.equals("arm64")) ? "arm64"
                : arch.contains("64") ? "x64"
                : "x86";
        return o + "-" + a;
    }

    private static String binaryName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "ergpower-bridge.exe"
                : "ergpower-bridge";
    }

    private static Path extract(String resource, String classifier) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = cl.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("no bundled BLE bridge for this platform (" + classifier
                        + "). Build it with `cargo build --release --manifest-path bridge/Cargo.toml`,"
                        + " or set ergpower.ble.bridge.binary to a prebuilt executable.");
            }
            Path tmp = Files.createTempFile("ergpower-bridge-" + classifier + "-", suffix());
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            tmp.toFile().setExecutable(true, false);
            tmp.toFile().deleteOnExit();
            return tmp;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to extract bundled bridge binary", e);
        }
    }

    private static String suffix() {
        return binaryName().endsWith(".exe") ? ".exe" : "";
    }
}
