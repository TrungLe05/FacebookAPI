package com.dev.fbapi.application.usecase;

import com.dev.fbapi.domain.model.PageInfo;
import com.dev.fbapi.domain.port.FacebookGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetPageInfoUseCase {
    private final FacebookGateway facebookGateway;

    public PageInfo execute(String pageId) {
        return facebookGateway.getPageInfo(pageId);
    }
}
