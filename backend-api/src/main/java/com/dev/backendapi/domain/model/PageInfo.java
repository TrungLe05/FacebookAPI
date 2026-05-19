package com.dev.backendapi.domain.model;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PageInfo {
    String id;
    String name;
    Long fanCount;
    Long followersCount;
    String about;
    String website;
}
