package edu.umass.cs.jfoley.coop.index.corpus;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.fn.SinkFn;
import edu.umass.cs.jfoley.coop.querying.TermSlice;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author jfoley
 */
public abstract class AbstractCorpusReader implements Closeable {
  public abstract List<String> pullTokens(int document);

  /**
   * Raw access to corpus structure.
   * @param slice the coordinates of the data to pull.
   * @return a list of term ids corresponding to the data in the given slice.
   */
  public List<String> pullTokens(TermSlice slice) {
    // Inefficient if you have a tiled corpus, but a good default impl.
    return new ArrayList<>(
        ListFns.slice(
            pullTokens(slice.document),
            slice.start,
            slice.end));
  }


  /**
   * Only pull each document once.
   */
  public List<Pair<TermSlice, List<String>>> pullTermSlices(List<TermSlice> requests) throws IOException {
    // sort by document.
    Collections.sort(requests);

    // cache a document a time in memory so we don't pull it twice.
    List<String> currentDocTerms = null;
    int currentDocId = -1;

    // slices:
    List<List<String>> slices = new ArrayList<>();
    for (TermSlice request : requests) {
      if(request.document != currentDocId) {
        currentDocId = request.document;
        currentDocTerms = pullTokens(currentDocId);
      }
      // make a copy here so that the documents don't end up leaked into memory via subList.
      slices.add(new ArrayList<>(ListFns.slice(currentDocTerms, request.start, request.end)));
    }
    return ListFns.zip(requests, slices);
  }

  public void forTermInSlice(List<TermSlice> requests, SinkFn<String> onTerm) {
    // sort by document.
    Collections.sort(requests);

    // cache a document a time in memory so we don't pull it twice.
    List<String> currentDocTerms = null;
    int currentDocId = -1;

    // slices:
    for (TermSlice request : requests) {
      if(request.document != currentDocId) {
        currentDocId = request.document;
        currentDocTerms = pullTokens(currentDocId);
      }
      for (String term : ListFns.slice(currentDocTerms, request.start, request.end)) {
        onTerm.process(term);
      }
    }
  }
}
