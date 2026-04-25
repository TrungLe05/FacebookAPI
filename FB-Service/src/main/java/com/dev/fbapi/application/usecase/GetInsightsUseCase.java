package com.dev.fbapi.application.usecase;


import com.dev.fbapi.domain.model.Insight;
import com.dev.fbapi.domain.port.FacebookGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetInsightsUseCase {
    private final FacebookGateway facebookGateway;

    public List<Insight> execute(String pageId) {
        return facebookGateway.getInsights(pageId);
    }
}
