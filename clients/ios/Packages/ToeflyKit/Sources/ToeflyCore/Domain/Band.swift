import Foundation

/// Band scale of the TOEFL iBT 2026 with CEFR and the comparable 0–120 score (docs/03-domain-model.md §8.5).
/// Mirrors `clients/web/src/lib/band.ts` so both clients show the same hints.
public enum Band {
    public static let all: [Double] = [1, 1.5, 2, 2.5, 3, 3.5, 4, 4.5, 5, 5.5, 6]

    private static let comparableLowerBound: [Double: Int] = [
        6: 114, 5.5: 105, 5: 95, 4.5: 84, 4: 72, 3.5: 58, 3: 44, 2.5: 33, 2: 22, 1.5: 11, 1: 0,
    ]

    public static func isBand(_ value: Double) -> Bool {
        value >= 1 && value <= 6 && (value * 2).rounded() == value * 2
    }

    public static func cefr(of band: Double) -> String {
        switch band {
        case 6...: "C2"
        case 5...: "C1"
        case 4...: "B2"
        case 3...: "B1"
        case 2...: "A2"
        default: "A1"
        }
    }

    /// Lower bound of the comparable 0–120 score, shown as "95+" (reference until January 2028).
    public static func comparableScore(of band: Double) -> Int? {
        comparableLowerBound[band]
    }

    /// Mean of the section targets rounded half up to 0.5 (AC-02.01.2).
    public static func overall(of sections: [Double]) -> Double {
        guard !sections.isEmpty else { return 0 }
        let mean = sections.reduce(0, +) / Double(sections.count)
        return (mean * 2 + 0.5).rounded(.down) / 2
    }

    public static func format(_ band: Double) -> String {
        String(format: "%.1f", band)
    }

    /// "5.0 ≈ C1 ≈ 95+" (docs/07-ux-ui.md §5.3, AC-02.01.1).
    public static func hint(_ band: Double) -> String {
        let score = comparableScore(of: band).map { "\($0)+" } ?? "—"
        return "\(format(band)) ≈ \(cefr(of: band)) ≈ \(score)"
    }

    /// Spoken form for VoiceOver: "5.0, C1, about 95 of 120".
    public static func spokenHint(_ band: Double) -> String {
        let score = comparableScore(of: band).map { ", about \($0) of 120" } ?? ""
        return "\(format(band)), \(cefr(of: band))\(score)"
    }
}
