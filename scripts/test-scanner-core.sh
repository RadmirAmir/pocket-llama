#!/usr/bin/env bash
set -euo pipefail
scanner_project="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test -r "${CHAT_JSON_JAR:?Set CHAT_JSON_JAR to the desktop org.json jar}"
scanner_build="$scanner_project/build/scanner-core"
mkdir -p "$scanner_build/classes"
scanner_cp="$CHAT_JSON_JAR:$scanner_project/app/libs/jsoup-1.21.2.jar"
javac --release 11 -encoding UTF-8 -cp "$scanner_cp" -d "$scanner_build/classes" \
 "$scanner_project"/app/src/main/java/dev/pocketllama/photo/{ScanGeometry,SuryaProtocol,SuryaPageHtml,SuryaImageSize,SuryaInputProgress,SuryaOutputGuard,SuryaDeadline,SafeOcrHtml,ScanPdf,ScanXlsx,EncryptedFiles}.java \
 "$scanner_project"/tests/{ScannerCoreTests,PhotoPrivacyTests,SuryaNativeResults,SuryaGuardTests}.java
java -cp "$scanner_build/classes:$scanner_cp" ScannerCoreTests "$scanner_build" "$scanner_project/tests/fixtures/scan-page.jpg" "$scanner_project/app/src/main/assets/fonts/Manrope.ttf"
java -cp "$scanner_build/classes:$scanner_cp" SuryaGuardTests
java -cp "$scanner_build/classes" PhotoPrivacyTests
mkdir -p "$scanner_project/build/scanner-store/classes"
javac --release 11 -encoding UTF-8 -cp "$CHAT_JSON_JAR:$scanner_project/app/libs/onnxruntime-android-1.23.2.jar" -d "$scanner_project/build/scanner-store/classes" \
 "$scanner_project"/tests/scanner-stubs/android/{content/Context,app/ActivityManager}.java \
 "$scanner_project/tests/scanner-stubs/dev/pocketllama/photo/PhotoKey.java" \
 "$scanner_project"/app/src/main/java/dev/pocketllama/photo/{PhotoStore,DocumentStore,ScanGeometry,EncryptedFiles,OcrEngine,OcrRaster,OcrGeometry}.java "$scanner_project/tests/ScannerStoreTests.java"
java -cp "$scanner_project/build/scanner-store/classes:$CHAT_JSON_JAR" ScannerStoreTests
mkdir -p "$scanner_project/build/scanner-pipeline/classes"
javac --release 11 -encoding UTF-8 -cp "$scanner_cp:$scanner_project/app/libs/onnxruntime-android-1.23.2.jar" -d "$scanner_project/build/scanner-pipeline/classes" \
 "$scanner_project"/tests/scanner-stubs/android/{content/Context,app/ActivityManager}.java \
 "$scanner_project/tests/scanner-stubs/dev/pocketllama/photo/PhotoKey.java" \
 "$scanner_project"/tests/pipeline-stubs/android/{graphics/Bitmap,os/SystemClock}.java \
 "$scanner_project"/tests/pipeline-stubs/dev/pocketllama/photo/*.java \
 "$scanner_project"/app/src/main/java/dev/pocketllama/photo/{PhotoStore,DocumentStore,ScanGeometry,EncryptedFiles,OcrEngine,OcrRaster,OcrGeometry,SuryaPipeline,SuryaProtocol,SuryaPageHtml,SuryaImageSize,SuryaInputProgress,SuryaOutputGuard,SafeOcrHtml}.java \
 "$scanner_project/tests/SuryaPipelineTests.java"
java -cp "$scanner_project/build/scanner-pipeline/classes:$scanner_cp" SuryaPipelineTests
python3 - "$scanner_build" <<'PY'
import sys
from pathlib import Path
import fitz,openpyxl
p=Path(sys.argv[1]);doc=fitz.open(p/'document.pdf');assert len(doc)==2
for page in doc:
 text=page.get_text();assert all(s in text for s in ['Счёт № 7','Бумага: 500 рублей','Invoice / Test']);page.get_pixmap()
w=openpyxl.load_workbook(p/'tables.xlsx');s=w.active
assert s['A1'].value=='Товар' and s['C2'].data_type=='s' and s['C2'].value.startswith('=HYPERLINK')
assert {'A1:A2','B1:C1'}==set(map(str,s.merged_cells))
print('PDF search/rasterization and XLSX merged cells/inline strings passed')
PY
