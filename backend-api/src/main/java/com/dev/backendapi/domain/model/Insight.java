package com.dev.backendapi.domain.model;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Insight {
    String id;
    String name;
    String period;
    String title;
    String description;
    List<Map<String, Object>> values;
}
