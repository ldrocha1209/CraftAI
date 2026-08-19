package com.lucasrocha.craftai.client.intent;

import com.lucasrocha.craftai.client.data.AssistanceMode;

public record AssistanceIntent(
        AssistanceMode mode,
        boolean followUpLanguage,
        boolean destinationFollowUpLanguage
) {}
