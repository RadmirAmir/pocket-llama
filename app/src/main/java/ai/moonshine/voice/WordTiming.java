// Moonshine Voice 0.1.5, MIT. See notices/MOONSHINE.txt.
package ai.moonshine.voice;

public class WordTiming {
    public String word;
    public float start;
    public float end;
    public float confidence;

    public String toString() {
        return "WordTiming(word=" + word + ", start=" + start + ", end=" + end
                + ", confidence=" + confidence + ")";
    }
}
