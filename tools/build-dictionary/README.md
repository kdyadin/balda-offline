# Сборка словарей

Два независимых скрипта на Python 3.10+ без сторонних зависимостей.

| Скрипт | Что делает | Результат |
|---|---|---|
| `build_words.py` | Словарь проверки слов и список стартовых слов | `app/src/main/assets/words.bin`, `app/src/main/assets/start_words.txt` |
| `build_definitions.py` | База толкований из дампа Викисловаря | `dist/definitions.db`, `dist/definitions.db.sha256` |

## Словарь проверки слов (`words.bin`)

### Источники

- **Список существительных** — [Harrix/Russian-Nouns](https://github.com/Harrix/Russian-Nouns), файл `dist/russian_nouns.txt`.
  Лицензия MIT. Список получен автором из морфологического словаря
  [OpenCorpora](http://opencorpora.org/) (CC BY-SA 4.0): нарицательные существительные в начальной форме
  (именительный падеж, единственное число; для pluralia tantum — множественное).
- **Частотный список** — [hermitdave/FrequencyWords](https://github.com/hermitdave/FrequencyWords),
  `content/2018/ru/ru_50k.txt` (подсчёт по субтитрам OpenSubtitles). Лицензия CC BY-SA 4.0.
  Используется только для отбора стартовых слов: в стартовые попадают существительные,
  входящие в 30 000 самых частых словоформ, чтобы на поле не оказалось «абазин» или «эпизоотия».

### Фильтрация

1. Строки с заглавной буквы (имена собственные) — отбрасываются.
2. Только буквы а–я и ё, без дефисов, апострофов и латиницы.
3. Длина от 2 до 49 букв (7×7 — максимальное поле).
4. Хотя бы одна гласная (отсекает аббревиатуры вроде «вгтрк»).
5. Ручной стоп-лист `stopwords.txt` (аббревиатуры: вуз, загс и т. п.).
6. Ё → Е, дубликаты после нормализации удаляются.

### Формат `words.bin`

```
"BLDW"          4 байта, сигнатура
version         1 байт  = 1
count           4 байта, big-endian
для каждого слова в отсортированном порядке (по кодовым точкам):
  len           1 байт
  len байт      индексы букв в алфавите «абвгдежзийклмнопрстуфхцчшщъыьэюя» (0..31)
```

Читает его `core/src/main/kotlin/com/kdyadin/balda/core/dictionary/DictionaryFormat.kt`.
Загрузка ~50 000 слов занимает десятки миллисекунд, проверка слова — бинарный поиск.

### Пересборка

```bash
cd tools/build-dictionary
python build_words.py --download      # скачает исходники в work/ и перепишет assets
cd ../.. && ./gradlew :core:test      # тесты производительности проверяют собранный словарь
```

## База толкований (`definitions.db`)

### Источник

Дамп русского Викисловаря `ruwiktionary-latest-pages-articles.xml.bz2`
(https://dumps.wikimedia.org/ruwiktionary/latest/, ~340 МБ). Лицензия текстов — CC BY-SA 4.0.

### Что извлекается

Для каждой страницы основного пространства имён, чьё название есть в `words.bin`:
раздел `{{-ru-}}` → подраздел «Значение» → строки, начинающиеся с `#`.
Вики-разметка вычищается (шаблоны помет частично сохраняются как текст: «разг.», «перен.» и т. п.,
примеры употребления `{{пример|…}}` удаляются). Берётся не более 3 значений, каждое — до 2 предложений
и 220 символов.

### Схема

```sql
CREATE TABLE definitions (word TEXT PRIMARY KEY, defs TEXT NOT NULL) WITHOUT ROWID;  -- defs: значения через '\n'
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);  -- source, license, built, format, count
```

Ключ `word` — лемма в нижнем регистре с Ё→Е, как в `words.bin`.

### Пересборка и публикация

```bash
cd tools/build-dictionary
python build_definitions.py --download   # 10–20 минут на полном дампе
ls dist/                                  # definitions.db, definitions.db.sha256
```

Затем выложить оба файла как assets релиза GitHub (по умолчанию тег `definitions-v1`)
и, если адрес другой, поправить `balda.definitionsUrl` и `balda.definitionsSha256Url` в `gradle.properties`.
Приложение скачивает файл с докачкой по `Range`, сверяет SHA-256 и только после этого подменяет базу.
