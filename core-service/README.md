# 🚀 Core Service - Facebook Webhook Pipeline

Module **Core Service** đóng vai trò là "bộ não" của hệ thống xử lý tin nhắn và bình luận từ Facebook. Nó đảm nhận việc phân tích dữ liệu, lọc rác (Spam Detection), hiểu ngôn ngữ tự nhiên (AI Classification bằng Groq/Llama-3), và đưa ra quyết định tự động.

---

## 🛠 Yêu cầu hệ thống (Prerequisites)
Để chạy được ứng dụng này, máy của bạn cần cài đặt sẵn:
- **Java 17+** (Khuyên dùng Java 21 hoặc 23)
- **Maven**
- **Apache Kafka** (hoặc dùng Docker Compose đi kèm)
- **Redis** (Port mặc định 6379)

---

## 🔑 Cấu hình AI Model (Groq API Key)
Hiện tại dự án đang sử dụng **Groq** với model `llama-3.1-8b-instant` để xử lý ngôn ngữ tự nhiên (NLP) với tốc độ siêu nhanh và miễn phí. 

**Cách lấy API Key miễn phí:**
1. Truy cập [console.groq.com](https://console.groq.com/) và đăng nhập.
2. Ở menu bên trái, chọn **API Keys** -> Bấm **Create API Key**.
3. Copy khóa API được sinh ra (bắt đầu bằng `gsk_...`).

**Cấu hình vào Project:**
Tạo file `.env` ở thư mục gốc của `core-service` hoặc cấu hình trực tiếp vào Run/Debug Configurations của IDE:
```env
GROQ_API_KEY=gsk_your_api_key_here
REDIS_HOST=localhost
REDIS_PORT=6379
```

*(Hoặc bạn có thể dán trực tiếp API key vào mục `groq` trong file `src/main/resources/application.yaml` - KHÔNG khuyến khích khi push code lên GitHub).*

---

## ⚙️ Hướng dẫn cài đặt và chạy ứng dụng

### Bước 1: Khởi động các dịch vụ nền
Chạy Kafka (Zookeeper + Broker) và Redis. Nếu bạn có file `docker-compose.yml`, hãy chạy:
```bash
docker-compose up -d
```

### Bước 2: Khởi chạy Webhook Service
Đảm bảo bạn cũng clone và chạy module `webhook-service` (module chịu trách nhiệm hứng Webhook từ Facebook và đẩy vào Kafka).

### Bước 3: Chạy Core Service
Bạn có thể mở project bằng IntelliJ IDEA và chạy file `CoreServiceApplication.java` hoặc sử dụng Maven:
```bash
mvn spring-boot:run
```
Ứng dụng sẽ chạy ở port **3002** và bắt đầu lắng nghe các sự kiện từ Kafka topic `raw_events`.

---

## 🧪 Hướng dẫn kiểm thử (Testing)
Tất cả các kịch bản test bằng Postman (từ comment hỏi giá, đến spam, đến khiếu nại) đã được tài liệu hóa chi tiết kèm theo log đối chiếu.

👉 **Vui lòng xem file: [TESTING.md](./TESTING.md)** để biết chi tiết cách giả lập sự kiện Facebook.
