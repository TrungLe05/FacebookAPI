package com.dev.fbapi.domain.model;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LikeSummary {
    Long totalCount;
    Boolean canLike;
    Boolean hasLiked;
}
