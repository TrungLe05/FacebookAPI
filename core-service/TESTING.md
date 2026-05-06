# 🧪 Hướng dẫn Test Kịch bản Facebook Webhook (Postman)

Tài liệu này cung cấp các Payload (JSON format chuẩn của Facebook) để giả lập các sự kiện gửi vào Webhook. Bạn dùng **Postman** gửi POST request đến `webhook-service` để kiểm chứng luồng đi dữ liệu trong `core-service`.

## 🌐 Thiết lập Postman
* **Method:** `POST`
* **URL:** `http://localhost:3001/webhook` *(Đảm bảo `webhook-service` đang chạy ở port 8080)*
* **Headers:** `Content-Type: application/json`

---

## Kịch bản 1: Khách hàng hỏi giá sản phẩm
**Luồng xử lý:** Nhận comment -> SpamDetector (Sạch) -> AI (intent=ask_price) -> DecisionEngine (AUTO_REPLY) -> FacebookActionExecutor (Đã Reply).

**Payload:**
```json
{
  "object": "page",
  "entry": [
    {
      "id": "1234567890_PAGE_ID",
      "time": 1680000000,
      "changes": [
        {
          "field": "feed",
          "value": {
            "from": {"id": "111222333_USER", "name": "Khách Hàng A"},
            "item": "comment",
            "post_id": "1234567890_987654321",
            "comment_id": "1234567890_987654321_117",
            "created_time": 1680000000,
            "message": "Shop ơi cái này giá bao nhiêu vậy ạ?",
            "verb": "add"
          }
        }
      ]
    }
  ]
}
```

**✅ Log đối chiếu (Mẫu):**
```text
[Consumer] Received event=ade9ce4a-1b96-4d62-a56b-659a8d49cec2 type=comment partition=0 offset=84
[AiClassifier] Using model: llama-3.1-8b-instant endpoint: https://api.groq.com/openai/v1/chat/completions
[AiClassifier] Groq Raw Response:
{
  "intent": "ask_price",
   "sentiment": "neutral",
   "requires_reply": true,
   "confidence": 0.9
}
[DecisionEngine] event=ade9ce4a-1b96-4d62-a56b-659a8d49cec2 → spam=NONE/ intent=ask_price sentiment=neutral → decision=AUTO_REPLY
[FacebookActionExecutor] Executing AUTO_REPLY for event ade9ce4a-1b96-4d62-a56b-659a8d49cec2
[FacebookClient] [MOCK] replyToComment skipped – commentId=1234567890_987654321_117 message=Cảm ơn bạn đã quan tâm! Vui lòng inbox để được tư vấn giá chi tiết và ưu đãi tốt nhất nhé 😊
[StatusService] ade9ce4a-1b96-4d62-a56b-659a8d49cec2 → REPLIED
```

---

## Kịch bản 2: Spammer spam lặp lại nội dung (Soft Spam)
**Luồng xử lý:** Nhận comment -> SpamDetector (Phát hiện lặp lại trong Redis) -> Đánh dấu `SOFT` -> DecisionEngine (HIDE_IMMEDIATELY).

**Payload:** *(Gửi y hệt Payload này **2 lần liên tiếp** trên Postman)*
```json
{
  "object": "page",
  "entry": [
    {
      "id": "1234567890_PAGE_ID",
      "time": 1680000100,
      "changes": [
        {
          "field": "feed",
          "value": {
            "from": {"id": "999888777_SPAM", "name": "Spammer 1"},
            "item": "comment",
            "post_id": "1234567890_987654321",
            "comment_id": "1234567890_987654321_222",
            "created_time": 1680000100,
            "message": "Inbox để biết thêm chi tiết nha",
            "verb": "add"
          }
        }
      ]
    }
  ]
}
```

**✅ Log đối chiếu (Mẫu cho lần gửi thứ 2):**
```text
[DecisionEngine] event=83389992-d59d-4efe-8bfe-1235411db24c → spam=SOFT/ intent=other sentiment=neutral → decision=HIDE_IMMEDIATELY
[FacebookActionExecutor] Executing HIDE_IMMEDIATELY for event 83389992-d59d-4efe-8bfe-1235411db24c
[FacebookClient] [MOCK] hideComment skipped – commentId=1234567890_987654321_222
[StatusService] 83389992-d59d-4efe-8bfe-1235411db24c → HIDDEN (SPAM_HIDDEN)
```

---

## Kịch bản 3: Chứa link lừa đảo (Hard Spam)
**Luồng xử lý:** Nhận comment -> SpamDetector (Chứa URL lạ) -> Đánh dấu `HARD` -> DecisionEngine (HIDE_AND_QUEUE_REVIEW) -> Đưa vào danh sách review.

**Payload:**
```json
{
  "object": "page",
  "entry": [
    {
      "id": "1234567890_PAGE_ID",
      "time": 1680000200,
      "changes": [
        {
          "field": "feed",
          "value": {
            "from": {"id": "666555444_BOT", "name": "Bot Scam"},
            "item": "comment",
            "post_id": "1234567890_987654321",
            "comment_id": "1234567890_987654321_333",
            "created_time": 1680000200,
            "message": "Nhận ngay 500k khi click vào link này: http://scam-link.com/free",
            "verb": "add"
          }
        }
      ]
    }
  ]
}
```

**✅ Log đối chiếu (Mẫu):**
```text
[Consumer] Received event=c15c65a6-a1d8-4a14-8ccd-9a2788dfc07a type=comment partition=0 offset=87
[SpamDetector] Hard spam - URL detected from sender 666555444_BOT
[DecisionEngine] event=c15c65a6-a1d8-4a14-8ccd-9a2788dfc07a → spam=HARD/ intent=spam sentiment=neutral → decision=HIDE_AND_QUEUE_REVIEW
[FacebookActionExecutor] Executing HIDE_AND_QUEUE_REVIEW for event c15c65a6-a1d8-4a14-8ccd-9a2788dfc07a
[FacebookClient] [MOCK] hideComment skipped – commentId=1234567890_987654321_333
```

---

## Kịch bản 4: Phàn nàn / Khiếu nại (Inbox Messenger)
**Luồng xử lý:** Nhận tin nhắn Inbox -> AI (intent=complaint, sentiment=negative) -> DecisionEngine (QUEUE_FOR_MANUAL_REPLY) -> Chuyển giao cho CSKH.

**Payload:** *(Lưu ý: Khối `messaging` khác với `changes` của comment)*
```json
{
  "object": "page",
  "entry": [
    {
      "id": "1234567890_PAGE_ID",
      "time": 1680000300,
      "messaging": [
        {
          "sender": {"id": "777666555_USER"},
          "recipient": {"id": "1234567890_PAGE_ID"},
          "timestamp": 1680000300,
          "message": {
            "mid": "mid.$cAA...",
            "text": "Hàng giao bị lỗi rồi shop ơi, làm ăn chán thế. Yêu cầu đổi trả ngay!"
          }
        }
      ]
    }
  ]
}
```

**✅ Log đối chiếu (Mẫu):**
```text
[Consumer] Received event=2e1d57a2-f118-4567-9a34-9951c91ddb74 type=message partition=0 offset=89
[AiClassifier] Groq Raw Response:
{
  "intent": "complaint",
   "sentiment": "negative",
   "requires_reply": true,
   "confidence": 0.95
}
[DecisionEngine] event=2e1d57a2-f118-4567-9a34-9951c91ddb74 → spam=NONE/ intent=complaint sentiment=negative → decision=QUEUE_FOR_MANUAL_REPLY
[FacebookActionExecutor] Executing QUEUE_FOR_MANUAL_REPLY for event 2e1d57a2-f118-4567-9a34-9951c91ddb74
[FacebookActionExecutor] Complaint queued for manual reply: 2e1d57a2-f118-4567-9a34-9951c91ddb74
[StatusService] 2e1d57a2-f118-4567-9a34-9951c91ddb74 → PROCESSED (QUEUED_MANUAL_REPLY)
```
