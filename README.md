# Stock Assistant

Stock Assistant is an AI-powered investment assistant platform that combines market data, portfolio management, trade records, OCR import, AI analysis, and RAG-based knowledge retrieval.

## Tech Stack

- Frontend: React 19, Vite, Ant Design
- Backend: Java 21, Spring Boot, Maven Multi-Module
- AI Worker: Python, FastAPI
- Database: PostgreSQL, JSONB, pgvector
- Cache / Queue: Redis
- Infrastructure: Docker Compose

## Core Features

- Instrument master data, market quotes, and stock information integration
- Portfolio, position, and trade record management
- OCR `Draft -> Review -> Confirm` import workflow
- AI conversation, investment analysis, and SSE streaming responses
- RAG ingestion and query for document-based knowledge retrieval
- JWT authentication, refresh-session handling, and modular backend architecture

## Project Structure

```text
apps/
  frontend/     React frontend
  backend/      Spring Boot backend
  ai-worker/    FastAPI AI / OCR / RAG worker
docs/           Project design documents and ADRs
```

## Backend Modules

- `app-auth`: authentication and authorization
- `app-api`: REST API and SSE entry layer
- `app-portfolio`: portfolio and trade workflows
- `app-stocks`: market data and instrument services
- `app-ocr`: OCR job, draft, and confirm workflow
- `app-ai`: AI conversation and analysis logic
- `app-rag`: RAG document flow and AI worker integration
- `app-files`: file storage abstraction layer
- `app-persistence`: JPA, Flyway, and database schema
- `app-bootstrap`: application assembly and startup

## Local Development

### Frontend

```bash
cd apps/frontend
npm install
npm run dev
```

### Backend

```bash
cd apps/backend/invest-assistant-backend
./mvnw spring-boot:run
```

### AI Worker

```bash
cd apps/ai-worker
uv pip install --python .venv\Scripts\python.exe -e .
.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
```

### Docker Compose

```bash
cd apps/backend/invest-assistant-backend
docker compose up -d
```

## Documentation

- Project design documents are located in `docs/`
- ADRs are located in `docs/adr/`
