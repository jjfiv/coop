package edu.umass.cs.jfoley.coop.experiments.deepscore;

import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.core.retrieval.Results;
import org.lemurproject.galago.core.retrieval.Retrieval;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.core.retrieval.prf.WeightedTerm;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.core.util.WordLists;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StringPooler;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author jfoley
 */
public class RunRelevanceModel {
  public static class RelevanceModel {

    Retrieval m_retrieval;

    private HashMap<String, Integer> lengths;

    public RelevanceModel(Retrieval r) {
      m_retrieval = r;
    }

    public static class Gram extends WeightedTerm {

      public String term;

      public Gram(String t) {
        this(t, 0.0);
      }
      public Gram(String t, double score) {
        super(score);
        term = t;
      }

      public String getTerm() {
        return term;
      }

      // The secondary sort is to have defined behavior for statistically tied samples.
      public int compareTo(@Nonnull WeightedTerm other) {
        Gram that = (Gram) other;
        int result = this.score > that.score ? -1 : (this.score < that.score ? 1 : 0);
        if (result != 0) {
          return result;
        }
        result = (this.term.compareTo(that.term));
        return result;
      }

      public String toString() {
        return "<" + term + "," + score + ">";
      }

      @Override
      public Gram clone(double score) {
        return new Gram(term, score);
      }
    }

    public ArrayList<WeightedTerm> generateGrams(List<ScoredDocument> initialResults) throws IOException {
      HashMap<String, Double> scores = logsToPosteriors2(initialResults);
      HashMap<String, HashMap<String, Integer>> counts = countGrams(initialResults);
      ArrayList<WeightedTerm> scored = scoreGrams(counts, scores);
      Collections.sort(scored);
      return scored;
    }


    // Implementation here is identical to the Relevance Model unigram normaliztion in Indri.
    // See RelevanceModel.cpp for details
    public static final HashMap<String, Double> logsToPosteriors(List<ScoredDocument> results) {
      HashMap<String, Double> scores = new HashMap<>();
      if (results.size() == 0) {
        return scores;
      }

      // For normalization
      double K = results.get(0).score;

      // First pass to get the sum
      double sum = 0;
      for (ScoredDocument sd : results) {
        double recovered = Math.exp(K + sd.score);
        scores.put(sd.documentName, recovered);
        sum += recovered;
      }

      // Normalize
      for (Map.Entry<String, Double> entry : scores.entrySet()) {
        entry.setValue(entry.getValue() / sum);
      }
      return scores;
    }


    /**
     * This is a "fixed" version that computes normalized log posteriors.  It uses the
     * log-sum-exp trick to avoid underflow.
     *
     * @param results
     * @return
     */
    public static final HashMap<String, Double> logsToPosteriors2(List<ScoredDocument> results) {
      HashMap<String, Double> scores = new HashMap<>();
      if (results.size() == 0) {
        return scores;
      }

      // For normalization
      double K = results.get(0).score;

      // First pass to get the sum
      double sum = 0;
      for (ScoredDocument sd : results) {
        double recovered = Math.exp(sd.score - K);
        scores.put(sd.documentName, sd.score);
        sum += recovered;
      }
      double logNorm = K + Math.log(sum);

      // Normalize
      for (Map.Entry<String, Double> entry : scores.entrySet()) {
        entry.setValue(Math.exp(entry.getValue() - logNorm));
      }
      return scores;
    }

    protected HashMap<String, HashMap<String, Integer>> countGrams(List<ScoredDocument> results) throws IOException {
      lengths = new HashMap<>();
      HashMap<String, HashMap<String, Integer>> counts = new HashMap<>();
      HashMap<String, Integer> termCounts;
      Document doc;
      String term;
      for (ScoredDocument sd : results) {
        doc = m_retrieval.getDocument(sd.documentName, Document.DocumentComponents.JustTerms);
        if (doc != null) {
          for (String s : doc.terms) {
            term = s;
            if (!counts.containsKey(term)) {
              counts.put(term, new HashMap<>());
            }
            termCounts = counts.get(term);
            if (termCounts.containsKey(sd.documentName)) {
              termCounts.put(sd.documentName, termCounts.get(sd.documentName) + 1);
            } else {
              termCounts.put(sd.documentName, 1);
            }
            lengths.put(sd.documentName, doc.terms.size());
          }
        }
      }
      return counts;
    }

    protected ArrayList<WeightedTerm> scoreGrams(HashMap<String, HashMap<String, Integer>> counts,
                                                 HashMap<String, Double> scores) throws IOException {
      ArrayList<WeightedTerm> grams = new ArrayList<>();
      HashMap<String, Integer> termCounts;

      for (String term : counts.keySet()) {
        Gram g = new Gram(term);
        termCounts = counts.get(term);
        for (String docID : termCounts.keySet()) {
          int length = lengths.get(docID);
          int count = termCounts.get(docID);
          Double score = scores.get(docID);
          if (score == null) {
            System.out.println("WTF!");
          }
          // this performs the probability computation in real space... possibly leading
          // to underflow for small scores.
          // consider doing this in log space.
          g.score +=  score * (count / (double) length);
        }
        // 1 / fbDocs from the RelevanceModel source code
        // WHY?!?  we rescale the values anyway
        g.score *= (1.0 / scores.size());
        grams.add(g);
      }

      return grams;
    }
  }

  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);
    LocalRetrieval ret = new LocalRetrieval("/mnt/scratch3/jfoley/robust.galago");

    Map<String, String> qidToDesc = new TreeMap<>();
    for (String line : LinesIterable.fromFile("/home/jfoley/code/queries/robust04/rob04.titles.tsv").slurp()) {
      String[] data = line.split("\t");
      qidToDesc.put(data[0].trim(), data[1].trim());
    }

    TagTokenizer tok = new TagTokenizer();
    StringPooler.disable();

    double origWeight = argp.get("origWeight", 0.8);
    int feedbackDocs = argp.get("fbDocs", 10);
    int feedbackTerms = argp.get("fbTerms", 30);
    double mu = argp.get("mu", 1500);
    double uniw = argp.get("uniw", 0.8);
    double odw = argp.get("uniw", 0.15);
    double uww = argp.get("uniw", 0.05);

    RelevanceModel rm = new RelevanceModel(ret);
    Set<String> rmstop = WordLists.getWordListOrDie("inquery");

    try (PrintWriter scores = IO.openPrintWriter("robust.sdm-rm3.trecrun")) {
      for (Map.Entry<String, String> kv : qidToDesc.entrySet()) {
        String qid = kv.getKey();
        String query = kv.getValue();
        List<String> tokens = tok.tokenize(query).terms;

        Node sdm = new Node("sdm");
        sdm.addTerms(tokens);

        Parameters qp = Parameters.create();
        // general mu param
        qp.put("mu", mu);
        // sdm params
        qp.put("uniw", uniw);
        qp.put("odw", odw);
        qp.put("uww", uww);

        Results res = ret.transformAndExecuteQuery(sdm, qp);
        ArrayList<WeightedTerm> weightedTerms = rm.generateGrams(ListFns.take(res.scoredDocuments, feedbackDocs));

        List<WeightedTerm> valuableTerms = new ArrayList<>();
        for (WeightedTerm weightedTerm : weightedTerms) {
          if(valuableTerms.size() >= feedbackTerms) break;
          //skip stopwords
          if(rmstop.contains(weightedTerm.getTerm())) continue;
          // skip original query
          if(tokens.contains(weightedTerm.getTerm())) continue;

          valuableTerms.add(weightedTerm);
        }

        // build expansion galago query:
        Node xp = new Node("combine");
        for (int i = 0; i < valuableTerms.size(); i++) {
          WeightedTerm weightedTerm = valuableTerms.get(i);
          xp.add(Node.Text(weightedTerm.getTerm()));
          xp.getNodeParameters().set(Integer.toString(i), weightedTerm.score);
        }

        // build final query:
        Node fullQ = new Node("combine");
        fullQ.getNodeParameters().set("0", origWeight);
        fullQ.getNodeParameters().set("1", 1.0 - origWeight);
        fullQ.add(sdm.clone());
        fullQ.add(xp);

        // run query, write to trecrun:
        Parameters fqp = Parameters.create();
        // general mu param
        fqp.put("mu", mu);
        // sdm params
        fqp.put("uniw", uniw);
        fqp.put("odw", odw);
        fqp.put("uww", uww);
        ret.transformAndExecuteQuery(fullQ, fqp)
            .printToTrecrun(scores, qid, "sdm-rm3-galago");
      }
    }
  }
}
