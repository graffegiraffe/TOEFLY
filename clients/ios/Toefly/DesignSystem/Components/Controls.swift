import SwiftUI
import ToeflyCore

/// Single-choice grid of chips (band targets, minutes a day). A radio group for VoiceOver: each chip is a button
/// with the selected trait (docs/07-ux-ui.md §5.3).
struct ChoiceGrid<Value: Hashable>: View {
    let label: String
    let options: [Value]
    @Binding var selection: Value
    let title: (Value) -> String
    var spokenTitle: ((Value) -> String)?
    var minimumWidth: CGFloat = 64

    @ScaledMetric(relativeTo: .body) private var scaledWidth: CGFloat = 64

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s2) {
            Text(label).font(.toeflyCaption.weight(.semibold)).foregroundStyle(Color.toefly.ink)
            LazyVGrid(
                columns: [GridItem(.adaptive(minimum: max(minimumWidth, scaledWidth)), spacing: Space.s2)],
                spacing: Space.s2,
            ) {
                ForEach(options, id: \.self) { option in
                    chip(option)
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(label)
    }

    private func chip(_ option: Value) -> some View {
        let selected = option == selection
        return Button {
            withAnimation(Motion.fast) { selection = option }
        } label: {
            Text(title(option))
                .font(.toeflyBodyStrong.monospacedDigit())
                .frame(maxWidth: .infinity, minHeight: 44)
                .foregroundStyle(selected ? Color.white : Color.toefly.ink)
                .background(selected ? Color.toefly.primary : Color.toefly.surface, in: .rect(cornerRadius: Radius.sm))
                .overlay {
                    RoundedRectangle(cornerRadius: Radius.sm)
                        .strokeBorder(selected ? Color.toefly.primary : Color.toefly.border)
                }
                .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(spokenTitle?(option) ?? title(option))
        .accessibilityAddTraits(selected ? [.isSelected] : [])
        .accessibilityIdentifier("choice-\(title(option))")
    }
}

/// Checkbox toggle: 22 pt square, radius 5, `primary` when on, 44 pt target (docs/07-ux-ui.md §3.5).
struct ToeflyCheckboxStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        Button {
            withAnimation(Motion.fast) { configuration.isOn.toggle() }
        } label: {
            HStack(alignment: .firstTextBaseline, spacing: Space.s3) {
                RoundedRectangle(cornerRadius: 5)
                    .fill(configuration.isOn ? Color.toefly.primary : Color.toefly.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: 5).strokeBorder(
                            configuration.isOn ? Color.toefly.primary : Color.toefly.checkboxBorder,
                            lineWidth: 1.5,
                        )
                    }
                    .overlay {
                        Image(systemName: "checkmark")
                            .font(.caption.weight(.heavy))
                            .foregroundStyle(.white)
                            .scaleEffect(configuration.isOn ? 1 : 0.6)
                            .opacity(configuration.isOn ? 1 : 0)
                    }
                    .frame(width: 22, height: 22)
                    .alignmentGuide(.firstTextBaseline) { $0[VerticalAlignment.center] + 6 }
                configuration.label
                    .font(.toeflyBody)
                    .foregroundStyle(Color.toefly.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(minHeight: 44)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityRepresentation {
            Toggle(isOn: configuration.$isOn) { configuration.label }
        }
    }
}

extension ToggleStyle where Self == ToeflyCheckboxStyle {
    static var toeflyCheckbox: ToeflyCheckboxStyle {
        ToeflyCheckboxStyle()
    }
}

/// Tag of a week section: pill, `micro` 600, colors of §2.3 (docs/07-ux-ui.md §3.4).
struct SectionTag: View {
    let section: WeekSection

    var body: some View {
        let colors = WeekSectionStyle.tag(section)
        Text(section.label)
            .font(.toeflyMicro)
            .foregroundStyle(colors.fg)
            .padding(.horizontal, Space.s2)
            .padding(.vertical, 2)
            .background(colors.bg, in: .capsule)
    }
}

/// The logo: TOE in `ink`, FLY in the brand color (docs/07-ux-ui.md §4.2).
struct Logo: View {
    var font: Font = .title3.weight(.heavy)

    var body: some View {
        Text("\(Text("TOE").foregroundStyle(Color.toefly.ink))\(Text("FLY").foregroundStyle(Color.toefly.brand700))")
            .font(font)
            .tracking(0.5)
            .accessibilityLabel("TOEFLY")
    }
}

/// Avatar with initials.
struct Avatar: View {
    let initials: String
    var size: CGFloat = 36

    var body: some View {
        Text(initials)
            .font(.system(size: size * 0.38, weight: .semibold))
            .minimumScaleFactor(0.5)
            .lineLimit(1)
            .foregroundStyle(Color.toefly.accentOnSoft)
            .frame(width: size, height: size)
            .background(Color.toefly.accentSoft, in: .circle)
            .accessibilityHidden(true)
    }
}
