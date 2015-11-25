package edu.umass.cs.jfoley.coop.experiments.rels;

import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.time.Debouncer;
import edu.washington.cs.knowitall.extractor.HtmlSentenceExtractor;
import edu.washington.cs.knowitall.extractor.ReVerbExtractor;
import edu.washington.cs.knowitall.extractor.conf.ConfidenceFunction;
import edu.washington.cs.knowitall.extractor.conf.ReVerbOpenNlpConfFunction;
import edu.washington.cs.knowitall.nlp.ChunkedSentence;
import edu.washington.cs.knowitall.nlp.OpenNlpSentenceChunker;
import edu.washington.cs.knowitall.nlp.extraction.ChunkedBinaryExtraction;
import org.lemurproject.galago.core.eval.EvalDoc;
import org.lemurproject.galago.core.eval.QueryResults;
import org.lemurproject.galago.core.eval.QuerySetResults;
import org.lemurproject.galago.utility.Parameters;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author jfoley
 */
public class ReVerbSnippets {
  public static class ReVerbExtraction implements Comparable<ReVerbExtraction> {
    final double confidence;
    final ChunkedBinaryExtraction extraction;
    final double docScore;
    private final double score;

    public ReVerbExtraction(double docScore, double confidence, ChunkedBinaryExtraction extraction) {
      this.docScore = docScore;
      this.confidence = confidence;
      this.extraction = extraction;

      this.score = docScore + Math.log(confidence);
    }

    public String getSubject() {
      return extraction.getArgument1().getText();
    }

    public double getSubjectConfidence() {
      return extraction.getArgument1().getConfidence();
    }

    public String getObject() {
      return extraction.getArgument2().getText();
    }

    public double getObjectConfidence() {
      return extraction.getArgument2().getConfidence();
    }

    public String getRelation() {
      return extraction.getRelation().getText();
    }

    public Parameters toJSON() {
      return Parameters.parseArray(
          "confidence", confidence,
          "subj", getSubject(),
          "obj", getObject(),
          "rel", getRelation());
    }

    @Override
    public String toString() {
      return String.format("%1.3f:%1.3f ``%s`` ``%s`` ``%s``", confidence,docScore, getSubject(), getRelation(), getObject());
    }

    @Override
    public int compareTo(@Nonnull ReVerbExtraction o) {
      return Double.compare(score, o.score);
    }
  }

  public static void main(String[] args) throws IOException {
    //String dataset = "clue12a.sdm";
    String dataset = "robust";

    // Looks on the classpath for the default model files.
    HtmlSentenceExtractor sentenceExtractor = new HtmlSentenceExtractor();
    OpenNlpSentenceChunker chunker = new OpenNlpSentenceChunker();

    ReVerbExtractor reverb = new ReVerbExtractor();

    QuerySetResults qresults = new QuerySetResults("/mnt/scratch3/jfoley/snippets/"+dataset+".sdm.trecrun");

    ConfidenceFunction confFunc = //(ignored) -> 1.0;
        new ReVerbOpenNlpConfFunction();

    int K = 50;
    Debouncer msg = new Debouncer();
    Map<String, TopKHeap<ReVerbExtraction>> topExtr = new TreeMap<>();
    Map<String, StreamingStats> scoreStatsByQuery = new HashMap<>();

    try (LinesIterable snippetLines = LinesIterable.fromFile("/mnt/scratch3/jfoley/snippets/" + dataset + ".rawsnippets.tsv.gz")) {
      for (String snippetLine : snippetLines) {
        String[] cols = snippetLine.split("\t");
        String qid = cols[0];
        String docId = cols[1];
        String rawText = cols[4];
        if(msg.ready()) {
          System.err.println(qid+" "+docId);
        }

        QueryResults data = qresults.get(qid);

        // compute stats for this query if not done so already:
        StreamingStats stats = scoreStatsByQuery.computeIfAbsent(qid, (ignored) -> {
          StreamingStats statsBuilder = new StreamingStats();
          for (EvalDoc evalDoc : data) {
            statsBuilder.push(evalDoc.getScore());
          }
          return statsBuilder;
        });

        // collect the document score for passages in this document:
        double documentScore = data.stream().filter((x) -> x.getName().equals(docId)).findAny().map(EvalDoc::getScore).orElse(stats.getMin());

        TopKHeap<ReVerbExtraction> queryExtractions = topExtr.computeIfAbsent(qid, ignored -> new TopKHeap<>(K));

        for (String sentence : sentenceExtractor.extract(rawText)) {
          //System.err.println("\t"+sentence);
          ChunkedSentence sent = chunker.chunkSentence(sentence);
          for (ChunkedBinaryExtraction extr : reverb.extract(sent)) {
            double conf = confFunc.getConf(extr);
            queryExtractions.add(new ReVerbExtraction(documentScore, conf, extr));
          }
        }
        //if(snippetLines.getLineNumber()>10) break;
      }

    }

    for (Map.Entry<String, TopKHeap<ReVerbExtraction>> kv : topExtr.entrySet()) {
      String qid = kv.getKey();
      TopKHeap<ReVerbExtraction> topExtractions = kv.getValue();
      System.err.println(qid+": Showing the "+K+" best extractions out of "+topExtractions.getTotalSeen()+" total.");
      for (ReVerbExtraction topExtraction : topExtractions.getSorted()) {
        System.err.println("\t"+topExtraction.toString());
      }
    }
  }
}
