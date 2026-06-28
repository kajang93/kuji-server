package com.kuji.backend.domain.a2a.event;

public record MarketingCampaignProposalEvent(
        String campaignId,
        String kujiTitle,
        int targetUserCount,
        String proposedCopy
) {}
