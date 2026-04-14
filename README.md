# Search & AI Extraction Service

This microservice is a core component of the Exam Bank ecosystem. It serves a dual purpose:
1. **AI Background Worker:** Asynchronously processes uploaded exam files (PDF, Word, Images), extracts raw text using Apache Tika, and uses Gemini AI to convert the content into structured JSON (Questions, Options, Answers, Explanations).
2. **Search Engine:** Indexes exam data into Elasticsearch to provide lightning-fast, full-text search capabilities for the frontend.

## Tech Stack
* **Framework:** Spring Boot 3.2.x (Java 21)
* **Message Broker:** RabbitMQ
* **Search Engine:** Elasticsearch 8.x
* **Object Storage:** MinIO
* **AI Integration:** Spring AI (Google Gemini Vertex AI)
* **File Parsing:** Apache Tika

## Architecture Overview
This service implements a CQRS-inspired, event-driven architecture:
* Listens to `exam.source.uploaded` events from `exam_service`.
* Downloads files securely from MinIO.
* Calls Gemini AI for extraction.
* Publishes `search.ai.extracted` events back to RabbitMQ for data persistence.
* Syncs data to Elasticsearch for querying.

## Prerequisites
* Docker (for running local dependencies like RabbitMQ, MinIO, Elasticsearch)
* Java 21+
* Maven
* A valid Google Gemini API Key

## Environment Variables
To run this service, you need to set the following environment variables (or rely on the default fallbacks in `application.properties`):

| Variable | Description |
|---|---|
| `SERVER_PORT` | Application port (Default: 8083) |
| `RABBITMQ_HOST` | RabbitMQ host |
| `RABBITMQ_USERNAME` | RabbitMQ username |
| `RABBITMQ_PASSWORD` | RabbitMQ password |
| `ELASTICSEARCH_URL` | Elasticsearch URL (Default: http://localhost:9200) |
| `MINIO_URL` | MinIO URL |
| `MINIO_ACCESS_KEY` | MinIO access key |
| `MINIO_SECRET_KEY` | MinIO secret key |
| `GEMINI_API_KEY` | **Required:** Your Google AI Studio API key |

## Getting Started
1. Ensure RabbitMQ, MinIO, and Elasticsearch containers are running.
2. Build the project:
   ```bash
   mvn clean install