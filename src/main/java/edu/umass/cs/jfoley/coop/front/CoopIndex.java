package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import org.lemurproject.galago.utility.Parameters;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * @author jfoley
 */
public interface CoopIndex extends Closeable {
  CoopTokenizer getTokenizer();
  CoopDoc getDocument(int id);

  List<String> translateToTerms(IntList termIds) throws IOException;

  CoopDoc getDocument(String name);
  default PostingMover<PositionsList> getPositionsMover(String termKind, String queryTerm) throws IOException {
    TermPositionsIndex index = getPositionsIndex(termKind);
    if(index == null) return null;
    return index.getPositionsMover(queryTerm);
  }
  default PostingMover<PositionsList> getPositionsMover(String termKind, int queryTermId) throws IOException {
    TermPositionsIndex index = getPositionsIndex(termKind);
    if(index == null) return null;
    return index.getPositionsMover(queryTermId);
  }
  Iterable<Pair<Integer, String>> lookupNames(IntList hits) throws IOException;
  Iterable<Pair<TermSlice, IntList>> pullTermSlices(Iterable<TermSlice> slices);
  Iterable<Pair<String, Integer>> lookupTermIds(List<String> query) throws IOException;
  long getCollectionLength() throws IOException;
  Iterable<Pair<Integer, String>> lookupTerms(IntList termIds) throws IOException;
  IntList translateFromTerms(List<String> query) throws IOException;
  Parameters getMetadata();

  TermPositionsIndex getPositionsIndex(String termKind);

}
