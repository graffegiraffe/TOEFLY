#!/usr/bin/env python3
"""Writes the color sets of Toefly/Resources/Assets.xcassets/Colors from the design tokens.

The values are the tables of docs/07-ux-ui.md §2.1–2.3 (the same as design/prototype/css/theme.css and the web
theme). Every color set has an Any and a Dark appearance, so iOS switches the theme by itself. Run after changing
a token:  python3 clients/ios/scripts/generate-color-assets.py
"""
import json
import pathlib
import re
import shutil

ROOT = pathlib.Path(__file__).resolve().parent.parent / "Toefly" / "Resources" / "Assets.xcassets"
COLORS = ROOT / "Colors"

BRAND = {
    "brand50": "#E6F7EF", "brand100": "#C7EEDB", "brand200": "#A7EBC8", "brand300": "#6FD9A4",
    "brand400": "#3CC98A", "brand500": "#04AC6A", "brand600": "#08945B", "brand700": "#0F7D4D",
    "brand800": "#106B44", "brand900": "#0D5236", "brand950": "#08311F",
}

# name: (light, dark) — docs/07-ux-ui.md §2.2; `accent` is stored as `brandAccent` (AccentColor is the app tint).
SEMANTIC = {
    "canvas": ("#F4F8F5", "#0B140F"),
    "surface": ("#FFFFFF", "#14211A"),
    "surfaceRaised": ("#FFFFFF", "#1A2A21"),
    "surfaceTask": ("#FFFFFF", "#111C16"),
    "surfaceSunken": ("#EAF0EC", "#0F1A14"),
    "ink": ("#14231A", "#E8F2EC"),
    "inkMuted": ("#5B6B61", "#97A89E"),
    "inkSubtle": ("#6B7B71", "#7F9086"),
    "inkInverse": ("#FFFFFF", "#0B140F"),
    "border": ("#DCE4DF", "rgba(255,255,255,0.08)"),
    "borderStrong": ("#B9C7BE", "rgba(255,255,255,0.18)"),
    "checkboxBorder": ("#6B7B71", "#7F9086"),
    "brandAccent": ("#04AC6A", "#04AC6A"),
    "accentSoft": ("#E6F7EF", "#173A25"),
    "accentOnSoft": ("#106B44", "#6FD9A4"),
    "primary": ("#0F7D4D", "#0F7D4D"),
    "primaryHover": ("#106B44", "#106B44"),
    "link": ("#0F7D4D", "#3CC98A"),
    "focusRing": ("#0F7D4D", "#3CC98A"),
    "success": ("#0F7D4D", "#3CC98A"),
    "successSoft": ("#E6F7EF", "#173A25"),
    "warning": ("#B45309", "#F5B544"),
    "warningSoft": ("#FEF3C7", "#33270C"),
    "danger": ("#B42318", "#FF8A80"),
    "dangerSoft": ("#FEECEB", "#3A1714"),
    "dangerSolid": ("#C9372C", "#C9372C"),
    "info": ("#0B6378", "#7FD3E6"),
    "infoSoft": ("#DDF4F8", "#0E2A31"),
    "recording": ("#C9372C", "#FF6B5E"),
    "ringTrack": ("rgba(0,0,0,0.08)", "rgba(255,255,255,0.10)"),
    "meshCanvas": ("#F4F8F5", "#0F1E15"),
}

# WeekSection tags — docs/07-ux-ui.md §2.3: (light bg, light fg, dark bg, dark fg).
TAGS = {
    "Reading": ("#FFF4C2", "#7A5A00", "#2E2A12", "#F2D46B"),
    "Listening": ("#DDF4F8", "#0B6378", "#0E2A31", "#7FD3E6"),
    "Writing": ("#FBE3F1", "#9C2A6B", "#33162A", "#F0A3CF"),
    "Speaking": ("#E6F7EF", "#106B44", "#173A25", "#6FD9A4"),
    "Vocabulary": ("#ECE9FB", "#4B3FA6", "#221E3D", "#B8AEF5"),
    "Grammar": ("#FDEBDD", "#9A4A12", "#33200F", "#F5B583"),
    "Resources": ("#EEF2EF", "#3C4C42", "#1E2B24", "#C9D6CE"),
}


def components(value: str) -> dict:
    match = re.fullmatch(r"rgba\((\d+),(\d+),(\d+),([\d.]+)\)", value.replace(" ", ""))
    if match:
        red, green, blue, alpha = match.groups()
        values = [int(red) / 255, int(green) / 255, int(blue) / 255, float(alpha)]
    else:
        hex_value = value.lstrip("#")
        values = [int(hex_value[i : i + 2], 16) / 255 for i in (0, 2, 4)] + [1.0]
    return {
        "color-space": "srgb",
        "components": {key: f"{number:.3f}" for key, number in zip(("red", "green", "blue", "alpha"), values)},
    }


def write(name: str, light: str, dark: str) -> None:
    folder = COLORS / f"{name}.colorset"
    folder.mkdir(parents=True, exist_ok=True)
    colors = [{"color": components(light), "idiom": "universal"}]
    if dark != light:
        colors.append({
            "appearances": [{"appearance": "luminosity", "value": "dark"}],
            "color": components(dark),
            "idiom": "universal",
        })
    contents = {"colors": colors, "info": {"author": "xcode", "version": 1}}
    (folder / "Contents.json").write_text(json.dumps(contents, indent=2) + "\n")


def main() -> None:
    if COLORS.exists():
        shutil.rmtree(COLORS)
    COLORS.mkdir(parents=True)
    (COLORS / "Contents.json").write_text(json.dumps({"info": {"author": "xcode", "version": 1}}, indent=2) + "\n")
    for name, value in BRAND.items():
        write(name, value, value)
    for name, (light, dark) in SEMANTIC.items():
        write(name, light, dark)
    for section, (light_bg, light_fg, dark_bg, dark_fg) in TAGS.items():
        write(f"tag{section}Bg", light_bg, dark_bg)
        write(f"tag{section}Fg", light_fg, dark_fg)
    accent = COLORS.parent / "AccentColor.colorset"
    accent.mkdir(parents=True, exist_ok=True)
    write_accent = {"colors": [
        {"color": components("#0F7D4D"), "idiom": "universal"},
        {"appearances": [{"appearance": "luminosity", "value": "dark"}], "color": components("#3CC98A"), "idiom": "universal"},
    ], "info": {"author": "xcode", "version": 1}}
    (accent / "Contents.json").write_text(json.dumps(write_accent, indent=2) + "\n")
    print(f"{len(list(COLORS.glob('*.colorset')))} color sets in {COLORS}")


if __name__ == "__main__":
    main()
