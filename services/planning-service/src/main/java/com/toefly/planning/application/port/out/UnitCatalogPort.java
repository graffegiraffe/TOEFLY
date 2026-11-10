package com.toefly.planning.application.port.out;

import com.toefly.planning.domain.composition.UnitRef;
import java.util.List;

/** Approved units of the book tracks in book order (content-service). */
public interface UnitCatalogPort {

    List<UnitRef> roundUp();

    List<UnitRef> collinsGrammar();

    List<UnitRef> collinsVocabulary();
}
