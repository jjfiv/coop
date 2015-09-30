package edu.umass.cs.jfoley.coop.phrases;

import ciir.jfoley.chai.collections.list.CircularIntBuffer;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Contains an hashset for each candidate size while working through patterns that may match.
 * This allows rapid tagging of a large dictionary. (300k+ terms/second with 355k dbpedia titles.)
 *
 * Based on intutition in the <a href="https://en.wikipedia.org/wiki/Rabin%E2%80%93Karp_algorithm">Rabin%E2%80%93Karp_algorithm</a> but without a special rolling hash, for now.
 *
 * @author jfoley
 */
public class PhraseDetector {
  public ArrayList<HashMap<List<Integer>, Integer>> matchingBySize;
  public int N;

  public PhraseDetector(int N) {
    this.N = N;
    this.matchingBySize = new ArrayList<>();
    for (int i = 0; i < N; i++) {
      matchingBySize.add(new HashMap<>());
    }
  }

  public void addPattern(IntList data, int id) {
    int n = data.size()-1;
    if(n < 0 || n >= N) return;
    // use the first matching if duplicate patterns exist:
    matchingBySize.get(n).putIfAbsent(data, id);
  }

  public Integer getMatch(List<Integer> query) {
    int n = query.size()-1;
    if(n < 0 || n >= N) return null;
    return matchingBySize.get(n).get(query);
  }

  public boolean matches(List<Integer> query) {
    return getMatch(query) != null;
  }

  public interface PhraseMatchListener {
    void onPhraseMatch(int phraseId, int position, int size);
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
      // tag backwards, so that we get the longest/earliest patterns first
      for (int i = patternBuffers.size() - 1; i >= 0; i--) {
        CircularIntBuffer buffer = patternBuffers.get(i);
        buffer.push(term);
        if (buffer.full()) {
          Integer match = getMatch(buffer);
          if(match != null) {
            handler.onPhraseMatch(match, position - i, i + 1);
            hits++;
          }
        }
      }
    }
    return hits;
  }

  @Override
  public String toString() {
    return ListFns.map(matchingBySize, HashMap::size).toString();
  }
}
