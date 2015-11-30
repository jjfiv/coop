package edu.umass.cs.jfoley.coop.prf;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import edu.umass.cs.jfoley.coop.PMITerm;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.experiments.rels.ScoreDocumentsForSnippets;
import edu.umass.cs.jfoley.coop.front.TermPositionsIndex;
import edu.umass.cs.jfoley.coop.front.eval.EvaluateBagOfWordsMethod;
import edu.umass.cs.jfoley.coop.front.eval.NearbyTermFinder;
import edu.umass.cs.jfoley.coop.front.eval.PMITermScorer;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import gnu.trove.map.hash.TIntIntHashMap;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.core.retrieval.Results;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author jfoley
 */
public class PassagePMITermExpansion {
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);
    //String dataset = "clue12a.sdm";
    String dataset = argp.get("dataset", "robust");
    //String dataset = "clue12a.sdm";
    LocalRetrieval ret = new LocalRetrieval("/mnt/scratch3/jfoley/"+dataset+".galago");
    IntCoopIndex target = new IntCoopIndex(Directory.Read("/mnt/scratch3/jfoley/"+dataset+".ints"));
    TermPositionsIndex index = target.getPositionsIndex();
    Map<String, String> queries = new TreeMap<>(ScoreDocumentsForSnippets.loadQueries(dataset));

    String baselineModel = argp.get("baseline", "ql");
    if(baselineModel.equals("ql")) {
      baselineModel = "combine";
    }
    double expansionWeight = argp.get("expansionWeight", 0.8);
    int K = 100;
    int minTermFrequency = argp.get("minTermFrequency", 2);
    int numTerms = argp.get("numTerms", 50);

    TagTokenizer tok = new TagTokenizer();

    try (PrintWriter trecrun = IO.openPrintWriter(dataset+"."+baselineModel+"-exppmi.mtf"+minTermFrequency+".numTerms"+numTerms+".w"+Double.toString(expansionWeight).replace('.', '_')+".trecrun")) {
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
        bom.put("passageSize", 30);
        Parameters info = Parameters.create();
        EvaluateBagOfWordsMethod method = new EvaluateBagOfWordsMethod(bom, info, index);
        List<DocumentResult<Integer>> hits = method.computeTimed();

        int phraseWidth = method.getPhraseWidth();
        int queryFrequency = hits.size();
        NearbyTermFinder termFinder = new NearbyTermFinder(target, argp, info, phraseWidth);
        TIntIntHashMap termProxCounts = termFinder.termCounts(hits);
        PMITermScorer termScorer = new PMITermScorer(index, minTermFrequency, queryFrequency, index.getCollectionLength());
        List<PMITerm<Integer>> pmiTerms = termScorer.scoreTerms(termProxCounts, numTerms);

        IntList termIds = new IntList();
        for (PMITerm<Integer> topTerm : pmiTerms) {
          termIds.add(topTerm.term);
        }
        Map<Integer, String> forwardMap = target.getTermVocabulary().getForwardMap(termIds);

        Node expansion = new Node("combine");
        int expansionTermIndex = 0;
        for (PMITerm<Integer> pmiTerm : pmiTerms) {
          double weight = pmiTerm.pmi();
          String termAsStr = forwardMap.get(pmiTerm.term);
          if (termAsStr == null) continue;
          expansion.addChild(Node.Text(termAsStr));
          expansion.getNodeParameters().set(Integer.toString(expansionTermIndex++), weight);
        }

        Node total = new Node("combine");
        total.add(sdm);
        total.add(expansion);
        total.getNodeParameters().set("0", expansionWeight);
        total.getNodeParameters().set("1", (1.0 - expansionWeight));

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
