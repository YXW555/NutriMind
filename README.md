# NutriMind

## Local configuration

Detailed onboarding and collaboration guide:

- [TEAM_ONBOARDING.md](/d:/workspace/NutriMind/docs/TEAM_ONBOARDING.md)

1. Copy `.env.example` to `.env`.
2. Fill in your own local values for `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, and `APP_JWT_SECRET`.
3. If you need machine-specific overrides for the discovery startup script, add a `.env.local` file. The script loads `.env` first and `.env.local` second.

`APP_RAG_QWEN_API_KEY` is optional. If it is empty, `meal-service` still starts, but it skips Milvus bootstrap and falls back to local knowledge retrieval.

The default project setup uses Docker MySQL on `localhost:3307`.
If you want to use a MySQL installed directly on your computer instead, change `MYSQL_PORT` in `.env` to `3306` and do not start the Docker `mysql` service.

## Startup

- Start only MySQL: `docker compose -f docker-compose.dev.yml up -d mysql`
- Start all local infra: `docker compose -f docker-compose.dev.yml up -d`
- Start discovery-mode services: `powershell -ExecutionPolicy Bypass -File .\scripts\start-local-discovery.ps1`
- Check running containers: `docker ps`
- Check MySQL logs: `docker logs nutrimind-mysql --tail 50`
- Stop only MySQL: `docker compose -f docker-compose.dev.yml stop mysql`

Docker Compose reads the root `.env` file automatically. The discovery startup script also reads `.env` and `.env.local`, validates required values, prints the MySQL target it will use, and uses `http://localhost:19530` as the default Milvus URI for local processes.

## Team workflow

- Do not commit `.env` or `.env.local`.
- Commit only `.env.example` with placeholder values.
- Share real secrets through a password manager or another private channel, not through Git.
- Prefer personal third-party API keys when possible. If the team must share one key, rotate it whenever the team membership changes.
