package edu.umass.cs.jfoley.coop.lucene;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.util.BytesRef;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * @author jfoley
 */
public class OneDocPerTerm implements CollectorManager<OneDocPerTerm.OneSentencePerDocCollector, List<OneDocPerTerm.TermScoredDoc>> {
  private final List<Term> documentsAsTerms;
  private final boolean firstMatchOnly;

  public OneDocPerTerm(List<Term> documentsAsTerms, boolean firstMatchOnly) {
    this.documentsAsTerms = documentsAsTerms;
    this.firstMatchOnly = firstMatchOnly;
  }

  @Override
  public OneSentencePerDocCollector newCollector() throws IOException {
    return new OneSentencePerDocCollector(documentsAsTerms, firstMatchOnly);
  }

  @Override
  public List<TermScoredDoc> reduce(Collection<OneSentencePerDocCollector> collectors) throws IOException {
    HashMap<Term, TermScoredDoc> bestByDocument = new HashMap<>();

    for (OneSentencePerDocCollector collector : collectors) {
      collector.scorePerPage.forEach((term, result) -> {
        final TermScoredDoc before = bestByDocument.get(term);
        if(before == null) {
          bestByDocument.put(term, result);
        } else if(result.score > before.score) {
          // overwrite if this is a better score:
          before.doc = result.doc;
          before.score = result.score;
        }
      });
    }

    return new ArrayList<>(bestByDocument.values());
  }

  public static class TermScoredDoc {
    public final Term term;
    public int doc;
    public float score;

    public TermScoredDoc(Term term) {
      this.term = term;
      this.doc = -1;
      this.score = -Float.MAX_VALUE;
    }
  }

  public static class TermHits extends DocIdSetIterator implements Comparable<TermHits> {
    final Term what;
    final PostingsEnum postings;

    public TermHits(Term what, PostingsEnum postings) {
      this.what = what;
      this.postings = postings;
    }

    public Term getTerm() {
      return what;
    }

    public BytesRef getBytes() {
      return what.bytes();
    }

    @Override
    public int docID() {
      return postings.docID();
    }

    @Override
    public int nextDoc() throws IOException {
      return postings.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
      return postings.advance(target);
    }

    @Override
    public long cost() {
      return postings.cost();
    }

    @Override
    public int compareTo(@Nonnull TermHits o) {
      return Integer.compare(postings.docID(), o.postings.docID());
    }

    public boolean matches(int doc) throws IOException {
      final int here = docID();
      if(here == doc) return true;
      else if(here < doc) {
        return advance(doc) == doc;
      }
      return false;
    }
  }

  static class OneSentencePerDocCollector implements Collector {
    HashMap<Term, TermScoredDoc> scorePerPage = new HashMap<>();
    private List<Term> documentsAsTerms;
    private boolean firstMatchOnly;

    public OneSentencePerDocCollector(List<Term> documentsAsTerms, boolean firstMatchOnly) {
      this.documentsAsTerms = documentsAsTerms;
      this.firstMatchOnly = firstMatchOnly;
    }

    @Override
    public LeafCollector getLeafCollector(LeafReaderContext context) throws IOException {
      final LeafReader reader = context.reader();
      final int docBase = context.docBase;
      ArrayList<TermHits> nextImportantHit = new ArrayList<>();

      for (Term term : documentsAsTerms) {
        final PostingsEnum postings = reader.postings(term, PostingsEnum.NONE);
        if(postings == null) continue;
        final TermHits e = new TermHits(term, postings);
        nextImportantHit.add(e);
      }

      return new LeafCollector() {
        Scorer scorer;
        ArrayList<TermHits> candidates = nextImportantHit;

        @Override
        public void setScorer(Scorer scorer) throws IOException {
          this.scorer = scorer;
        }

        @Override
        public void collect(int relDoc) throws IOException {
          int doc = relDoc + docBase;

          // only consider a document if it matches one of our terms.
          TermHits match = null;
          for (TermHits candidate : candidates) {
            if(candidate.matches(relDoc)) {
              match = candidate;
            }
          }
          if(match == null) return;

          float newScore = scorer.score();
          // get or create:
          TermScoredDoc scoredSentence = scorePerPage.computeIfAbsent(match.getTerm(), TermScoredDoc::new);
          // overwrite if this is a better score:
          if(newScore > scoredSentence.score) {
            scoredSentence.doc = doc;
            scoredSentence.score = newScore;
          }

          // speed up processing by allowing the first hit for each to satisfy.
          if(firstMatchOnly) {
            candidates.remove(match);
          }
        }
      };
    }


    @Override
    public boolean needsScores() {
      return true;
    }
  }
}
