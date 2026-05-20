# 🔁 Retry Service

<p align="left">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.0-6DB33F?logo=spring" />
  <img src="https://img.shields.io/badge/Apache%20Kafka-Consumer%20%2B%20Producer-231F20?logo=apachekafka" />
  <img src="https://img.shields.io/badge/Exponential%20Backoff-1s%20→%202s%20→%204s-orange" />
</p>

---

## 🎯 Mục Đích

**Retry Service** là **lưới an toàn** của hệ thống. Khi Backend API gặp lỗi tạm thời khi gọi Facebook Graph API (rate limit, mạng không ổn định...), service này chịu trách nhiệm:

1. **Consume `send_failed` events** từ Kafka — nhận các command thất bại cần retry
2. **Áp dụng Exponential Backoff** — chờ ngày càng lâu hơn giữa các lần retry (1s → 2s → 4s)
3. **Tối đa 3 lần retry** — sau đó route message vào `dead_letter` để monitoring và alerting
4. **Publish `send_retry`** — gửi lại command cho Backend API xử lý tiếp

---

## 🏗️ Vị Trí Trong Hệ Thống

```
┌─────────────────────────┐
│      Backend API        │  Gọi Facebook API thất bại (tạm thời)
│                         │  → Kafka: send_failed
└─────────┬───────────────┘
          │
          ▼  Kafka: send_failed
┌─────────────────────────────────────────────┐
│              Retry Service                  │  ← BẠN ĐANG Ở ĐÂY
│                                             │
│  SendFailedConsumer                         │
│       ↓                                     │
│  retryCount >= 3 ? → dead_letter            │
│       ↓ (chưa đủ 3 lần)                    │
│  Exponential Backoff (ScheduledExecutor)    │
│       ↓  delay = 1000ms * 2^retryCount      │
│  Publish → send_retry                       │
└─────────┬───────────────┬─────────────────┘
          │               │
    Kafka: send_retry   Kafka: dead_letter
          │               │
          ▼               ▼
┌─────────────────┐  ┌─────────────────────────┐
│  Backend API    │  │  Prometheus + AlertMgr  │
│ (Retry attempt) │  │  (Cảnh báo Slack/Email) │
└─────────────────┘  └─────────────────────────┘
```

---

## 📁 Cấu Trúc Package

```
com.dev.retryservice/
├── RetryserviceApplication.java
├── consumer/
│   └── SendFailedConsumer.java    # Kafka consumer: send_failed → xử lý retry logic
├── Models/
│   ├── SendFailedEvent.java       # DTO của event thất bại
│   └── ReplyCommand.java          # Payload command gốc (được giữ nguyên)
└── config/
    └── KafkaConfig.java           # Cấu hình consumer/producer factories
```

---

## 🔄 Luồng Xử Lý Retry

### Exponential Backoff

```
Lần 1: delay = 1000ms * 2^0 = 1,000ms  (1 giây)
Lần 2: delay = 1000ms * 2^1 = 2,000ms  (2 giây)
Lần 3: delay = 1000ms * 2^2 = 4,000ms  (4 giây)
Lần 4: retryCount >= MAX_RETRIES(3) → dead_letter
```

### Chi Tiết Flow

```
SendFailedEvent consumed từ Kafka(send_failed)
        │
        ├── retryCount >= 3 ?
        │       └── YES → publishDeadLetter(event)
        │               → Kafka: dead_letter
        │               → (Prometheus alert → Slack + Email)
        │
        └── NO → tính delay = 1000ms * 2^retryCount
                 → scheduler.schedule(delay ms):
                     publish SendFailedEvent (retryCount + 1)
                     → Kafka: send_retry
                     → Backend API consume & thử lại gọi Facebook API
```

---

## 📦 SendFailedEvent — Cấu Trúc Event

```json
{
  "schemaVersion": 1,
  "commandId": "uuid-của-command-gốc",
  "eventId": "uuid-của-event-gốc",
  "retryCount": 1,
  "lastError": "Connection timeout to graph.facebook.com",
  "nextRetryAt": "2024-01-15T10:30:05Z",
  "failedAt": "2024-01-15T10:30:00Z",
  "command": {
    "commandId": "...",
    "action": "reply",
    "commentId": "comment_abc123",
    "replyText": "Cảm ơn bạn đã quan tâm!...",
    "eventType": "comment",
    ...
  }
}
```

| Field | Mô tả |
|-------|-------|
| `schemaVersion` | Phiên bản schema (dùng cho migration sau này) |
| `commandId` | ID của command gốc (giống với Backend API) |
| `retryCount` | Số lần đã retry (tăng dần) |
| `lastError` | Thông báo lỗi lần cuối |
| `nextRetryAt` | Thời điểm dự kiến retry tiếp |
| `failedAt` | Thời điểm thất bại đầu tiên |
| `command` | Toàn bộ `ReplyCommand` gốc (giữ nguyên để replay) |

---

## ⚙️ ScheduledExecutorService

Service dùng `ScheduledExecutorService` với **4 threads** để chạy delayed retry song song:

```java
private final ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(4);
```

Điều này cho phép xử lý nhiều retry cùng lúc với các delay khác nhau mà không block Kafka consumer thread.

---

## 📊 Kafka Topics

| Topic | Role | Mô tả |
|-------|------|-------|
| `send_failed` | **Consumer** | Nhận event thất bại từ Backend API |
| `send_retry` | **Producer** | Gửi event retry về Backend API |
| `dead_letter` | **Producer** | Gửi event quá 3 lần thất bại để monitoring |

### Kafka Config Chính

- **Consumer group**: `retry-service-group`
- **Auto offset reset**: `earliest` (không bỏ lỡ message cũ)
- **Ack mode**: `RECORD` (manual ack sau mỗi message)
- **Error handler**: `FixedBackOff(0, 0)` — không retry ở level consumer, retry được handle trong code

---

## ⚙️ Cấu Hình

### `application.yaml`

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: retry-service-group
      auto-offset-reset: earliest
```

### Biến Môi Trường

```env
# Kafka (nếu khác localhost)
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

### Constants (trong code)

```java
private static final int MAX_RETRIES = 3;     // Tối đa 3 lần retry
private static final String SEND_RETRY_TOPIC  = "send_retry";
private static final String DEAD_LETTER_TOPIC = "dead_letter";
```

---

## 🚀 Cách Chạy

```bash
cd retry-service
./mvnw spring-boot:run
```

> **Yêu cầu**: Kafka phải đang chạy (`docker-compose up -d`)

---

## 🧪 Test Retry Flow

### Cách 1: Kích Hoạt Qua Kafka UI

1. Mở Kafka UI: `http://localhost:8081`
2. Tìm topic `send_failed`
3. Produce message với payload `SendFailedEvent` (retryCount=0):

```json
{
  "schemaVersion": 1,
  "commandId": "test-command-001",
  "eventId": "test-event-001",
  "retryCount": 0,
  "lastError": "Simulated failure",
  "command": {
    "commandId": "test-command-001",
    "action": "reply",
    "commentId": "comment_test",
    "replyText": "Test reply",
    "eventType": "comment",
    "senderId": "user123"
  }
}
```

4. Quan sát log: Service sẽ đợi 1 giây rồi publish sang `send_retry`

### Cách 2: Kích Hoạt Qua Circuit Breaker

1. Tắt kết nối Facebook API (hoặc dùng sai token)
2. Gửi webhook event → Core Service → Backend API sẽ thất bại → publish `send_failed`
3. Retry Service sẽ tự động xử lý

### Kiểm Tra Dead Letter

Sau 3 lần retry thất bại, kiểm tra topic `dead_letter` trong Kafka UI.

---

## 📝 Dependencies Chính

| Dependency | Mục đích |
|-----------|---------|
| spring-boot-starter | Core Spring Boot |
| spring-kafka | Kafka consumer & producer |
| lombok | Boilerplate reduction |

---

## 📊 Monitoring & Alerting

Retry Service **không có metrics riêng** nhưng hoạt động của nó được giám sát gián tiếp qua Prometheus:

| Alert | Trigger | Severity |
|-------|---------|---------|
| `SendFailedTopicActive` | Có message mới trong `send_failed` | ⚠️ Warning |
| `RetryTopicActive` | Có message mới trong `send_retry` | ⚠️ Warning |
| `DeadLetterQueueReceived` | Có message mới trong `dead_letter` | 🔴 Critical |

Khi `dead_letter` có message → AlertManager gửi ngay **Slack + Email notification**.

---

## 🏁 Tổng Quan Vòng Đời Retry

```
Backend API gọi Facebook API
        │
        │ Thất bại tạm thời
        ▼
Kafka: send_failed (retryCount=0)
        │
        ▼
Retry Service: delay 1s → Kafka: send_retry (retryCount=1)
        │
        ▼
Backend API retry lần 1
        │ Vẫn thất bại
        ▼
Kafka: send_failed (retryCount=1)
        │
        ▼
Retry Service: delay 2s → Kafka: send_retry (retryCount=2)
        │
        ▼
Backend API retry lần 2
        │ Vẫn thất bại
        ▼
Kafka: send_failed (retryCount=2)
        │
        ▼
Retry Service: delay 4s → Kafka: send_retry (retryCount=3)
        │
        ▼
Backend API retry lần 3
        │ Vẫn thất bại
        ▼
Kafka: send_failed (retryCount=3)
        │
        ▼
Retry Service: retryCount >= MAX_RETRIES(3)
        │
        ▼
Kafka: dead_letter → 🚨 Alert → Slack + Email
```

---

## ⚠️ Lưu Ý

1. **Graceful Shutdown**: Khi service shutdown, `@PreDestroy` gọi `scheduler.shutdown()` để hoàn thành các delayed retry đang chờ
2. **Thread Safety**: `ScheduledExecutorService` thread pool đảm bảo retry an toàn trong môi trường concurrent
3. **Không có state**: Retry Service hoàn toàn **stateless** — toàn bộ context được giữ trong `SendFailedEvent.command`
4. **Idempotency**: Backend API có idempotency check, nên ngay cả khi retry trùng lặp cũng không gây ra duplicate action
