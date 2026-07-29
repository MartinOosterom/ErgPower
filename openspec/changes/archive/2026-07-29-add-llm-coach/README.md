# add-llm-coach

An **optional, pluggable** LLM layer on top of the deterministic technique analysis: when a provider is
configured (Ollama by default, swappable for OpenAI/Anthropic/…), an "AI coach" turns the analysis's
features + Kleshnev scores + flags into prioritized, plain-language coaching. It consumes the analysis
JSON — never raw curves — so it stays grounded, and everything works fully without it.
