package work.zing.ergpower.pm5.tools;

import java.nio.file.Path;

import work.zing.ergpower.pm5.source.ReplayPm5Source;
import work.zing.ergpower.pm5.storage.SessionMeta;
import work.zing.ergpower.pm5.storage.SessionStorage;

/**
 * Dev tool: replay a captured raw-frame NDJSON through the decoder and write a session folder.
 *
 * <pre>{@code
 * java -cp <cp> work.zing.ergpower.pm5.tools.ReplayToStorage <capture.ndjson> [outputDir]
 * }</pre>
 *
 * Useful for eyeballing the stored layout and for regenerating a session from the reference fixture.
 */
public final class ReplayToStorage {

    private ReplayToStorage() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: ReplayToStorage <capture.ndjson> [outputDir]");
            System.exit(2);
        }
        Path capture = Path.of(args[0]);
        Path outDir = Path.of(args.length > 1 ? args[1] : "sessions");
        String name = capture.getFileName().toString().replaceFirst("\\.ndjson$", "");
        Path session = outDir.resolve("replay-" + name);

        SessionStorage.store(
                new ReplayPm5Source(capture),
                session,
                SessionMeta.of("replay:" + capture.getFileName()));

        System.out.println("wrote session -> " + session.toAbsolutePath());
    }
}
