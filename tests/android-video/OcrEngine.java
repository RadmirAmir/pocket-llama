package dev.pocketllama.photo;
/** Metadata shape only: this fixture tests persisted OCR text, not the ONNX recognizer. */
public final class OcrEngine {
    public static final class Line {
        public final String text;public final double score;public final double[] box;
        public Line(String text,double score,double[] box){this.text=text;this.score=score;this.box=box;}
    }
}
