package com.toefly.content.application.port.out;

import java.util.Optional;

public interface EmbeddingPort {

    /** The embedding of the text, or empty when the model is unreachable (search then degrades to full text). */
    Optional<float[]> embed(String text);
}
