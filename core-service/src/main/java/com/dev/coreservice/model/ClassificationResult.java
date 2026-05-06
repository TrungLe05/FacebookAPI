package com.dev.coreservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kết quả phân tích của AI Classifier (Gemini Flash).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationResult {
    private String intent;          // ask_price | complaint | compliment | spam | other
    private String sentiment;       // positive | neutral | negative
    private boolean requiresReply;
    private double confidence;      // 0.0 – 1.0
    private boolean fallback;       // true nếu AI không available, dùng default
}
