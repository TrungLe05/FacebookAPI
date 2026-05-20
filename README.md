# 🚀 Facebook API — Hệ Thống Microservices Tự Động Hoá Tương Tác Facebook

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.0-6DB33F?logo=spring" />
  <img src="https://img.shields.io/badge/Apache%20Kafka-7.5.0-231F20?logo=apachekafka" />
  <img src="https://img.shields.io/badge/Redis-7-DC382D?logo=redis" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-336791?logo=postgresql" />
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker" />
  <img src="https://img.shields.io/badge/AI-Groq%20%7C%20Gemini-4285F4?logo=google" />
</p>

---

## 📖 Giới Thiệu

**FB-API** là một hệ thống **Microservices** được xây dựng bằng **Spring Boot** nhằm tự động hoá toàn bộ vòng đời tương tác với **Facebook Graph API**. Hệ thống có khả năng:

- 📥 **Nhận và xử lý webhook** từ Facebook (comments, messages)
- 🤖 **Phân tích nội dung bằng AI** (Groq Llama-3) để phân loại intent và sentiment
- 🛡️ **Phát hiện spam** bằng rule-based engine (blacklist, URL detection, repeat tracking)
- ⚡ **Ra quyết định tự động**: trả lời, ẩn comment, hoặc đưa vào hàng chờ
- 🔁 **Retry tự động** với Exponential Backoff khi gọi Facebook API thất bại
- 📊 **Monitoring & Alerting** qua Prometheus, AlertManager, Slack và Gmail

---

## 🏗️ Kiến Trúc Hệ Thống

```
Facebook Graph API / Webhook
         │
         ▼
┌────────────────────┐
│   Webhook Service  │  Port 8080
│  (Thu thập sự kiện)│
└────────┬───────────┘
         │ Publish → Kafka: raw_events
         ▼
┌────────────────────┐
│    Core Service    │  Processing Pipeline
│  ┌──────────────┐  │
│  │ SpamDetector │  │  Step 1: Phát hiện spam (rule-based)
│  ├──────────────┤  │
│  │ AiClassifier │  │  Step 2: Phân loại intent bằng AI (Groq)
│  ├──────────────┤  │
│  │DecisionEngine│  │  Step 3: Ra quyết định
│  └──────────────┘  │
└────────┬───────────┘
         │ Publish → Kafka: reply_commands
         ▼
┌────────────────────┐
│    Backend API     │  Port 3000
│ (Thực thi Facebook │
│  Graph API calls)  │
└────────┬───────────┘
         │ On failure → Kafka: send_failed
         ▼
┌────────────────────┐
│   Retry Service    │
│  (Exponential      │
│   Backoff Retry)   │
└────────┬───────────┘
         │ → Kafka: send_retry | dead_letter
         └──────────────────────────────────┐
                                            ▼
                              ┌─────────────────────────┐
                              │  Prometheus + AlertMgr   │
                              │  (Monitoring & Alerting) │
                              └─────────────────────────┘
```

### Luồng Xử Lý Event (End-to-End)

```
User comment/message trên Facebook
        │
        ▼
[Webhook Service] Verify HMAC-SHA256 signature → Normalize event → Kafka(raw_events)
        │
        ▼
[Core Service] Kafka Consumer:
   1. SpamDetector: kiểm tra blacklist → URL → nội dung lặp (Redis)
   2. AiClassifier: Groq Llama-3 (primary) → Keyword (fallback)
   3. DecisionEngine: AUTO_REPLY | HIDE | QUEUE_REVIEW | BLACKLIST_AND_HIDE | IGNORE
   4. ReplyCommandPublisher: publish ReplyCommand → Kafka(reply_commands)
        │
        ▼
[Backend API] Kafka Consumer(reply_commands):
   - Idempotency check (PostgreSQL)
   - Gọi Facebook Graph API: replyToComment / hideComment / sendMessage
   - Thành công → markProcessed
   - Thất bại (tạm thời) → Kafka(send_failed)
   - Thất bại (token hết hạn) → Kafka(dead_letter)
        │
        ▼
[Retry Service] Kafka Consumer(send_failed):
   - Exponential Backoff (1s → 2s → 4s)
   - Tối đa 3 lần retry → dead_letter nếu vẫn thất bại
        │
        ▼
[Prometheus + AlertManager] Giám sát dead_letter, send_failed, consumer lag
   → Gửi alert qua Slack + Email
```

---

## 📦 Danh Sách Services

| Service | Port | Mô tả | Công nghệ chính |
|---------|------|-------|-----------------|
| [webhook-service](./webhook-service/README.md) | 3001 | Nhận & chuẩn hoá webhook từ Facebook | Spring Boot, Kafka |
| [core-service](./core-service/README.md) | 3002 | Pipeline xử lý AI & ra quyết định | Spring Boot, Redis, Kafka, Groq |
| [backend-api](./backend-api/README.md) | 3000 | Thực thi Facebook Graph API, REST API | Spring Boot, PostgreSQL, Kafka, Resilience4j |
| [retry-service](./retry-service/README.md) | 3003 | Retry với Exponential Backoff | Spring Boot, Kafka |

---

## 🔧 Kafka Topics

| Topic | Producer       | Consumer | Mô tả |
|-------|----------------|----------|-------|
| `raw_events` | webhook-service | core-service | Sự kiện thô từ Facebook |
| `reply_commands` | core-service   | backend-api | Lệnh thực thi (reply/hide/blacklist) |
| `send_failed` | backend-api    | retry-service | Thất bại tạm thời, cần retry |
| `send_retry` | retry-service  | backend-api | Retry payload |
| `dead_letter` | backend-api, retry-service | Monitoring | Thất bại vĩnh viễn (max retries / token expired) |
| `dead_letter_events` | retry-service  | — | Event không xử lý được ở pipeline |

---

## 🏛️ Infrastructure

| Service | Port | Mô tả |
|---------|------|-------|
| Kafka | 9092 | Message broker |
| Zookeeper | 2181 | Kafka coordinator |
| Kafka UI | 8081 | Web UI quản lý Kafka topics |
| Redis | 6379 | Cache, idempotency, event status, spam tracking |
| RedisInsight | 5540 | Web UI quản lý Redis |
| PostgreSQL | 5432 | Lưu trữ idempotency keys |
| pgAdmin | 8080 | Web UI quản lý PostgreSQL |
| Prometheus | 9090 | Monitoring, scrape metrics |
| AlertManager | 9093 | Routing alerts → Slack + Email |
| Kafka Exporter | 9308 | Export Kafka metrics cho Prometheus |

---

## 🚀 Hướng Dẫn Cài Đặt & Khởi Chạy

### Yêu Cầu Hệ Thống

- **Java 21+**
- **Maven 3.8+**
- **Docker Desktop** (với Docker Compose)
- **Tài khoản Facebook Developer** với Facebook App & Page Access Token
- **Ngrok** hoặc domain công khai để expose webhook

### Bước 1: Cấu Hình Biến Môi Trường

Tạo file `.env` ở thư mục gốc:

```env
# PostgreSQL
POSTGRES_USER=admin
POSTGRES_PASSWORD=admin
POSTGRES_DB=BlogAI

# pgAdmin
PGADMIN_EMAIL=admin@admin.com
PGADMIN_PASSWORD=admin

```

Cấu hình `.env` cho từng service:

```env
# backend-api/.env
PAGE_ID=your_facebook_page_id
PAGE_ACCESS_TOKEN=your_page_access_token
DATABASE_USERNAME=admin
DATABASE_PASSWORD=admin

# webhook-service/.env
FACEBOOK_APP_SECRET=your_app_secret
FACEBOOK_VERIFY_TOKEN=your_custom_verify_token

# core-service/.env
PAGE_ID=your_facebook_page_id
PAGE_ACCESS_TOKEN=your_page_access_token
GROQ_API_KEY=your_groq_api_key
```

### Bước 2: Khởi Động Infrastructure

```bash
# Khởi động tất cả containers
docker-compose up -d

# Kiểm tra trạng thái
docker-compose ps
```

### Bước 3: Build và Chạy Các Services

```bash
# Terminal 1 — Webhook Service
cd webhook-service
./mvnw spring-boot:run

# Terminal 2 — Core Service
cd core-service
./mvnw spring-boot:run

# Terminal 3 — Backend API
cd backend-api
./mvnw spring-boot:run

# Terminal 4 — Retry Service
cd retry-service
./mvnw spring-boot:run
```

### Bước 4: Cấu Hình Facebook Webhook

1. Dùng Ngrok để expose Webhook Service:
   ```bash
   ngrok http 3001
   ```

2. Vào **Facebook Developer Console** → Cấu hình Webhook:
   - **Callback URL**: `https://your-ngrok-url/webhook`
   - **Verify Token**: Giá trị `FACEBOOK_VERIFY_TOKEN` trong `.env`
   - **Subscriptions**: `feed`, `messages`

---

## 📊 Monitoring

| Dashboard | URL |
|-----------|-----|
| Kafka UI | http://localhost:8081 |
| Prometheus | http://localhost:9090 |
| AlertManager | http://localhost:9093 |
| pgAdmin | http://localhost:8080 |
| RedisInsight | http://localhost:5540 |
| Backend API Health | http://localhost:3000/actuator/health |
| Circuit Breaker Status | http://localhost:3000/actuator/circuitbreakers |

### Alert Rules

Hệ thống có 3 loại cảnh báo:
- **🔴 Critical**: Message mới vào `dead_letter` → Alert ngay qua Slack + Email
- **🟡 Warning**: `send_failed` topic có message → retry đang diễn ra
- **🟡 Warning**: Consumer lag > 100 → hệ thống đang quá tải

---

## 🛡️ Các Tính Năng Nổi Bật

### Circuit Breaker (Resilience4j)
- Tự động ngắt kết nối khi 60% request thất bại
- Sliding window: 12 calls
- Thời gian chờ OPEN → HALF-OPEN: 30 giây

### Idempotency
- Mỗi `ReplyCommand` có `commandId` duy nhất
- Backend API kiểm tra PostgreSQL trước khi gọi Facebook API
- Đảm bảo không gửi duplicate reply/hide

### AI Fallback Chain
```
Groq Llama-3.1 → Keyword-based Fallback
```

### Spam Detection (không cần AI)
1. **Blacklist check**: Redis-based sender blacklist
2. **URL detection**: Regex pattern cho link rút gọn, HTTP/HTTPS
3. **Malicious content**: Keyword matching (scam, casino, cờ bạc...)
4. **Repeat tracking**: SHA-256 hash + Redis counter (threshold 3 lần/24h)

---

## 📐 Kiến Trúc Code (Backend API)

Backend API được xây dựng theo **Hexagonal Architecture** (Ports & Adapters):

```
presentation/         ← Controllers, DTOs (HTTP layer)
application/          ← Use Cases, Services (Business logic)
domain/               ← Models, Ports/Interfaces (Core)
infrastructure/       ← Adapters, Config, Kafka, DB (External systems)
```

---

## 👨‍💻 Tác Giả

Dự án được phát triển như một bài tập thực hành về **Microservices**, **Event-Driven Architecture**, và **AI Integration** với Spring Boot.

---

## 📄 Tài Liệu Tham Khảo

- [Facebook Graph API Docs](https://developers.facebook.com/docs/graph-api/)
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Groq API Reference](https://console.groq.com/docs/api-reference)
- [Gemini API Reference](https://ai.google.dev/api/)
- [Resilience4j Documentation](https://resilience4j.readme.io/docs)
