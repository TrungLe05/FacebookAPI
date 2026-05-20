# 🧠 Core Service

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.0-6DB33F?logo=spring" />
  <img src="https://img.shields.io/badge/Redis-7-DC382D?logo=redis" />
  <img src="https://img.shields.io/badge/Kafka-Consumer%20%2B%20Producer-231F20?logo=apachekafka" />
</p>

---

## 🎯 Mục Đích

**Core Service** là **trái tim của hệ thống** — nơi diễn ra toàn bộ logic nghiệp vụ thông minh. Service này:

1. **Consume sự kiện** từ Kafka topic `raw_events` (do Webhook Service gửi)
2. **Phát hiện spam** bằng rule-based engine (không cần AI, nhanh & tiết kiệm)
3. **Phân loại nội dung bằng AI** — intent (hỏi giá, khiếu nại, khen...) và sentiment
4. **Ra quyết định** dựa trên ma trận quy tắc kết hợp spam result + AI result
5. **Publish lệnh thực thi** (`ReplyCommand`) lên Kafka → Backend API thực hiện
6. **Theo dõi trạng thái sự kiện** trong Redis (RECEIVED → PROCESSED/FAILED/DEAD_LETTER)

---

## 🏗️ Vị Trí Trong Hệ Thống

```
┌─────────────────────┐
│   Webhook Service   │
└─────────┬───────────┘
          │  Kafka: raw_events
          ▼
┌─────────────────────────────────────────────┐
│               Core Service                  │  ← BẠN ĐANG Ở ĐÂY
│                                             │
│  RawEventConsumer                           │
│       ↓                                     │
│  [1] SpamDetector (Redis + Rules)           │
│       ↓                                     │
│  [2] AiClassifier (Groq → Gemini → KW)     │
│       ↓                                     │
│  [3] DecisionEngine                         │
│       ↓                                     │
│  [4] ReplyCommandPublisher                  │
│       ↓                                     │
│  EventStatusService (Redis)                 │
└─────────┬───────────────────────────────────┘
          │  Kafka: reply_commands
          ▼
┌─────────────────────┐
│    Backend API      │
└─────────────────────┘
```

---

## 📁 Cấu Trúc Package

```
com.dev.coreservice/
├── CoreServiceApplication.java
├── config/
│   ├── AdminController.java          # REST API quản lý (blacklist, event status)
│   ├── AppProperties.java            # Cấu hình: Facebook, AI (Groq/Gemini), Spam
│   ├── KafkaConsumerConfig.java      # Cấu hình Kafka consumer
│   ├── KafkaProducerConfig.java      # Cấu hình Kafka producer
│   ├── RedisConfig.java              # Cấu hình Redis connection
│   └── WebClientConfig.java         # Cấu hình WebClient cho AI API calls
├── consumer/
│   └── RawEventConsumer.java         # Kafka consumer chính — orchestrate pipeline
├── pipeline/
│   ├── SpamDetector.java             # Bước 1: Phát hiện spam (rule-based)
│   ├── AiClassifier.java             # Bước 2: Phân loại AI
│   ├── DecisionEngine.java           # Bước 3: Ra quyết định
│   └── ReplyCommandPublisher.java    # Bước 4: Publish lệnh
├── service/
│   ├── BlacklistService.java         # Quản lý blacklist trong Redis
│   └── EventStatusService.java       # Track trạng thái event trong Redis
├── model/
│   ├── NormalizedEvent.java          # Input event (từ raw_events)
│   ├── SpamResult.java               # Kết quả spam detection
│   ├── ClassificationResult.java     # Kết quả AI classification
│   ├── Decision.java                 # Enum: AUTO_REPLY, HIDE, QUEUE...
│   ├── ReplyCommand.java             # Output command (→ reply_commands)
│   └── EventStatusRecord.java        # Record trạng thái event
└── client/
    ├── FacebookClient.java           # (Dự phòng) Direct Facebook API calls
    └── FacebookNonRetryableException.java
```

---

## 🔄 Pipeline Xử Lý Chi Tiết

### Bước 1: SpamDetector

Phát hiện spam **không cần AI** — nhanh và tiết kiệm API calls.

```
Input: NormalizedEvent
  │
  ├── [1] Blacklist check → Redis: key "blacklist:{senderId}"
  │       → Tìm thấy: SpamResult(blacklisted=true, hardSpam=true)
  │
  ├── [2] URL Detection → Regex: (https?://|bit.ly|tinyurl|t.co|goo.gl|fb.me)
  │       ├── Có URL + từ khoá độc hại (casino, scam, cờ bạc...) → malicious=true
  │       └── Có URL thông thường → hardSpam=true
  │
  └── [3] Repeat Content → Redis counter
          Key: "spam:repeat:{senderId}:{SHA256(content)[0:16]}"
          TTL: 24h (cấu hình được)
          ├── count >= 3 → repeatOffender=true, hardSpam=true
          └── count == 2 → softSpam=true
```

**SpamResult Fields:**

| Field | Ý nghĩa |
|-------|---------|
| `blacklisted` | Người dùng trong blacklist |
| `hardSpam` | Spam mạnh → ẩn comment ngay |
| `softSpam` | Spam nhẹ → ẩn + queue review |
| `malicious` | Chứa link độc hại / scam |
| `repeatOffender` | Lặp nội dung quá ngưỡng → đưa vào blacklist |
| `reason` | Lý do spam (để log) |

---

### Bước 2: AiClassifier

Phân loại intent và sentiment bằng AI với **Fallback Chain**:

```
Groq Llama-3.1-8b-instant (Primary)
    │  Thất bại (4xx, timeout)
    ▼
Keyword-based (Fallback 1 — không cần AI)
```

**Intent Categories:**

| Intent | Mô tả | Ví dụ (tiếng Việt) |
|--------|-------|-------------------|
| `ask_price` | Hỏi giá | "giá bao nhiêu", "bao nhiêu tiền" |
| `ask_info` | Hỏi thông tin | "ship được không", "còn hàng không" |
| `complaint` | Khiếu nại | "tệ lắm", "hoàn tiền", "hỏng rồi" |
| `compliment` | Khen ngợi | "tốt lắm", "uy tín", "recommend" |
| `spam` | Spam | Link quảng cáo, nội dung không liên quan |
| `other` | Khác | Không xác định rõ |

**ClassificationResult:**
```json
{
  "intent": "ask_price",
  "sentiment": "neutral",
  "requires_reply": true,
  "confidence": 0.95,
  "fallback": false
}
```

**Post-processing**: Nếu AI trả về `other` nhưng keyword khớp → override bằng keyword result (tránh bỏ lỡ hỏi giá tiếng Việt).

---

### Bước 3: DecisionEngine

Ma trận quyết định kết hợp SpamResult + ClassificationResult:

| Spam Status | AI Intent | Sentiment | Quyết Định |
|-------------|-----------|-----------|------------|
| BLACKLISTED | bất kỳ | bất kỳ | `HIDE_IMMEDIATELY` |
| HARD_SPAM + REPEAT | bất kỳ | bất kỳ | `BLACKLIST_AND_HIDE` |
| HARD_SPAM + MALICIOUS | bất kỳ | bất kỳ | `HIDE_AND_QUEUE_REVIEW` |
| HARD_SPAM | bất kỳ | bất kỳ | `HIDE_IMMEDIATELY` |
| SOFT_SPAM | bất kỳ | bất kỳ | `HIDE_AND_QUEUE_REVIEW` |
| Clean | `ask_price` / `ask_info` | neutral/positive | `AUTO_REPLY` |
| Clean | `complaint` | negative | `QUEUE_FOR_MANUAL_REPLY` |
| Clean | `compliment` | positive | `AUTO_REPLY_THANK_YOU` |
| Clean | `spam` (AI) | bất kỳ | `HIDE_IMMEDIATELY` |
| Clean | `other` | bất kỳ | `IGNORE` |
| Bất kỳ | bất kỳ | bất kỳ (confidence < 0.3) | `IGNORE` |

---

### Bước 4: ReplyCommandPublisher

Tạo `ReplyCommand` và publish lên Kafka `reply_commands`:

**Auto-reply messages:**
- `ask_price` / `ask_info`: *"Cảm ơn bạn đã quan tâm! Vui lòng inbox để được tư vấn giá chi tiết nhé 😊"*
- `compliment`: *"Cảm ơn bạn rất nhiều! Sự ủng hộ của bạn là động lực lớn nhất 🙏❤️"*

**BLACKLIST_AND_HIDE**: Tự động thêm `senderId` vào Redis blacklist trước khi publish command.

---

## 📊 EventStatusService — Tracking Trạng Thái

Mọi event được track trong Redis với key pattern `event:{eventId}:status` (TTL: 7 ngày).

```
RECEIVED → PROCESSING → PROCESSED
                      → REPLIED
                      → HIDDEN
                      → FAILED → DEAD_LETTER
```

---

## 🔧 Admin REST API

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| `POST` | `/admin/blacklist/{senderId}` | Thêm người dùng vào blacklist |
| `DELETE` | `/admin/blacklist/{senderId}` | Xóa khỏi blacklist |
| `GET` | `/admin/blacklist/{senderId}` | Kiểm tra blacklist |
| `GET` | `/admin/event/{eventId}/status` | Xem trạng thái xử lý event |

---

## ⚙️ Cấu Hình

### Biến Môi Trường (`.env`)

```env
# Facebook
PAGE_ID=your_page_id
PAGE_ACCESS_TOKEN=your_page_access_token

# AI APIs
GROQ_API_KEY=gsk_xxxxxxxxxxxx

# Kafka (nếu khác localhost)
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Redis (nếu khác localhost)
REDIS_HOST=localhost
REDIS_PORT=6379
```

### `application.yaml` (các tham số chính)

```yaml
# Spam Detection
spam:
  url-pattern: "(https?://|bit\\.ly|tinyurl\\.com|t\\.co|goo\\.gl|fb\\.me)"
  repeat-threshold: 3          # Số lần lặp → hard spam
  repeat-window-hours: 24      # Cửa sổ thời gian
  status-ttl-seconds: 604800   # TTL event status (7 ngày)

# AI Models
ai:
  groq:
    model: "llama-3.1-8b-instant"
    endpoint: "https://api.groq.com/openai/v1/chat/completions"
```

---

## 🚀 Cách Chạy

```bash
cd core-service
./mvnw spring-boot:run
```

> **Yêu cầu**: Kafka và Redis phải đang chạy (`docker-compose up -d`)

---

## 🧪 Test

### Test Blacklist API

```bash
# Thêm vào blacklist
curl -X POST http://localhost:8082/admin/blacklist/user123

# Kiểm tra
curl http://localhost:8082/admin/blacklist/user123

# Xóa khỏi blacklist
curl -X DELETE http://localhost:8082/admin/blacklist/user123
```

### Xem Trạng Thái Event

```bash
curl http://localhost:8082/admin/event/some-event-uuid/status
```

### Simulate Pipeline (qua Kafka)

Gửi message vào `raw_events` topic qua Kafka UI (`http://localhost:8081`) với payload:
```json
{
  "eventId": "test-uuid-001",
  "eventType": "comment",
  "pageId": "123456",
  "senderId": "user789",
  "content": "Giá sản phẩm này bao nhiêu vậy shop?",
  "commentId": "comment_abc",
  "postId": "post_xyz",
  "timestamp": 1716192000000
}
```

---

## 📝 Dependencies Chính

| Dependency | Mục đích |
|-----------|---------|
| spring-boot-starter-web | REST API (Admin) |
| spring-kafka | Kafka consumer & producer |
| spring-boot-starter-data-redis | Redis client |
| spring-boot-starter-webflux | WebClient cho AI API calls |
| lombok | Giảm boilerplate |

---

## ⚠️ Lưu Ý

- Core Service **không có cơ sở dữ liệu SQL** — toàn bộ state dùng **Redis**
- AI API timeout: **10 giây** mỗi provider — nếu vượt quá sẽ chuyển fallback
- Consumer group ID: `core-service-group` (topic: `raw_events`)
- Service **tự động bỏ qua** event có `senderId` trùng với `PAGE_ID` (event của chính trang)
