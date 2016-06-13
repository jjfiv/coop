package edu.umass.cs.jfoley.coop;

import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.TermStatistics;

/**
 * @author jfoley
 */
public class CountStats {

  public long collectionFrequency;
  public long documentFrequency;

  /** Length of the collection, in terms or tokens. */
  public long collectionLength;
  /** Length of the collection, in number of documents. */
  public long totalDocumentCount;

  public CountStats() {
    this(0,0,0,0);
  }
  public CountStats(long collectionFrequency, long documentFrequency, long collectionLength, long totalDocumentCount) {
    this.collectionFrequency = collectionFrequency;
    this.documentFrequency = documentFrequency;
    this.collectionLength = collectionLength;
    this.totalDocumentCount = totalDocumentCount;
  }

  public CountStats(CollectionStatistics cstats, TermStatistics tstats) {
    this(tstats.totalTermFreq(), tstats.docFreq(), cstats.sumTotalTermFreq(), cstats.maxDoc());
  }

  public void add(CountStats rhs) {
    this.collectionFrequency += rhs.collectionFrequency;
    this.documentFrequency += rhs.documentFrequency;

    this.collectionLength += rhs.collectionLength;
    this.totalDocumentCount += rhs.totalDocumentCount;
  }

  public double averageDocumentLength() {
    return collectionLength / (double) totalDocumentCount;
  }
}