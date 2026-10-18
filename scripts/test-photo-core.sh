#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test -r "${OCR_ORT_JAR:?Set OCR_ORT_JAR to the desktop ONNX Runtime 1.22.0 jar}"
test_dir="$project_dir/build/photo-core"
mkdir -p "$test_dir/classes" "$test_dir/images"
python3 - "$test_dir/images" <<'PY'
from pathlib import Path
import sys
from PIL import Image, ImageDraw, ImageFont
p=Path(sys.argv[1])
font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
im=Image.new('RGB',(1600,1100),'#fffefa');draw=ImageDraw.Draw(im)
rows=[(95,'Локальный диктофон Pocket',58),(220,'Встреча команды · 15 октября 2026',42),(340,'Решение: сохранить фото только на телефоне.',36),(435,'Задача: проверить русский и English текст.',36),(530,'Бюджет: 12 450 рублей. Срок: 20 октября.',36),(690,'Никаких облачных сервисов и подписок.',36)]
for y,text,size in rows: draw.text((90,y),text,font=ImageFont.truetype(font,size),fill='#252a27')
preview=p.parents[1]/'ocr';preview.mkdir(exist_ok=True);im.save(preview/'russian-page.png')
im.save(p/'page.png');im.rotate(7,expand=True,fillcolor='white').save(p/'tilted.png');Image.new('RGB',(800,600),'white').save(p/'blank.png')
PY
javac --release 11 -encoding UTF-8 -cp "$OCR_ORT_JAR" -d "$test_dir/classes" \
 "$project_dir"/app/src/main/java/dev/pocketllama/photo/{EncryptedFiles,OcrRaster,OcrGeometry,OcrEngine}.java \
 "$project_dir"/tests/{PhotoPrivacyTests,OcrHostProbe}.java
java -XX:-UsePerfData -cp "$test_dir/classes" PhotoPrivacyTests
for fixture in page tilted blank; do
 java -XX:-UsePerfData -cp "$test_dir/classes:$OCR_ORT_JAR" OcrHostProbe "$project_dir/app/src/main/assets/ocr" "$test_dir/images/$fixture.png" > "$test_dir/$fixture.txt" 2> "$test_dir/$fixture-ort.log"
done
python3 - "$test_dir" <<'PY'
from pathlib import Path
import sys
p=Path(sys.argv[1])
for name in ['page','tilted']:
 text=(p/(name+'.txt')).read_text()
 for expected in ['lines: 6','сохранить фото только на телефоне','12 450 рублей','20 октября','Никаких облачных сервисов']:
  assert expected in text,(name,expected,text)
assert 'lines: 0' in (p/'blank.txt').read_text()
print('Desktop Java OCR: Russian page, tilted page and blank image passed')
PY
