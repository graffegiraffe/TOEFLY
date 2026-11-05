package com.toefly.common.test.contract;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Completeness of contracts (docs/11-quality-testing.md §8.5). */
public final class ContractCoverage {

    private ContractCoverage() {}

    /** Every topic the service publishes has at least one message contract in the directory tree. */
    public static void assertMessagesCover(Path directory, Collection<String> topics) {
        Set<String> covered = Contracts.load(directory).stream()
                .filter(Contract::isMessage)
                .map(contract -> contract.outputMessage().sentTo())
                .collect(Collectors.toSet());
        List<String> missing = topics.stream()
                .filter(topic -> !covered.contains(topic))
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw new AssertionError("Topics without a message contract in " + directory + ": " + missing);
        }
    }

    /** Every consumer of an internal operation ({@code x-toefly-callers}) has a REST contract directory. */
    public static void assertCallersCover(Path directory, Collection<String> callers) {
        List<String> missing = callers.stream()
                .filter(caller -> Contracts.load(directory).stream()
                        .filter(Contract::isRest)
                        .noneMatch(contract -> contract.file()
                                .getParent()
                                .getFileName()
                                .toString()
                                .equals(caller)))
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw new AssertionError("Callers without a REST contract in " + directory + ": " + missing);
        }
    }
}
