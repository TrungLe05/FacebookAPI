# Webhook Service với Kafka - Hướng dẫn cài đặt

Hệ thống xử lý sự kiện thời gian thực từ Facebook Webhook, normalize dữ liệu và đẩy vào Kafka topic `raw_events`.

## Kiến trúc tổng quan

```
Facebook → webhook-service (port 3001) → Kafka (raw_events) → Core Service
```

---

## Yêu cầu hệ thống

- Java 21+
- Maven 3.5+
- Docker Desktop
- ngrok (để expose localhost ra internet cho Facebook gọi vào)

---

## 1. Clone project

```bash
git clone <repository-url>
cd <project-folder>
```

Cấu trúc project:

```
FB-API/                  # Base service Facebook API (đã có sẵn)
webhook-service/         # Service mới xử lý webhook
```

---

## 2. Cấu hình biến môi trường

Mở file `webhook-service/src/main/resources/application.yml` và điền thông tin:

```yaml
server:
  port: 3001

facebook:
  app-secret: <App Secret từ Facebook Developer Console>
  verify-token: <Token bạn tự tạo - phải khớp với cái điền trên Facebook>

spring:
  application:
    name: webhook-service
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

### Cách lấy App Secret và tạo Verify Token

**App Secret:**
1. Vào [developers.facebook.com](https://developers.facebook.com) → chọn App của bạn
2. **Settings** → **Basic** → copy **App Secret**

**Verify Token** (tự generate):

```powershell
# Chạy trong PowerShell
[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

Copy chuỗi output ra và điền vào `verify-token` trong `application.yml`.

---

## 3. Khởi động Kafka bằng Docker

```bash
# Khởi động Zookeeper, Kafka, Kafka UI
docker-compose up -d

# Kiểm tra các container đang chạy
docker ps
```

Phải thấy 3 container: `zookeeper`, `kafka`, `kafka-ui`.

**Kafka UI** (xem topic và message): [http://localhost:8080](http://localhost:8080)

Nội dung `docker-compose.yml`:

```yaml
version: '3.8'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_LISTENERS: INTERNAL://0.0.0.0:29092,EXTERNAL://0.0.0.0:9092
      KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka:29092,EXTERNAL://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: INTERNAL
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: kafka-ui
    depends_on:
      - kafka
    ports:
      - "8080:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
```

---

## 4. Khởi động webhook-service

```bash
cd webhook-service
mvn spring-boot:run
```

Kiểm tra service đang chạy ở port 3001:

```powershell
netstat -ano | findstr :3001
```

Phải thấy dòng `LISTENING`.

---

## 5. Expose webhook ra internet bằng ngrok

Facebook cần gọi vào HTTPS endpoint của bạn, nên phải dùng ngrok để expose localhost.

```bash
# Cài ngrok tại https://ngrok.com/download
# Đăng ký tài khoản miễn phí và lấy authtoken

ngrok config add-authtoken <YOUR_AUTHTOKEN>
ngrok http 3001
```

Copy URL `https://xxx.ngrok-free.app` hiện ra — đây là Callback URL dùng cho Facebook.

> **Lưu ý:** Mỗi lần restart ngrok (free tier) sẽ đổi URL mới → phải cập nhật lại Callback URL trên Facebook Developer Console.

---

## 6. Cấu hình Webhook trên Facebook Developer Console

### 6.1 Đăng ký Webhook

1. Vào [developers.facebook.com](https://developers.facebook.com) → chọn App
2. **Webhooks** → **Add Subscription** → chọn **Page**
3. Điền thông tin:
    - **Callback URL:** `https://xxx.ngrok-free.app/webhook`
    - **Verify Token:** chuỗi bạn đã tạo ở bước 2
4. Click **Verify and Save**

> Server phải đang chạy thì Facebook mới verify được.

### 6.2 Subscribe fields

Sau khi verify thành công, chọn subscribe các fields:
- ✅ `feed` — nhận comment, post
- ✅ `messages` — nhận tin nhắn

### 6.3 Subscribe Page vào App

Vào [Graph API Explorer](https://developers.facebook.com/tools/explorer/):

1. Chọn App và đổi **"Người dùng hoặc Trang"** sang **tên Page của bạn**
2. Method **POST**, URL: `<PAGE_ID>/subscribed_apps`
3. Thêm param: `subscribed_fields` = `feed,messages`
4. Click **Gửi** → phải trả về `{"success": true}`

---

## 7. Test luồng hoàn chỉnh

### Cách 1: Test bằng Facebook Developer Console

1. **Webhooks** → click **"Test"** cạnh field `feed` hoặc `messages`
2. Click **"Gửi đến máy chủ của tôi"**
3. Kiểm tra ngrok inspector: [http://localhost:4040](http://localhost:4040)
4. Kiểm tra Kafka UI: [http://localhost:8080](http://localhost:8080) → Topics → `raw_events`

### Cách 2: Comment thật lên Facebook Page

- App đang ở **chế độ Phát triển** → chỉ nhận webhook từ tài khoản có vai trò trong App (Admin, Developer, Tester)
- Dùng đúng tài khoản cá nhân Admin để comment lên Page (không dùng danh tính Page)

### Cách 3: Giả lập bằng PowerShell (Khuyến nghị khi không test được từ Facebook)

**Giả lập event `feed` (comment):**

```powershell
$body = '{"object":"page","entry":[{"id":"<PAGE_ID>","time":1714000000,"changes":[{"field":"feed","value":{"from":{"id":"123456","name":"Test User"},"message":"Hello test comment","item":"comment","verb":"add"}}]}]}'

$secret = "<APP_SECRET_CUA_BAN>"

$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($secret)
$hash = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($body))
$signature = "sha256=" + [BitConverter]::ToString($hash).Replace("-","").ToLower()

Invoke-WebRequest -Method POST `
  -Uri "https://<NGROK_URL>/webhook" `
  -Headers @{"Content-Type"="application/json"; "X-Hub-Signature-256"=$signature} `
  -Body $body
```

**Giả lập event `messages` (tin nhắn):**

```powershell
$body = '{"object":"page","entry":[{"id":"<PAGE_ID>","time":1714000000,"messaging":[{"sender":{"id":"12334"},"recipient":{"id":"<PAGE_ID>"},"timestamp":"1527459824","message":{"mid":"test_message_id","text":"Hello test message"}}]}]}'

$secret = "<APP_SECRET_CUA_BAN>"

$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($secret)
$hash = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($body))
$signature = "sha256=" + [BitConverter]::ToString($hash).Replace("-","").ToLower()

Invoke-WebRequest -Method POST `
  -Uri "https://<NGROK_URL>/webhook" `
  -Headers @{"Content-Type"="application/json"; "X-Hub-Signature-256"=$signature} `
  -Body $body
```

> Thay `<PAGE_ID>`, `<APP_SECRET_CUA_BAN>`, `<NGROK_URL>` bằng giá trị thật.

**Kiểm tra message trong Kafka:**

```bash
docker exec kafka kafka-console-consumer \
  --topic raw_events \
  --bootstrap-server localhost:9092 \
  --from-beginning
```

---

## 8. Troubleshooting

| Lỗi | Nguyên nhân | Fix |
|---|---|---|
| Facebook không verify được Callback URL | Service chưa chạy hoặc ngrok sai URL | Kiểm tra service port 3001 và URL ngrok |
| `Invalid signature` (500) | App Secret sai hoặc payload bị thay đổi | Kiểm tra `app-secret` trong `application.yml` |
| `NullPointerException` tại `changes` | Payload `messages` không có field `changes` | Đảm bảo code xử lý cả `changes` và `messaging` |
| Kafka UI không kết nối được | kafka-ui dùng `localhost` thay vì tên service | Dùng `kafka:29092` trong biến `KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS` |
| Facebook không gửi webhook khi comment | App ở chế độ Phát triển, tài khoản không có role | Thêm tài khoản vào Tester hoặc dùng tài khoản Admin |
| ngrok đổi URL sau khi restart | Free tier ngrok | Cập nhật lại Callback URL trên Facebook sau mỗi lần restart ngrok |

---

## 9. Checklist chạy project

- [ ] Docker Desktop đang chạy
- [ ] `docker-compose up -d` — 3 container Up
- [ ] `webhook-service` đang chạy ở port 3001
- [ ] `ngrok http 3001` đang chạy
- [ ] Callback URL trên Facebook đã cập nhật URL ngrok mới nhất
- [ ] `application.yml` có đúng `app-secret` và `verify-token`
- [ ] Page đã được subscribe: `<PAGE_ID>/subscribed_apps` trả về `{"success": true}`