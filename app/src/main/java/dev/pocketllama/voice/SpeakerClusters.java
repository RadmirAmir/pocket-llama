package dev.pocketllama.voice;

import java.util.*;

/** Bounded-memory online centroids; confidence gates avoid forcing unknown voices.
 * A later reassignment uses final centroids. No N*N similarity matrix. */
public final class SpeakerClusters {
    private final int limit;
    private final ArrayList<float[]> centers=new ArrayList<>();
    private final ArrayList<Integer> counts=new ArrayList<>();
    public SpeakerClusters(int expected){limit=expected>0?Math.min(8,expected):12;}
    public int add(float[] vector) {
        float[] v=normalize(vector);int best=-1;double score=-2;
        for(int i=0;i<centers.size();i++){double s=dot(v,centers.get(i));if(s>score){score=s;best=i;}}
        if(best<0||(score<0.55&&centers.size()<limit)){centers.add(v);counts.add(1);return centers.size();}
        // Update only confident examples; mixed speech must not poison a centroid.
        if(score>=0.55){float[] c=centers.get(best);int n=counts.get(best);float alpha=1f/Math.min(24,n+1);for(int j=0;j<c.length;j++)c[j]=(1-alpha)*c[j]+alpha*v[j];centers.set(best,normalize(c));counts.set(best,n+1);}
        return score<0.30?0:best+1;
    }
    public Match match(float[] vector) {
        float[] v=normalize(vector);int best=0;double score=-2,second=-2;
        for(int i=0;i<centers.size();i++){double s=dot(v,centers.get(i));if(s>score){second=score;score=s;best=i+1;}else second=Math.max(second,s);}
        return new Match(score<0.35||score-second<0.04?0:best,score,score-second);
    }
    public int size(){return centers.size();}
    public static float[] normalize(float[] a){double norm=0;for(float v:a){if(!Float.isFinite(v))throw new IllegalArgumentException("Некорректный embedding");norm+=v*v;}if(norm<1e-12)throw new IllegalArgumentException("Пустой embedding");float[] r=a.clone();norm=Math.sqrt(norm);for(int i=0;i<r.length;i++)r[i]/=(float)norm;return r;}
    public static double dot(float[] a,float[] b){double s=0;for(int i=0;i<a.length;i++)s+=a[i]*b[i];return s;}
    public static final class Match {public final int speaker;public final double similarity,margin;Match(int s,double v,double m){speaker=s;similarity=v;margin=m;}}
}
