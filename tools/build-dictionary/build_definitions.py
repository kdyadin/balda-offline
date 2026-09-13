#!/usr/bin/env python3
"""
Сборка базы толкований (SQLite) из дампа русского Викисловаря (CC-BY-SA 4.0).

Вход:
  work/ruwiktionary-latest-pages-articles.xml.bz2 — дамп (https://dumps.wikimedia.org/ruwiktionary/latest/)
  ../../app/src/main/assets/words.bin              — словарь игры: толкования берём только для его слов

Выход:
  dist/definitions.db         — SQLite: таблица definitions(word TEXT PRIMARY KEY, defs TEXT), meta(key, value)
  dist/definitions.db.sha256  — контрольная сумма (hex) для проверки в приложении

Формат: для каждого слова до 3 значений, каждое — 1–2 предложения, разделитель значений — перевод строки.
Ключ — лемма в нижнем регистре с Ё→Е.

Запуск:  python build_definitions.py [--dump путь] [--download]
Полный дамп (~340 МБ bz2) обрабатывается примерно 10–20 минут.
"""
from __future__ import annotations

import argparse
import bz2
import hashlib
import re
import sqlite3
import struct
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent
WORK = HERE / "work"
DIST = HERE / "dist"
ASSETS = HERE.parent.parent / "app" / "src" / "main" / "assets"
DUMP_URL = "https://dumps.wikimedia.org/ruwiktionary/latest/ruwiktionary-latest-pages-articles.xml.bz2"
ALPHABET = "абвгдежзийклмнопрстуфхцчшщъыьэюя"
MAX_MEANINGS = 3
MAX_SENTENCES = 2
MAX_MEANING_CHARS = 220

NS = "{http://www.mediawiki.org/xml/export-0.11/}"


def normalize(word: str) -> str:
    return word.strip().lower().replace("ё", "е")


def read_words_bin(path: Path) -> set[str]:
    data = path.read_bytes()
    assert data[:4] == b"BLDW", "не words.bin"
    (count,) = struct.unpack(">i", data[5:9])
    pos = 9
    words = set()
    for _ in range(count):
        n = data[pos]
        pos += 1
        words.add("".join(ALPHABET[b] for b in data[pos:pos + n]))
        pos += n
    return words


# ---------- разбор вики-разметки ----------

RU_SECTION_RE = re.compile(r"^=\s*\{\{-ru-(?:\|[^}]*)?\}\}\s*=\s*$", re.M)
ANY_LANG_SECTION_RE = re.compile(r"^=\s*\{\{-[a-z\-]+-(?:\|[^}]*)?\}\}\s*=\s*$", re.M)
MEANING_HEADER_RE = re.compile(r"^={3,4}\s*Значение\s*={3,4}\s*$", re.M)
NEXT_HEADER_RE = re.compile(r"^={2,4}\s*[^=\n]+?\s*={2,4}\s*$", re.M)
TEMPLATE_RE = re.compile(r"\{\{([^{}]*)\}\}")
LINK_RE = re.compile(r"\[\[([^\]|]*)(?:\|([^\]]*))?\]\]")
EXT_LINK_RE = re.compile(r"\[https?://[^\s\]]+(?:\s+([^\]]*))?\]")
REF_RE = re.compile(r"<ref[^>]*>.*?</ref>|<ref[^>]*/>", re.S)
TAG_RE = re.compile(r"<[^>]+>")
SENT_SPLIT_RE = re.compile(r"(?<=[.!?…])\s+(?=[А-ЯЁA-Z«\"(])")
SPACE_RE = re.compile(r"\s+")

# Пометы, которые стоит сохранить как текст (сокращённо), остальные шаблоны выбрасываем.
KEEP_LABELS = {
    "разг.": "разг.", "устар.": "устар.", "перен.": "перен.", "прост.": "прост.", "книжн.": "книжн.",
    "спец.": "спец.", "жарг.": "жарг.", "мед.": "мед.", "биол.": "биол.", "техн.": "техн.", "хим.": "хим.",
    "физ.": "физ.", "матем.": "матем.", "истор.": "истор.", "религ.": "религ.", "муз.": "муз.", "воен.": "воен.",
    "бот.": "бот.", "зоол.": "зоол.", "геогр.": "геогр.", "юр.": "юр.", "фин.": "фин.", "полит.": "полит.",
    "экон.": "экон.", "лингв.": "лингв.", "спорт.": "спорт.", "кулин.": "кулин.", "неодуш.": "", "одуш.": "",
    "уменьш.": "уменьш.", "ласк.": "ласк.", "пренебр.": "пренебр.", "ирон.": "ирон.", "шутл.": "шутл.",
    "офиц.": "офиц.", "поэт.": "поэт.", "рег.": "рег.", "диал.": "диал.", "неол.": "неол.", "собир.": "собир.",
}


def render_template(m: re.Match) -> str:
    body = m.group(1)
    parts = body.split("|")
    name = parts[0].strip().lower()
    if name in ("пример", "семантика", "прим", "помета", "пометы", "илл", "выдел", "выдел2", "нп", "cite", "источник",
                "wikipedia", "w", "l", "lang", "unicode", "font", "ссылки", "-", "==", "?"):
        return ""
    if name == "помета" or name.endswith("."):
        return KEEP_LABELS.get(name, "")
    if name in KEEP_LABELS:
        return KEEP_LABELS[name]
    if name in ("действие", "свойство", "состояние", "результат", "процесс", "качество"):
        # {{действие|глагол|...}} — «действие по значению гл. глагол»
        arg = parts[1].strip() if len(parts) > 1 else ""
        label = {"действие": "действие по значению гл.", "свойство": "свойство по значению прил.",
                 "состояние": "состояние по значению прил.", "результат": "результат действия по значению гл.",
                 "процесс": "процесс по значению гл.", "качество": "качество по значению прил."}[name]
        return f"{label} {arg}" if arg else ""
    if name in ("уменьш.", "ласк.", "увел.", "уменьш.-ласк."):
        arg = parts[1].strip() if len(parts) > 1 else ""
        return f"{name} к {arg}" if arg else name
    if name in ("=", "same"):
        arg = parts[1].strip() if len(parts) > 1 else ""
        return f"то же, что {arg}" if arg else ""
    if name in ("t", "term", "итд", "и т. д."):
        return parts[1].strip() if len(parts) > 1 else ""
    if name == "гипокор." or name == "гипокор":
        return "уменьшительное к " + (parts[1].strip() if len(parts) > 1 else "")
    if name in ("as ru", "по-русски"):
        return parts[1] if len(parts) > 1 else ""
    if name.startswith("напр"):
        return ""
    # неизвестный шаблон: если единственный аргумент — текст, оставим его
    if len(parts) == 2 and "=" not in parts[1] and len(parts[1]) < 40:
        return parts[1].strip()
    return ""


def clean_meaning(line: str) -> str:
    text = line
    text = REF_RE.sub("", text)
    for _ in range(4):  # вложенные шаблоны
        text, n = TEMPLATE_RE.subn(render_template, text)
        if n == 0:
            break
    text = LINK_RE.sub(lambda m: m.group(2) if m.group(2) else m.group(1), text)
    text = EXT_LINK_RE.sub(lambda m: m.group(1) or "", text)
    text = TAG_RE.sub("", text)
    # незакрытые/слишком глубокие шаблоны — отрезаем вместе со всем хвостом
    text = text.split("{{", 1)[0]
    text = text.replace("'''", "").replace("''", "")
    text = text.replace("&nbsp;", " ").replace("&mdash;", "—").replace("&ndash;", "–")
    text = re.sub(r"\[\d+\]", "", text)          # ссылки на значения «[1]»
    text = re.sub(r"\(\s*\)", "", text)          # пустые скобки после удалённых шаблонов
    text = re.sub(r"\s*\|\|\s*", "; ", text)     # «||» — разделитель подзначений
    text = re.sub(r"(\s*,\s*){2,}", ", ", text)  # двойные запятые от удалённых помет
    text = re.sub(r"\s+([,;:.])", r"\1", text)
    text = SPACE_RE.sub(" ", text).strip(" ;:,-–—◆")
    # выкинуть остатки вроде «◆ пример…»
    text = text.split("◆", 1)[0].strip()
    if not text or text in ("?", "…"):
        return ""
    # одна помета без толкования («Зоол.», «Разг.») — бесполезна
    if len(text) < 12 and text.rstrip(".").lower() + "." in KEEP_LABELS:
        return ""
    if len(text) < 4:
        return ""
    sentences = SENT_SPLIT_RE.split(text)
    text = " ".join(sentences[:MAX_SENTENCES]).strip()
    if len(text) > MAX_MEANING_CHARS:
        text = text[:MAX_MEANING_CHARS - 1].rsplit(" ", 1)[0] + "…"
    if text and text[0].islower():
        text = text[0].upper() + text[1:]
    if text and text[-1] not in ".!?…":
        text += "."
    return text


def extract_meanings(wikitext: str) -> list[str]:
    m = RU_SECTION_RE.search(wikitext)
    if not m:
        return []
    start = m.end()
    nxt = ANY_LANG_SECTION_RE.search(wikitext, start)
    ru = wikitext[start:nxt.start() if nxt else len(wikitext)]
    meanings: list[str] = []
    for h in MEANING_HEADER_RE.finditer(ru):
        sec_start = h.end()
        nh = NEXT_HEADER_RE.search(ru, sec_start)
        section = ru[sec_start:nh.start() if nh else len(ru)]
        for raw in section.splitlines():
            raw = raw.strip()
            if not raw.startswith("#") or raw.startswith("#:") or raw.startswith("#*"):
                continue
            body = raw.lstrip("#").strip()
            if not body:
                continue
            text = clean_meaning(body)
            if text and text not in meanings:
                meanings.append(text)
            if len(meanings) >= MAX_MEANINGS:
                return meanings
    return meanings


def iter_pages(dump: Path):
    """Потоковый разбор дампа: (title, text) для страниц основного пространства имён."""
    with bz2.open(dump, "rb") as f:
        title = None
        ns = None
        for event, elem in ET.iterparse(f, events=("end",)):
            tag = elem.tag
            if tag == NS + "title":
                title = elem.text or ""
            elif tag == NS + "ns":
                ns = elem.text
            elif tag == NS + "text":
                if ns == "0" and title:
                    yield title, elem.text or ""
            elif tag == NS + "page":
                elem.clear()
                title = None
                ns = None


def download(url: str, target: Path) -> None:
    if target.exists():
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    print(f"Скачиваю {url} → {target} (это ~340 МБ)")
    urllib.request.urlretrieve(url, target)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dump", type=Path, default=WORK / "ruwiktionary-latest-pages-articles.xml.bz2")
    parser.add_argument("--words", type=Path, default=ASSETS / "words.bin")
    parser.add_argument("--out", type=Path, default=DIST / "definitions.db")
    parser.add_argument("--download", action="store_true")
    parser.add_argument("--limit", type=int, default=0, help="обработать только первые N страниц (для отладки)")
    args = parser.parse_args()

    if args.download:
        download(DUMP_URL, args.dump)
    if not args.dump.exists():
        print(f"Нет дампа {args.dump}. Запустите с --download.", file=sys.stderr)
        return 1
    words = read_words_bin(args.words)
    print(f"Слов в словаре игры: {len(words)}")

    args.out.parent.mkdir(parents=True, exist_ok=True)
    if args.out.exists():
        args.out.unlink()
    db = sqlite3.connect(args.out)
    db.execute("PRAGMA page_size = 4096")
    db.execute("CREATE TABLE definitions (word TEXT PRIMARY KEY, defs TEXT NOT NULL) WITHOUT ROWID")
    db.execute("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")

    found = 0
    pages = 0
    started = time.time()
    batch = []
    seen: set[str] = set()
    for title, text in iter_pages(args.dump):
        pages += 1
        if args.limit and pages > args.limit:
            break
        if pages % 100000 == 0:
            print(f"  страниц: {pages}, толкований: {found}, {time.time() - started:.0f} с")
        key = normalize(title)
        if key not in words or key in seen:
            continue
        meanings = extract_meanings(text)
        if not meanings:
            continue
        seen.add(key)
        batch.append((key, "\n".join(meanings)))
        found += 1
        if len(batch) >= 1000:
            db.executemany("INSERT OR IGNORE INTO definitions VALUES (?, ?)", batch)
            batch.clear()
    if batch:
        db.executemany("INSERT OR IGNORE INTO definitions VALUES (?, ?)", batch)
    db.executemany("INSERT INTO meta VALUES (?, ?)", [
        ("source", "Русский Викисловарь (ru.wiktionary.org), дамп pages-articles"),
        ("license", "CC BY-SA 4.0"),
        ("built", time.strftime("%Y-%m-%d")),
        ("format", "1"),
        ("count", str(found)),
    ])
    db.commit()
    db.execute("VACUUM")
    db.close()

    digest = hashlib.sha256(args.out.read_bytes()).hexdigest()
    sha_path = args.out.with_name(args.out.name + ".sha256")
    sha_path.write_text(digest + "\n", encoding="utf-8")
    size = args.out.stat().st_size
    print(f"Готово: {found} толкований из {pages} страниц за {time.time() - started:.0f} с")
    print(f"{args.out}: {size / 1024 / 1024:.1f} МБ, sha256 {digest}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
