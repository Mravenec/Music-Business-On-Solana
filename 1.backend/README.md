# 1.backend — Spring Boot API

`eh8s/` is a Maven project: Spring Boot 3.5 · Java 21 · JOOQ · MariaDB · JWT (HS256). No JPA.

## Layers

```text
database/jooq/      generated JOOQ tables and POJOs (one package per MariaDB schema); POJOs are the only data types
repository/         I*Repository interfaces + JOOQ implementations
service/            I*Service interfaces + implementations
  solana/           instruction builders, PDA derivation, RPC client, transaction verifier
  claude/           Claude client (NEXUS scoring, owner digest)
  course/           course access rules and video-link parsing (YouTube, Vimeo, Bunny, Cloudflare)
controller/         I*Controller interfaces + REST controllers
config/             security (JWT filter, public routes), CORS
```

Every layer depends on the interface of the next one, and every public type and method has Javadoc.

## Run and test

```bash
cd 1.backend/eh8s
cp .env.example .env          # JWT_SECRET, optional ANTHROPIC_API_KEY and Slack settings
mvn test                      # 164 unit tests
mvn spring-boot:run           # http://127.0.0.1:8080  (GET /health is public)
```

`src/test/java/com/eh8s/eh8s/http/` holds 34 `.http` contract files (IntelliJ / VS Code REST Client): each protected route returns 401 without a token and 2xx with a wallet-signed session.

Sign-in: `POST /api/session/challenge` returns a one-time nonce; the wallet signs `Sign in to EH8S`; `POST /api/session/wallet` verifies the ed25519 signature and returns the JWT.

Payments are never sent by the server. The client signs each transaction; the backend re-reads the confirmed signature from DevNet RPC and checks the program id, accounts and amounts before recording it.

Diagram: [`3.diagrams/1.backend/layers.puml`](../3.diagrams/1.backend/layers.puml).
