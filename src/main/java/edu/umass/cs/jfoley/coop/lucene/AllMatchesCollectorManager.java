package edu.umass.cs.jfoley.coop.lucene;

import ciir.jfoley.chai.collections.list.IntList;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.Scorer;

import java.io.IOException;
import java.util.Collection;

/**
 * Collect all doc ids that match a query, ignoring scores.
 * @author jfoley
 */
public class AllMatchesCollectorManager implements CollectorManager<AllMatchesCollectorManager.MatchesCollector, IntList> {
  @Override
  public MatchesCollector newCollector() throws IOException {
    return new MatchesCollector();
  }

  @Override
  public IntList reduce(Collection<MatchesCollector> collectors) throws IOException {
    IntList finalResults = new IntList();
    for (MatchesCollector collector : collectors) {
      finalResults.pushAll(collector.results);
    }
    return finalResults;
  }

  static class MatchesCollector implements Collector {
    IntList results = new IntList();

    @Override
    public LeafCollector getLeafCollector(LeafReaderContext context) throws IOException {
      final int docBase = context.docBase;
      return new LeafCollector() {
        @Override public void setScorer(Scorer scorer) throws IOException { }

        @Override
        public void collect(int doc) throws IOException {
          results.push(docBase+doc);
        }
      };
    }

    @Override
    public boolean needsScores() {
      return false;
    }
  }
}
