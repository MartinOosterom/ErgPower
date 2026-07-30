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
            technique analysis, a metrics window, the strokes in a window, a single stroke's force curve,
            and cross-session listing/compare. ALWAYS answer from the tools — call them rather than guess,
            and do not invent numbers you did not retrieve. If you are inferring beyond the data, say so.
            Prefer the session currently being viewed; use the cross-session tools only when the question
            is comparative or historical. Be concise and concrete, and refer to the athlete's own numbers.""";

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final SessionTools sessionTools;
    private final CrossSessionTools crossSessionTools;

    public SessionAgent(ObjectProvider<ChatModel> chatModelProvider, SessionTools sessionTools,
            CrossSessionTools crossSessionTools) {
        this.chatModelProvider = chatModelProvider;
        this.sessionTools = sessionTools;
        this.crossSessionTools = crossSessionTools;
    }

    /** Whether a chat provider is configured (so the agent is available). */
    public boolean available() {
        return chatModelProvider.getIfAvailable() != null;
    }

    /**
     * Stream an answer for the conversation, anchored to {@code anchorId}. The transcript's last message
     * is the new user question. Returns an empty stream if no provider is configured.
     */
    public Flux<String> chat(String anchorId, List<ChatMessage> transcript) {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model == null) {
            return Flux.empty();
        }
        List<Message> messages = new ArrayList<>();
        for (ChatMessage m : transcript) {
            if (m.getRole() == ChatMessage.RoleEnum.ASSISTANT) {
                messages.add(new AssistantMessage(m.getContent()));
            } else {
                messages.add(new UserMessage(m.getContent()));
            }
        }
        String system = SYSTEM + "\n\nThe session currently being viewed is '" + anchorId + "'.";
        return ChatClient.create(model).prompt()
                .system(system)
                .messages(messages)
                .tools(sessionTools, crossSessionTools)
                .stream()
                .content();
    }
}
