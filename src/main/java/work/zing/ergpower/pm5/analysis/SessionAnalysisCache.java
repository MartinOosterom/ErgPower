package work.zing.ergpower.pm5.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.NoSuchElementException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import work.zing.ergpower.api.model.SessionAnalysis;
import work.zing.ergpower.pm5.config.ErgPowerBleProperties;

/**
 * Caches each session's deterministic technique analysis so it is computed at most once per analyzer
 * version (change {@code cross-session-index}). The compact result — scores, feature stats, drift
 * trends, and flags, but <em>not</em> the heavy per-stroke curve arrays — is persisted as
 * {@code analysis.json} in the session folder, stamped with {@link TechniqueAnalyzer#ANALYZER_VERSION}.
 *
 * <p>On read the cache is used only when its stamp matches the current analyzer version; otherwise the
 * analysis is recomputed and rewritten. The cache is fully re-derivable from the stored session — delete
 * it and the next read reproduces it. It backs the cross-session index and the coach/agent, which need
 * the scores, not the curves (the chart view still computes the full analysis on demand).
 */
@Component
public class SessionAnalysisCache {

    static final String CACHE_FILE = "analysis.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path storageDir;
    private final TechniqueAnalyzer analyzer;

    @Autowired
    public SessionAnalysisCache(ErgPowerBleProperties ble, TechniqueAnalyzer analyzer) {
        this(Path.of(ble.storage().dir()), analyzer);
    }

    SessionAnalysisCache(Path storageDir, TechniqueAnalyzer analyzer) {
        this.storageDir = storageDir;
        this.analyzer = analyzer;
    }

    /**
     * The session's compact analysis (scores/features/trends/flags; curves omitted), served from the
     * cache when current, else computed and cached.
     *
     * @throws NoSuchElementException if the session does not exist
     * @throws IOException            if the stored data cannot be read
     */
    public SessionAnalysis scores(String id) throws IOException {
        Path dir = storageDir.resolve(id);
        if (!Files.isDirectory(dir)) {
            throw new NoSuchElementException(id);
        }
        Path cache = dir.resolve(CACHE_FILE);
        if (Files.exists(cache)) {
            JsonNode root = MAPPER.readTree(Files.readString(cache));
            if (root.path("analyzerVersion").asInt(-1) == TechniqueAnalyzer.ANALYZER_VERSION) {
                return MAPPER.treeToValue(root.get("analysis"), SessionAnalysis.class);
            }
        }
        SessionAnalysis compact = compact(analyzer.analyze(id));
        ObjectNode root = MAPPER.createObjectNode();
        root.put("analyzerVersion", TechniqueAnalyzer.ANALYZER_VERSION);
        root.set("analysis", MAPPER.valueToTree(compact));
        writeAtomic(cache, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(root));
        return compact;
    }

    /** Drop the heavy per-stroke arrays; the cache keeps only what cross-session consumers need. */
    private static SessionAnalysis compact(SessionAnalysis a) {
        return a.curves(null).meanCurve(null);
    }

    private static void writeAtomic(Path target, byte[] bytes) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
