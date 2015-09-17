package edu.umass.cs.jfoley.coop.phrases;

import ciir.jfoley.chai.collections.list.CircularIntBuffer;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.LinesIterable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Contains an hashset for each candidate size while working through patterns that may match.
 * This allows rapid tagging of a large dictionary. (300k+ terms/second with 355k dbpedia titles.)
 *
 * Based on intutition in the <a href="https://en.wikipedia.org/wiki/Rabin%E2%80%93Karp_algorithm">Rabin%E2%80%93Karp_algorithm</a> but without a special rolling hash, for now.
 *
 * @author jfoley
 */
public class PhraseDetector {
  public ArrayList<HashSet<List<Integer>>> matchingBySize;
  public int N;

  public PhraseDetector(int N) {
    this.N = N;
    this.matchingBySize = new ArrayList<>();
    for (int i = 0; i < N; i++) {
      matchingBySize.add(new HashSet<>());
    }
  }

  public void addPattern(List<Integer> data) {
    int n = data.size()-1;
    if(n < 0 || n >= N) return;
    matchingBySize.get(n).add(data);
  }

  public boolean matches(List<Integer> query) {
    int n = query.size()-1;
    if(n < 0 || n >= N) return false;
    return matchingBySize.get(n).contains(query);
  }

  public interface PhraseMatchListener {
    void onPhraseMatch(int position, int size);
  }

  public int match(int[] data, PhraseMatchListener handler) {
    // create circular buffers for this document:
    ArrayList<CircularIntBuffer> patternBuffers = new ArrayList<>(N);
    for (int i = 0; i < N; i++) {
      patternBuffers.add(new CircularIntBuffer(i+1));
    }

    int hits = 0;
    for (int position = 0; position < data.length; position++) {
      int term = data[position];
      for (int i = 0; i < patternBuffers.size(); i++) {
        CircularIntBuffer buffer = patternBuffers.get(i);
        buffer.push(term);
        if (buffer.full() && matches(buffer)) {
          handler.onPhraseMatch(position-i, i+1);
          hits++;
        }
      }
    }
    return hits;
  }


  public static PhraseDetector loadFromTextFile(int N, File fp) throws IOException {
    PhraseDetector phrases = new PhraseDetector(N);
    Pattern spaces = Pattern.compile("\\s+");
    try (LinesIterable lines = LinesIterable.fromFile(fp)) {
      for (String line : lines) {
        String[] col = spaces.split(line);
        int n = col.length;
        if(n > N) continue;
        IntList pattern = new IntList(n);
        for (String str : col) {
          pattern.add(Integer.parseInt(str));
        }
        phrases.addPattern(pattern);
      }
    }
    return phrases;
  }

}
