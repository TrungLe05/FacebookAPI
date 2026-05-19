package com.dev.backendapi.domain.model;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Post {
    String id;
    String message;
    String createdTime;
    String fullPicture;
    String permalinkUrl;
}
