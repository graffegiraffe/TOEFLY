"""PDF text layer through poppler (docs/08-content-pipeline.md section 4.1)."""

import re
import subprocess
from pathlib import Path

from .text import normalise

_PAGE_NUMBER = re.compile(r"^\s*\d{1,3}\s*$")


def pages(pdf: Path) -> list[str]:
    """Text of every page with the layout kept; form feeds separate pages."""
    out = subprocess.run(["pdftotext", "-layout", str(pdf), "-"], capture_output=True, text=True, check=True).stdout
    return out.split("\f")


def clean(pages_: list[str], header_markers: tuple[str, ...]) -> str:
    """Joins pages without running headers, page numbers and zero-width marks."""
    lines: list[str] = []
    for page in pages_:
        for line in page.replace("​", "").splitlines():
            stripped = line.strip()
            if stripped.startswith(header_markers) or stripped == "TST Prep" or _PAGE_NUMBER.match(line):
                continue
            lines.append(line.rstrip())
    return "\n".join(lines)


def unwrap(lines: list[str]) -> str:
    """Joins wrapped lines of one paragraph into a single normalised string."""
    return normalise(" ".join(part.strip() for part in lines if part.strip()))
