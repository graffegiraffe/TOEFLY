package com.toefly.vocabulary.application.port.in;

import java.util.UUID;

public record CreateItemCommand(UUID ownerId, String title) {}
