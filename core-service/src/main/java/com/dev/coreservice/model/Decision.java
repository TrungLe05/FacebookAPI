package com.dev.coreservice.model;

/**
 * Các action mà DecisionEngine có thể ra quyết định.
 */
public enum Decision {
    /** Ẩn comment ngay lập tức (hard spam / link / scam rõ ràng) */
    HIDE_IMMEDIATELY,

    /** Ẩn và đưa vào hàng đợi review thủ công */
    HIDE_AND_QUEUE_REVIEW,

    /** Tự động reply (hỏi giá, tương tác tích cực) */
    AUTO_REPLY,

    /** Tự động reply cảm ơn (khen / compliment) */
    AUTO_REPLY_THANK_YOU,

    /** Đưa vào queue để admin reply thủ công (khiếu nại, tiêu cực) */
    QUEUE_FOR_MANUAL_REPLY,

    /** Ẩn và thêm sender vào blacklist */
    BLACKLIST_AND_HIDE,

    /** Bỏ qua – không làm gì (confidence quá thấp) */
    IGNORE
}
