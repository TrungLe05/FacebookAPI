package com.dev.coreservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kết quả của SpamDetector – phân tầng spam.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpamResult {
    private boolean spam;               // là spam hay không
    private boolean hardSpam;           // link rõ ràng / scam → ẩn ngay
    private boolean softSpam;           // nghi spam → queue review
    private boolean blacklisted;        // sender đã trong blacklist
    private boolean repeatOffender;     // lặp lại ≥ 3 lần trong 24h
    private String reason;              // mô tả lý do
}
