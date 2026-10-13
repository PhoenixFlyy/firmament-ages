"""Turn the CurseForge description Markdown files into the HTML the Author Console's editor takes.

Covers what dev/cf/*.md use: ATX headings, paragraphs, bold, italic, inline code, links, bullet and numbered lists,
pipe tables, block quotes, horizontal rules and HTML comments (dropped). Not a general Markdown converter.

Usage: python dev/cf/md2html.py            (modpack-description.md and firmages-core-description.md -> .html)
       python dev/cf/md2html.py <file.md>  (that file -> same name with .html)
"""
import html
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
DEFAULT = [HERE / "modpack-description.md", HERE / "firmages-core-description.md"]

INLINE = [
    (re.compile(r"`([^`]+)`"), lambda m: f"<code>{m.group(1)}</code>"),     # text is already escaped
    (re.compile(r"\[([^\]]+)\]\(([^)]+)\)"), lambda m: f'<a href="{m.group(2)}">{m.group(1)}</a>'),
    (re.compile(r"\*\*(.+?)\*\*"), lambda m: f"<strong>{m.group(1)}</strong>"),
    (re.compile(r"(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])"), lambda m: f"<em>{m.group(1)}</em>"),
]


def inline(text: str) -> str:
    # protect code spans and links from escaping of their own characters: escape first, then apply the patterns
    out = html.escape(text, quote=False)
    out = out.replace("&quot;", '"')
    for pat, fn in INLINE:
        out = pat.sub(fn, out)
    return out


def convert(md: str) -> str:
    md = re.sub(r"<!--.*?-->", "", md, flags=re.S)
    lines = md.splitlines()
    out: list[str] = []
    para: list[str] = []
    i = 0

    def flush_para():
        if para:
            out.append("<p>" + inline(" ".join(s.strip() for s in para)) + "</p>")
            para.clear()

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if not stripped:
            flush_para()
            i += 1
            continue
        m = re.match(r"^(#{1,6})\s+(.*)$", stripped)
        if m:
            flush_para()
            level = len(m.group(1))
            out.append(f"<h{level}>{inline(m.group(2))}</h{level}>")
            i += 1
            continue
        if re.match(r"^(-{3,}|\*{3,})$", stripped):
            flush_para()
            out.append("<hr>")
            i += 1
            continue
        if stripped.startswith(">"):
            flush_para()
            quote = []
            while i < len(lines) and lines[i].strip().startswith(">"):
                quote.append(lines[i].strip()[1:].strip())
                i += 1
            out.append("<blockquote><p>" + inline(" ".join(quote)) + "</p></blockquote>")
            continue
        if stripped.startswith("|"):
            flush_para()
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                cells = [c.strip() for c in lines[i].strip().strip("|").split("|")]
                if not all(re.match(r"^:?-{2,}:?$", c) for c in cells):
                    rows.append(cells)
                i += 1
            head, body = rows[0], rows[1:]
            t = ["<table>", "<thead><tr>" + "".join(f"<th>{inline(c)}</th>" for c in head) + "</tr></thead>", "<tbody>"]
            for r in body:
                t.append("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in r) + "</tr>")
            t += ["</tbody>", "</table>"]
            out.extend(t)
            continue
        m = re.match(r"^(\s*)([-*]|\d+\.)\s+(.*)$", line)
        if m:
            flush_para()
            ordered = m.group(2)[0].isdigit()
            tag = "ol" if ordered else "ul"
            items = []
            while i < len(lines):
                m2 = re.match(r"^(\s*)([-*]|\d+\.)\s+(.*)$", lines[i])
                if m2 and (m2.group(2)[0].isdigit()) == ordered and len(m2.group(1)) == len(m.group(1)):
                    items.append(m2.group(3))
                    i += 1
                elif lines[i].strip() and lines[i].startswith(" ") and items:
                    items[-1] += " " + lines[i].strip()       # continuation line of the item
                    i += 1
                else:
                    break
            out.append(f"<{tag}>" + "".join(f"<li>{inline(s)}</li>" for s in items) + f"</{tag}>")
            continue
        para.append(line)
        i += 1
    flush_para()
    return "\n".join(out) + "\n"


def main(argv):
    files = [Path(a) for a in argv] or DEFAULT
    for src in files:
        dst = src.with_suffix(".html")
        dst.write_text(convert(src.read_text(encoding="utf-8")), encoding="utf-8")
        print(f"{src.name} -> {dst.name} ({dst.stat().st_size} bytes)")


if __name__ == "__main__":
    main(sys.argv[1:])
