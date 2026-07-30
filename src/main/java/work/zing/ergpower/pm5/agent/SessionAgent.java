package work.zing.ergpower.pm5.agent;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

import work.zing.ergpower.api.model.ChatMessage;
import work.zing.ergpower.pm5.config.AiProperties;

/**
 * The optional interactive session agent (change {@code session-agent}): a Spring AI {@link ChatClient}
 * with read-only tools ({@link SessionTools}, {@link CrossSessionTools}) that answers questions about a
 * session — and, via the cross-session tools, across sessions — grounded in the stored data.
 *
 * <p>Available only when a chat provider is configured (a {@link ChatModel} bean exists). The
 * conversation is client-held (the transcript is sent each turn); nothing is persisted, keeping the
 * live API read-only. Answers stream token-by-token.
 */
@Component
public class SessionAgent {

    static final String SYSTEM = """
            You are a rowing analyst assistant answering questions about the athlete's erg data. You have
            read-only TOOLS to fetch exactly what a question needs: a session's overview and force-curve
            technique analysis, a metrics window, the strokes in a window, and a single stroke's force
            curve. ALWAYS answer from the tools — call them rather than guess, and do not invent numbers
            you did not retrieve. If you are inferring beyond the data, say so. Be concise and concrete,
            and refer to the athlete's own numbers.""";

    static final String SET_SYSTEM = """
            You are a rowing analyst assistant answering questions ACROSS a set of the athlete's sessions.
            You have read-only TOOLS: per-session overview/analysis/metrics/strokes/forceCurve, plus
            cross-session listing and comparison. ALWAYS answer from the tools — call them rather than
            guess, and do not invent numbers you did not retrieve. Focus on the SELECTED sessions listed
            below; use compareSessions across them for comparative or progress questions. Be concise and
            concrete, and refer to the athlete's own numbers.""";

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final SessionTools sessionTools;
    private final CrossSessionTools crossSessionTools;
    private final AiProperties ai;

    public SessionAgent(ObjectProvider<ChatModel> chatModelProvider, SessionTools sessionTools,
            CrossSessionTools crossSessionTools, AiProperties ai) {
        this.chatModelProvider = chatModelProvider;
        this.sessionTools = sessionTools;
        this.crossSessionTools = crossSessionTools;
        this.ai = ai;
    }

    /** Presentation instructions appended to the agent's system prompt: Markdown output + optional language. */
    private String presentation() {
        String md = "\n\nFormat your answer as Markdown (headings, tables, lists, and emphasis where they aid clarity).";
        String lang = ai.languageOrNull();
        return lang == null ? md : md + "\nRespond in " + lang
                + ". Translate only your prose; keep the metric names and numeric values exactly as given.";
    }

    /** Whether a chat provider is configured (so the agent is available). */
    public boolean available() {
        return chatModelProvider.getIfAvailable() != null;
    }

    /**
     * Stream an answer about a SINGLE session (the analysis view). The agent gets only the session tools,
     * so it cannot reach other sessions. Empty stream when no provider is configured.
     */
    public Flux<String> chat(String anchorId, List<ChatMessage> transcript) {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model == null) {
            return Flux.empty();
        }
        String system = SYSTEM + "\n\nThe session being viewed is '" + anchorId + "'. Answer about it."
                + presentation();
        return ChatClient.create(model).prompt()
                .system(system)
                .messages(toMessages(transcript))
                .tools(sessionTools)
                .stream()
                .content();
    }

    /**
     * Stream an answer scoped to a SELECTED SET of sessions (the progress dashboard). The agent gets the
     * cross-session tools too, focused on the named set. Empty stream when no provider is configured.
     */
    public Flux<String> chatOverSet(List<String> sessionIds, List<ChatMessage> transcript) {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model == null) {
            return Flux.empty();
        }
        String system = SET_SYSTEM + "\n\nThe selected sessions are: " + String.join(", ", sessionIds) + "."
                + presentation();
        return ChatClient.create(model).prompt()
                .system(system)
                .messages(toMessages(transcript))
                .tools(sessionTools, crossSessionTools)
                .stream()
                .content();
    }

    private static List<Message> toMessages(List<ChatMessage> transcript) {
        List<Message> messages = new ArrayList<>();
        for (ChatMessage m : transcript) {
            if (m.getRole() == ChatMessage.RoleEnum.ASSISTANT) {
                messages.add(new AssistantMessage(m.getContent()));
            } else {
                messages.add(new UserMessage(m.getContent()));
            }
        }
        return messages;
    }
}
