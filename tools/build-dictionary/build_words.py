#!/usr/bin/env python3
"""
Сборка словаря проверки слов и списка стартовых слов для игры «Балда».

Вход:
  work/russian_nouns.txt  — список нарицательных существительных в начальной форме
                            (Harrix/Russian-Nouns, MIT; список получен из морфологии OpenCorpora, CC-BY-SA)
  work/ru_50k.txt         — частотный список (hermitdave/FrequencyWords, CC-BY-SA 4.0),
                            нужен только для отбора «обычных» стартовых слов
  stopwords.txt           — ручные исключения (аббревиатуры и т. п.)

Выход:
  ../../app/src/main/assets/words.bin       — бинарный словарь (формат см. DictionaryFormat.kt)
  ../../app/src/main/assets/start_words.txt — стартовые слова длиной 5, 6, 7

Запуск:  python build_words.py [--download]
"""
from __future__ import annotations

import argparse
import re
import struct
import sys
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
WORK = HERE / "work"
ASSETS = HERE.parent.parent / "app" / "src" / "main" / "assets"

NOUNS_URL = "https://raw.githubusercontent.com/Harrix/Russian-Nouns/master/dist/russian_nouns.txt"
FREQ_URL = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/ru/ru_50k.txt"

ALPHABET = "абвгдежзийклмнопрстуфхцчшщъыьэюя"  # 32 буквы, без ё
LETTER_INDEX = {c: i for i, c in enumerate(ALPHABET)}
WORD_RE = re.compile(r"^[а-яё]+$")
VOWELS = set("аеиоуыэюя")

MIN_LEN = 2
MAX_LEN = 49          # 7×7 — максимальная длина слова на самом большом поле
START_LENGTHS = (5, 6, 7)
START_FREQ_RANK = 30000  # стартовое слово должно входить в 30 000 самых частых словоформ


def normalize(word: str) -> str:
    return word.strip().lower().replace("ё", "е")


def download(url: str, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        return
    print(f"Скачиваю {url} → {target}")
    with urllib.request.urlopen(url, timeout=120) as resp, target.open("wb") as out:
        out.write(resp.read())


def load_stopwords() -> set[str]:
    path = HERE / "stopwords.txt"
    if not path.exists():
        return set()
    words = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].strip()
        if line:
            words.add(normalize(line))
    return words


def is_acceptable(word: str) -> bool:
    """Нарицательное существительное без дефисов, из русских букв, разумной длины, хотя бы с одной гласной."""
    if not WORD_RE.match(word):
        return False
    if not (MIN_LEN <= len(word) <= MAX_LEN):
        return False
    if not (set(word) & VOWELS):
        return False  # «вгтрк» и т. п. — аббревиатуры
    return True


def load_nouns(path: Path, stop: set[str]) -> set[str]:
    words: set[str] = set()
    dropped = 0
    for raw in path.read_text(encoding="utf-8").splitlines():
        raw = raw.strip()
        if not raw or raw[0].isupper():
            dropped += 1  # имена собственные пишутся с заглавной
            continue
        w = normalize(raw)
        if not is_acceptable(w) or w in stop:
            dropped += 1
            continue
        words.add(w)
    print(f"Существительных принято: {len(words)}, отброшено: {dropped}")
    return words


def load_frequency(path: Path) -> dict[str, int]:
    ranks: dict[str, int] = {}
    if not path.exists():
        print("Частотный список не найден — стартовые слова будут выбраны без фильтра по частоте")
        return ranks
    for i, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        parts = line.split()
        if parts:
            ranks.setdefault(normalize(parts[0]), i)
    return ranks


def write_words_bin(words: set[str], target: Path) -> None:
    sorted_words = sorted(words)  # порядок по кодовым точкам — такой же, как String.compareTo в Kotlin
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("wb") as out:
        out.write(b"BLDW")
        out.write(struct.pack(">B", 1))
        out.write(struct.pack(">i", len(sorted_words)))
        for w in sorted_words:
            out.write(struct.pack(">B", len(w)))
            out.write(bytes(LETTER_INDEX[c] for c in w))
    print(f"words.bin: {len(sorted_words)} слов, {target.stat().st_size / 1024:.0f} КБ")


def select_start_words(words: set[str], ranks: dict[str, int]) -> list[str]:
    result = []
    for w in sorted(words):
        if len(w) not in START_LENGTHS:
            continue
        rank = ranks.get(w)
        if ranks and (rank is None or rank > START_FREQ_RANK):
            continue
        result.append(w)
    by_len = {n: sum(1 for w in result if len(w) == n) for n in START_LENGTHS}
    print(f"Стартовых слов: {len(result)} (по длинам: {by_len})")
    for n in START_LENGTHS:
        if by_len[n] < 50:
            print(f"ВНИМАНИЕ: мало стартовых слов длины {n}", file=sys.stderr)
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--download", action="store_true", help="скачать исходные файлы, если их нет в work/")
    parser.add_argument("--nouns", type=Path, default=WORK / "russian_nouns.txt")
    parser.add_argument("--freq", type=Path, default=WORK / "ru_50k.txt")
    parser.add_argument("--assets", type=Path, default=ASSETS)
    args = parser.parse_args()

    if args.download:
        download(NOUNS_URL, args.nouns)
        download(FREQ_URL, args.freq)
    if not args.nouns.exists():
        print(f"Нет файла {args.nouns}. Запустите с --download или положите файл вручную.", file=sys.stderr)
        return 1

    stop = load_stopwords()
    words = load_nouns(args.nouns, stop)
    ranks = load_frequency(args.freq)

    write_words_bin(words, args.assets / "words.bin")
    start_words = select_start_words(words, ranks)
    (args.assets / "start_words.txt").write_text("\n".join(start_words) + "\n", encoding="utf-8", newline="\n")
    print(f"start_words.txt записан в {args.assets}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
