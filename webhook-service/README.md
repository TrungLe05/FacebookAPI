# 📡 Webhook Service

<p align="left">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.0-6DB33F?logo=spring" />
  <img src="https://img.shields.io/badge/Apache%20Kafka-Producer-231F20?logo=apachekafka" />
  <img src="https://img.shields.io/badge/Port-8080-blue" />
</p>

---

## 🎯 Mục Đích

**Webhook Service** là **cửa ngõ đầu vào** của toàn bộ hệ thống. Service này chịu trách nhiệm:

1. **Xác thực webhook** từ Facebook Developer Dashboard (GET challenge)
2. **Nhận sự kiện thô** (POST) từ Facebook khi có comment hoặc message mới
3. **Xác minh chữ ký HMAC-SHA256** để đảm bảo request hợp lệ từ Facebook
4. **Chuẩn hoá (normalize)** payload Facebook sang định dạng thống nhất `NormalizedEvent`
5. **Publish event** lên Kafka topic `raw_events` để các service khác xử lý tiếp

---

## 🏗️ Vị Trí Trong Hệ Thống

```
Facebook Platform
      │  POST /webhook  (comment, message events)
      │  GET  /webhook  (verification challenge)
      ▼
┌─────────────────────┐
│   Webhook Service   │  ← BẠN ĐANG Ở ĐÂY
│   (Port 3001)       │
└─────────┬───────────┘
          │  Kafka Publish
          ▼  Topic: raw_events
┌─────────────────────┐
│    Core Service     │
└─────────────────────┘
```

---

## 📁 Cấu Trúc Package

```
com.dev.webhookservice/
├── WebhookServiceApplication.java      # Entry point
├── Controller/
│   └── WebhookController.java          # REST Endpoints (GET/POST /webhook)
├── Service/
│   └── WebhookService.java             # Logic xác minh & normalize event
└── Dtos/
    └── NormalizedEvent.java            # DTO chuẩn hoá sự kiện
```

---

## 🔌 REST API Endpoints

### `GET /webhook` — Xác Thực Webhook với Facebook

Facebook gọi endpoint này khi bạn đăng ký webhook lần đầu.

| Parameter | Mô tả |
|-----------|-------|
| `hub.mode` | Phải là `subscribe` |
| `hub.verify_token` | Phải khớp với `FACEBOOK_VERIFY_TOKEN` trong cấu hình |
| `hub.challenge` | Facebook gửi kèm, service trả về nguyên giá trị này |

**Response thành công**: HTTP 200 + `hub.challenge` value (text/plain)  
**Response thất bại**: HTTP 403 `Forbidden`

**Ví dụ request:**
```
GET /webhook?hub.mode=subscribe&hub.verify_token=my_token&hub.challenge=abc123
```

---

### `POST /webhook` — Nhận Sự Kiện Từ Facebook

Facebook gửi POST request mỗi khi có sự kiện (comment mới, tin nhắn mới...).

**Headers:**
| Header | Mô tả |
|--------|-------|
| `X-Hub-Signature-256` | Chữ ký HMAC-SHA256 (`sha256=<hex>`) — tùy chọn |

**Body**: Raw JSON payload từ Facebook

**Response**: HTTP 200 `EVENT_RECEIVED` (luôn trả 200 để Facebook không retry)

---

## ⚙️ Luồng Xử Lý Chi Tiết

```
POST /webhook (rawBody, X-Hub-Signature-256)
        │
        ├── 1. Kiểm tra signature (nếu có)
        │       HMAC-SHA256(rawBody, APP_SECRET) == signature ?
        │       └── Không khớp → throw RuntimeException("Invalid signature")
        │
        ├── 2. Parse JSON payload
        │       root.object == "page" ?
        │       └── Không phải page object → log.warn, return
        │
        ├── 3. Duyệt từng entry → changes[]
        │       field == "feed" → normalizeEvent() → NormalizedEvent(type="comment")
        │
        ├── 4. Duyệt từng entry → messaging[]
        │       normalizeMessage() → NormalizedEvent(type="message")
        │
        └── 5. kafkaTemplate.send("raw_events", eventId, normalizedEvent)
```

---

## 📦 NormalizedEvent — Cấu Trúc Sự Kiện Chuẩn Hoá

```json
{
  "eventId": "uuid-v4",
  "eventType": "comment",
  "pageId": "123456789",
  "senderId": "user-facebook-id",
  "recipientId": "page-id",
  "content": "Sản phẩm giá bao nhiêu vậy shop?",
  "commentId": "comment-id-from-facebook",
  "postId": "post-id-from-facebook",
  "timestamp": 1716192000000,
  "rawPayload": { ... }
}
```

| Field | Mô tả |
|-------|-------|
| `eventId` | UUID mới sinh (không phụ thuộc Facebook ID) |
| `eventType` | `comment` (feed event) hoặc `message` |
| `pageId` | ID Facebook Page nhận event |
| `senderId` | ID người dùng Facebook gửi comment/tin nhắn |
| `content` | Nội dung văn bản |
| `commentId` | ID comment trên Facebook (dùng để hide/reply) |
| `postId` | ID bài đăng chứa comment |
| `rawPayload` | Toàn bộ payload gốc từ Facebook (lưu để debug) |

---

## 🔒 Bảo Mật — Xác Minh Chữ Ký

Service tính toán HMAC-SHA256 của raw request body với `APP_SECRET`:

```
expected = "sha256=" + HMAC-SHA256(rawBody, APP_SECRET).toHexString()
```

So sánh với header `X-Hub-Signature-256`. Nếu không khớp → từ chối request.

> **Lưu ý**: Khi nhấn "Test" trên Facebook Dashboard, Facebook không gửi signature → Service bỏ qua verification và log cảnh báo.

---

## ⚙️ Cấu Hình

### `application.yaml`

```yaml
server:
  port: 8080

spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

facebook:
  verify-token: ${FACEBOOK_VERIFY_TOKEN}
  app-secret: ${FACEBOOK_APP_SECRET}
```

### Biến Môi Trường (`.env`)

```env
FACEBOOK_VERIFY_TOKEN=my_custom_verify_token_123
FACEBOOK_APP_SECRET=your_facebook_app_secret_from_developer_console
```

---

## 🚀 Cách Chạy

### Development

```bash
cd webhook-service
./mvnw spring-boot:run
```

### Production (JAR)

```bash
./mvnw clean package -DskipTests
java -jar target/webhook-service-*.jar
```

### Expose ra Internet (Ngrok)

```bash
# Cài Ngrok và chạy
ngrok http 3001

# Copy URL dạng https://xxxx.ngrok-free.app
# Dán vào Facebook Developer Console → Webhooks
# Callback URL: https://xxxx.ngrok-free.app/webhook
```

---

## 🧪 Test Thủ Công

### Test Verification

```bash
curl "http://localhost:8080/webhook?hub.mode=subscribe&hub.verify_token=YOUR_TOKEN&hub.challenge=test123"
# Expected: test123
```

### Simulate Facebook Comment Event

```bash
curl -X POST http://localhost:8080/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "object": "page",
    "entry": [{
      "id": "123456789",
      "time": 1716192000,
      "changes": [{
        "field": "feed",
        "value": {
          "from": {"id": "user123", "name": "Test User"},
          "message": "Giá bao nhiêu vậy shop?",
          "comment_id": "comment123",
          "post_id": "post456"
        }
      }]
    }]
  }'
# Expected: EVENT_RECEIVED
```

### Simulate Facebook Message Event

```bash
curl -X POST http://localhost:8080/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "object": "page",
    "entry": [{
      "id": "page123",
      "time": 1716192000,
      "messaging": [{
        "sender": {"id": "user456"},
        "recipient": {"id": "page123"},
        "message": {"text": "Cho mình hỏi về sản phẩm"}
      }]
    }]
  }'
```

---

## 📝 Dependencies Chính

| Dependency | Phiên bản | Mục đích |
|-----------|-----------|---------|
| spring-boot-starter-web | 3.5.0 | REST API |
| spring-kafka | — | Kafka producer |
| lombok | 1.18.42 | Giảm boilerplate |

---

## ⚠️ Lưu Ý Quan Trọng

1. Service phải **trả về HTTP 200** trong vòng **5 giây** — nếu không Facebook sẽ retry
2. Toàn bộ xử lý nặng được **offload sang Kafka** để đảm bảo response nhanh
3. `rawBody` phải được đọc dưới dạng `byte[]` (không phải String) để tính HMAC chính xác
4. **Không bao giờ commit APP_SECRET** lên Git — luôn dùng biến môi trường