package com.dev.backendapi.application.usecase;

import com.dev.backendapi.domain.model.PageInfo;
import com.dev.backendapi.domain.port.FacebookGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetPageInfoUseCase {
    private final FacebookGateway facebookGateway;

    public PageInfo execute(String pageId) {
        return facebookGateway.getPageInfo(pageId);
    }
}
