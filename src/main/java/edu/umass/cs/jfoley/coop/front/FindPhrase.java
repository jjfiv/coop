package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.postings.extents.Span;
import edu.umass.cs.jfoley.coop.PMITerm;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.front.eval.*;
import edu.umass.cs.jfoley.coop.phrases.PhraseDetector;
import edu.umass.cs.jfoley.coop.phrases.PhraseHit;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitList;
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * @author jfoley
 */
public class FindPhrase extends CoopIndexServerFn {
  private IntCoopIndex dbpedia;
  private PhraseDetector dbpediaFinder;

  protected FindPhrase(CoopIndex index) throws IOException {
    super(index);
    this.dbpedia = null;
    this.dbpediaFinder = null;
    this.dbpedia = new IntCoopIndex(new Directory("dbpedia.ints"));
    if(!indexedEntities) {
      this.dbpediaFinder = dbpedia.loadPhraseDetector(20, (IntCoopIndex) index);
    }
  }

  @Override
  public Parameters handleRequest(Parameters p) throws IOException, SQLException {
    final Parameters output = Parameters.create();
    final int count = p.get("count", 200);
    assert(count > 0);
    final String termKind = p.get("termKind", "lemmas");
    final boolean pullSlices = p.get("pullSlices", false);
    final boolean scoreTerms = p.get("scoreTerms", false);
    final int numTerms = p.get("numTerms", 30);
    final int minTermFrequency = p.get("minTermFrequency", 4);

    final boolean findEntities = p.get("findEntities", false);
    final int numEntities = p.get("numEntities", 30);
    final int minEntityFrequency = p.get("minEntityFrequency", 4);

    final String method = p.get("method", "EvaluatePhrase");
    TermPositionsIndex termIndex = index.getPositionsIndex(termKind);

    FindHitsMethod hitFinder;
    switch (method) {
      case "EvaluatePhrase":
        hitFinder = new EvaluatePhraseMethod(p, output, termIndex);
        break;
      case "LookupSingleTerm":
        hitFinder = new LookupSingleTermMethod(p, output, termIndex);
        break;
      case "LookupSinglePhrase":
        hitFinder = new LookupSinglePhraseMethod(p, output, index);
        break;
      default: throw new IllegalArgumentException("method="+method);
    }

    ArrayList<DocumentResult<Integer>> hits = hitFinder.computeTimed();
    List<DocumentResult<Integer>> topHits = ListFns.slice(hits, 0, 200);

    final TIntObjectHashMap<Parameters> hitInfos = new TIntObjectHashMap<>();
    // only return 200 results
    for (DocumentResult<Integer> hit : topHits) {
      Parameters doc = Parameters.create();
      doc.put("id", hit.document);
      doc.put("loc", hit.value);
      hitInfos.put(hit.document, doc);
    }

    for (Pair<Integer, String> kv : index.lookupNames(new IntList(hitInfos.keys()))) {
      hitInfos.get(kv.left).put("name", kv.right);
    }

    TIntIntHashMap termProxCounts = null;
    int phraseWidth = hitFinder.getPhraseWidth();
    int queryFrequency = hits.size();
    NearbyTermFinder termFinder = new NearbyTermFinder(index, p, output, phraseWidth);

    if(scoreTerms) {
      termProxCounts = termFinder.termCounts(hits);
    }

    long startScoring = System.currentTimeMillis();

    if(termProxCounts != null) {
      PMITermScorer termScorer = new PMITermScorer(termIndex, minTermFrequency, queryFrequency, index.getCollectionLength());
      List<PMITerm<Integer>> topTerms = termScorer.scoreTerms(termProxCounts, numTerms);

      IntList termIds = new IntList(numTerms);
      for (PMITerm<Integer> topTerm : topTerms) {
        termIds.add(topTerm.term);
      }
      long endScoring = System.currentTimeMillis();

      long millisForScoring = (endScoring - startScoring);
      System.out.printf("Spent %d milliseconds scoring %d terms for %d locations; %1.2f ms/hit; %d candidates.\n", millisForScoring, termProxCounts.size(), hits.size(),
          ( (double) millisForScoring / (double) hits.size() ),
          termProxCounts.size());

      TIntObjectHashMap<String> terms = new TIntObjectHashMap<>();
      for (Pair<Integer, String> kv : index.lookupTerms(termIds)) {
        terms.put(kv.getKey(), kv.getValue());
      }

      List<Parameters> termResults = new ArrayList<>();
      for (PMITerm<Integer> pmiTerm : topTerms) {
        Parameters tjson = pmiTerm.toJSON();
        tjson.put("term", terms.get(pmiTerm.term));
        //tjson.put("docs", new ArrayList<>(termInfos.get(pmiTerm.term)));
        termResults.add(tjson);
      }
      output.put("termResults", termResults);
    }

    if(findEntities) {
      TIntIntHashMap ecounts;
      long startEntites = System.currentTimeMillis();
      TIntObjectHashMap<IntList> vocab = new TIntObjectHashMap<>();
      if(indexedEntities) {
        IOMap<Integer, PhraseHitList> documentHits = index.getEntitiesIndex().getPhraseHits().getDocumentHits();
        HashMap<Integer, List<TermSlice>> slicesByDocument = termFinder.slicesByDocument(termFinder.hitsToSlices(hits));
        ecounts = new TIntIntHashMap();

        for (Pair<Integer, PhraseHitList> pair : documentHits.getInBulk(new IntList(slicesByDocument.keySet()))) {
          int doc = pair.getKey();
          PhraseHitList dochits = pair.getValue();

          List<TermSlice> localSlices = slicesByDocument.get(doc);
          for (TermSlice slice : localSlices) {
            int start = slice.start;
            int size = slice.size();
            Span query = new Span(start, start + size);
            int q_end = start + size;
            //System.err.printf("q:[%d,%d)\n", query.begin, query.end);
            System.err.println("# dochits@"+doc+"[-1]="+ListFns.getLast(dochits));
            for (PhraseHit dochit : dochits) {
              int cstart = dochit.start();
              int cend = dochit.end();

              if (query.overlaps(cstart, cend)) {
                ecounts.adjustOrPutValue(dochit.id(), 1, 1);

                for (Pair<TermSlice, IntList> pairs : this.index.pullTermSlices(Arrays.asList(new TermSlice(doc, dochit.start(), dochit.end())))) {
                  vocab.put(dochit.id(), pairs.right);
                }
              }
            }
          }
        }
      } else {
        ecounts = new TIntIntHashMap();
        for (Pair<TermSlice, IntList> pair : termFinder.pullSlicesForTermScoring(termFinder.hitsToSlices(hits))) {
          IntList data = pair.right;
          int[] doc = data.asArray();
          dbpediaFinder.match(doc, (phraseId, position, size) -> {
            vocab.putIfAbsent(phraseId, IntList.clone(doc, position, size));
            ecounts.adjustOrPutValue(phraseId, 1, 1);
          });
        }
      }

      long stopEntities = System.currentTimeMillis();
      long millisForScoring = (stopEntities - startEntites);
      System.out.printf("Spent %d milliseconds scoring %d entities for %d locations; %1.2f ms/hit; %d candidates.\n", millisForScoring, ecounts.size(), hits.size(),
          ((double) millisForScoring / (double) hits.size()),
          ecounts.size());

      long start = System.currentTimeMillis();
      TIntIntHashMap freq = index.getEntitiesIndex().getCollectionFrequencies(new IntList(ecounts.keys()));
      long end = System.currentTimeMillis();
      System.err.println("Pull efrequencies: " + (end - start) + "ms.");

      TopKHeap<PMITerm<Integer>> pmiEntities = new TopKHeap<>(numEntities);
      double collectionLength = index.getCollectionLength();
      ecounts.forEachEntry((eid, frequency) -> {
        if (frequency >= minEntityFrequency) {
          try {
            IntList eterms;
            if(vocab.size() == 0) {
              eterms = index.getEntitiesIndex().getPhraseVocab().getForward(eid);
              IntList eterms2 = vocab.get(eid);
              if(!eterms.equals(eterms2)) {
                System.err.println("# different match than defined: "+eterms+" "+eterms2);
              }
            } else {
              eterms = vocab.get(eid);
            }
            List<String> sterms = index.translateToTerms(eterms);
            System.out.println(StrUtil.join(sterms, " ") + ": freq: " + frequency);
          } catch (IOException e) {
            e.printStackTrace();
          }

          int cf = 1;
          cf = freq.get(eid);
          if(cf == freq.getNoEntryValue()) {
            cf = 1;
          }

          pmiEntities.add(new PMITerm<>(eid, cf, queryFrequency, frequency, collectionLength));
        }
        return true;
      });

      List<Parameters> entities = new ArrayList<>();
      for (PMITerm<Integer> pmiEntity : pmiEntities.getUnsortedList()) {
        int eid = pmiEntity.term;
        IntList eterms;
        if(vocab.size() == 0) {
          eterms = index.getEntitiesIndex().getPhraseVocab().getForward(eid);
        } else {
          eterms = vocab.get(eid);
        }
        Parameters ep = pmiEntity.toJSON();
        List<String> sterms = index.translateToTerms(eterms);
        ep.put("term", StrUtil.join(sterms));
        ep.put("eId", eid);
        ep.put("doc", dbpedia.getDocument(eid).toJSON());
        ep.put("terms", sterms);
        ep.put("termIds", eterms);
        entities.add(ep);
      }

      output.put("entities", entities);
    }

    // also pull terms if we want:
    if(pullSlices) {
      for (Pair<TermSlice, IntList> pair : termFinder.pullSlicesForSnippets(hits)) {
        Parameters docp = hitInfos.get(pair.left.document);
        if(docp != null) {
          docp.put("terms", index.translateToTerms(pair.right));
        }
      }
    }

    output.put("results", ListFns.slice(new ArrayList<>(hitInfos.valueCollection()), 0, 200));
    return output;
  }

  public static boolean indexedEntities = true;
}
