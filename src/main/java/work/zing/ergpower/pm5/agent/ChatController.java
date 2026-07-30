package work.zing.ergpower.pm5.agent;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import work.zing.ergpower.api.model.ChatRequest;
import work.zing.ergpower.api.model.SetChatRequest;

/**
 * The session agent's chat endpoint (change {@code session-agent}). Hand-written for SSE streaming:
 * {@code POST /sessions/{id}/chat} takes the client-held transcript and streams the answer token-by-token
 * ({@code token} events, then a {@code done} event). Anchored to {@code id}; the agent roams other
 * sessions via its tools. 409 when no provider is configured. A read — the tool work runs off the event
 * loop and nothing is persisted.
 */
@RestController
public class ChatController {

    private final SessionAgent agent;

    public ChatController(SessionAgent agent) {
        this.agent = agent;
    }

    @PostMapping(value = "/sessions/{id}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@PathVariable("id") String id, @RequestBody ChatRequest body) {
        if (!agent.available()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "no LLM provider configured");
        }
        List<work.zing.ergpower.api.model.ChatMessage> messages =
                body.getMessages() != null ? body.getMessages() : List.of();
        return agent.chat(id, messages)
                .map(token -> ServerSentEvent.builder(token).event("token").build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder("").event("done").build()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** Set-scoped chat for the progress dashboard: the agent roams the selected sessions via its tools. */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatOverSet(@RequestBody SetChatRequest body) {
        if (!agent.available()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "no LLM provider configured");
        }
        List<String> sessions = body.getSessions() != null ? body.getSessions() : List.of();
        List<work.zing.ergpower.api.model.ChatMessage> messages =
                body.getMessages() != null ? body.getMessages() : List.of();
        return agent.chatOverSet(sessions, messages)
                .map(token -> ServerSentEvent.builder(token).event("token").build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder("").event("done").build()))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
