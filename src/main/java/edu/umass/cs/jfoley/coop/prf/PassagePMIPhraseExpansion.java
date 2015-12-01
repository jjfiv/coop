package edu.umass.cs.jfoley.coop.prf;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import edu.umass.cs.jfoley.coop.PMITerm;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.experiments.IntCorpusSDMIndex;
import edu.umass.cs.jfoley.coop.experiments.rels.ScoreDocumentsForSnippets;
import edu.umass.cs.jfoley.coop.front.TermPositionsIndex;
import edu.umass.cs.jfoley.coop.front.eval.EvaluateBagOfWordsMethod;
import edu.umass.cs.jfoley.coop.front.eval.NearbyTermFinder;
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import gnu.trove.map.hash.TLongIntHashMap;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.core.retrieval.Results;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author jfoley
 */
public class PassagePMIPhraseExpansion {
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);
    //String dataset = "clue12a.sdm";
    String dataset = argp.get("dataset", "robust");
    //String dataset = "clue12a.sdm";
    LocalRetrieval ret = new LocalRetrieval("/mnt/scratch3/jfoley/"+dataset+".galago");
    IntCoopIndex target = new IntCoopIndex(Directory.Read("/mnt/scratch3/jfoley/"+dataset+".ints"));
    TermPositionsIndex index = target.getPositionsIndex();
    Map<String, String> queries = new TreeMap<>(ScoreDocumentsForSnippets.loadQueries(dataset));

    String baselineModel = argp.get("baseline", "sdm");
    if(baselineModel.equals("ql")) {
      baselineModel = "combine";
    }
    double expansionWeight = argp.get("expansionWeight", 0.8);
    int n = 2; // up to bigrams
    int K = 100;
    int minTermFrequency = argp.get("minTermFrequency", 2);
    int numTerms = argp.get("numTerms", 50);
    boolean approx = argp.get("approxPhraseStats", true);

    TagTokenizer tok = new TagTokenizer();

    try (PrintWriter trecrun = IO.openPrintWriter(dataset+"."+baselineModel+"-x.pmi.bi.mtf"+minTermFrequency+".numTerms"+numTerms+".w"+Double.toString(expansionWeight).replace('.', '_')+".trecrun")) {
      for (Map.Entry<String, String> kv : queries.entrySet()) {
        String qid = kv.getKey();
        List<String> qterms = tok.tokenize(kv.getValue()).terms;
        System.err.println("# "+qid+" "+qterms);

        Node sdm = new Node(baselineModel);
        sdm.addTerms(qterms);

        Results requested = ret.transformAndExecuteQuery(sdm, Parameters.parseArray("requested", 3000));
        List<String> workingSet = new ArrayList<>(requested.resultSet());
        //IntList docFilter = target.getNames().translateReverse(workingSet, -1);

        Parameters bom = Parameters.create();
        bom.put("query", kv.getValue());
        bom.put("passageSize", 150);
        Parameters info = Parameters.create();
        EvaluateBagOfWordsMethod method = new EvaluateBagOfWordsMethod(bom, info, index);
        List<DocumentResult<Integer>> hits = method.compute();

        int phraseWidth = method.getPhraseWidth();
        int queryFrequency = hits.size();
        NearbyTermFinder termFinder = new NearbyTermFinder(target, argp, info, phraseWidth);

        //TIntIntHashMap termProxCounts = termFinder.termCounts(hits);
        //PMITermScorer termScorer = new PMITermScorer(index, minTermFrequency, queryFrequency, index.getCollectionLength());

        //IntList interestingUnigrams = new IntList(termScorer.scoreTerms(termProxCounts, 50).stream().mapToInt(x -> x.term).toArray());

        // collect bigrams:
        TLongIntHashMap bigramPhraseCounts = new TLongIntHashMap();
        for (Pair<TermSlice, IntList> regions : termFinder.pullSlicesForTermScoring(termFinder.hitsToSlices(hits))) {
          IntList termV = regions.right;
          for (int i = 0; i < termV.size()-1; i++) {
            int lhs = termV.getQuick(i);
            int rhs = termV.getQuick(i+1);
            if(lhs == rhs) continue;
            long key = IntCorpusSDMIndex.Bigram.toLong(lhs, rhs);
            bigramPhraseCounts.adjustOrPutValue(key, 1, 1);
          }
        }

        double collectionLength = index.getCollectionLength();
        List<PMITerm<IntList>> pmiTerms = new TopKHeap<>(numTerms);

        bigramPhraseCounts.forEachEntry((bigramLong, frequency) -> {
          if (frequency <= minTermFrequency) return true;

          IntCorpusSDMIndex.Bigram foo = IntCorpusSDMIndex.Bigram.fromLong(bigramLong);
          IntList pieces = new IntList();
          pieces.push(foo.first);
          pieces.push(foo.second);

          try {
            int freq = 0;
            if(approx) {
              // an upper-bound on the actual value.
              freq = Math.min(index.collectionFrequency(foo.first), index.collectionFrequency(foo.second));
            }  else {
              // slow as all hell
              freq = index.countPhrase(pieces);
            }
            pmiTerms.add(new PMITerm<>(pieces, freq, queryFrequency, frequency, collectionLength));
          } catch (IOException e) {
            e.printStackTrace();
          }
          return true;
        });


        IntList termIds = new IntList();
        for (PMITerm<IntList> topTerm : pmiTerms) {
          termIds.addAll(topTerm.term);
        }
        Map<Integer, String> forwardMap = target.getTermVocabulary().getForwardMap(termIds);

        Node expansion = new Node("combine");
        int expansionTermIndex = 0;
        for (PMITerm<IntList> pmiTerm : pmiTerms) {
          double weight = pmiTerm.pmi();
          String lterm = forwardMap.get(pmiTerm.term.get(0));
          if (lterm == null) continue;
          String rterm = forwardMap.get(pmiTerm.term.get(1));
          if (rterm == null) continue;

          Node od1 = new Node("od");
          od1.getNodeParameters().set("default", 1);
          od1.addTerms(Arrays.asList(lterm, rterm));
          expansion.addChild(od1);
          expansion.getNodeParameters().set(Integer.toString(expansionTermIndex++), weight);
        }

        Node total = new Node("combine");
        total.add(sdm);
        total.add(expansion);
        total.getNodeParameters().set("0", expansionWeight);
        total.getNodeParameters().set("1", (1.0 - expansionWeight));

        // don't expand if nothing found:
        if(expansion.isEmpty()) {
          total = sdm;
        }

        Parameters qp = Parameters.create();
        qp.put("workingSet", workingSet);
        Results reranked = ret.transformAndExecuteQuery(total, qp);

        for (ScoredDocument scoredDocument : reranked.scoredDocuments) {
          trecrun.println(scoredDocument.toTRECformat(qid, "sdm-exppmi"));
        }
      }
    }
  }
}
