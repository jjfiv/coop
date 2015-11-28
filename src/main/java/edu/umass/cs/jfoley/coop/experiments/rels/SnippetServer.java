package edu.umass.cs.jfoley.coop.experiments.rels;

import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.web.WebServer;
import ciir.jfoley.chai.web.json.JSONAPI;
import ciir.jfoley.chai.web.json.JSONMethod;
import edu.washington.cs.knowitall.extractor.HtmlSentenceExtractor;
import edu.washington.cs.knowitall.extractor.ReVerbExtractor;
import edu.washington.cs.knowitall.extractor.conf.ConfidenceFunction;
import edu.washington.cs.knowitall.extractor.conf.ReVerbOpenNlpConfFunction;
import edu.washington.cs.knowitall.nlp.ChunkedSentence;
import edu.washington.cs.knowitall.nlp.OpenNlpSentenceChunker;
import edu.washington.cs.knowitall.nlp.extraction.ChunkedBinaryExtraction;
import org.lemurproject.galago.core.eval.QuerySetResults;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.utility.Parameters;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class SnippetServer {
  public static class QuerySnippet implements Comparable<QuerySnippet> {
    private final String qid;
    private final String docId;
    private final int beginToken;
    private final int endToken;
    private final String rawText;
    private final double documentScore;

    public QuerySnippet(String qid, String docId, int beginToken, int endToken, String rawText, double score) {
      this.qid = qid;
      this.docId = docId;
      this.beginToken = beginToken;
      this.endToken = endToken;
      this.rawText = rawText;
      this.documentScore = score;
    }

    public Parameters toJSON() {
      return Parameters.parseArray(
          "qid", qid,
          "docId", docId,
          "span", Arrays.asList(beginToken, endToken),
          "documentScore", documentScore,
          "text", rawText);
    }

    @Override
    public int compareTo(@Nonnull QuerySnippet o) {
      return Double.compare(this.documentScore, o.documentScore);
    }
  }
  public static void main(String[] args) throws IOException {
    String dataset = "robust";
    // load document-scores:
    QuerySetResults qresults = new QuerySetResults("/mnt/scratch3/jfoley/snippets/"+dataset+".sdm.trecrun");
    Map<String, String> queries = ScoreDocumentsForSnippets.loadQueries(dataset);

    Map<String, List<QuerySnippet>> snippetsByQuery = new HashMap<>();

    try (LinesIterable snippetLines = LinesIterable.fromFile("/mnt/scratch3/jfoley/snippets/" + dataset + ".rawsnippets.tsv.gz")) {
      for (String snippetLine : snippetLines) {
        String[] cols = snippetLine.split("\t");
        String qid = cols[0];
        String docId = cols[1];
        int beginToken = Integer.parseInt(StrUtil.takeBefore(cols[2], ","));
        int endToken = Integer.parseInt(StrUtil.takeAfter(cols[2], ","));
        String rawText = cols[4];

        MapFns.extendListInMap(snippetsByQuery, qid, new QuerySnippet(qid, docId, beginToken, endToken, rawText, qresults.get(qid).findScore(docId, Double.NaN)));
      }
    }

    // best scoring first:
    for (List<QuerySnippet> querySnippets : snippetsByQuery.values()) {
      querySnippets.sort(Comparator.reverseOrder());
    }

    HashMap<String, JSONMethod> methods = new HashMap<>();

    methods.put("/queries", (p) -> {
      List<Parameters> qp = new ArrayList<>();
      for (Map.Entry<String, String> kv : queries.entrySet()) {
        String qid = kv.getKey();
        qp.add(Parameters.parseArray(
            "qid", qid,
            "text", kv.getValue(),
            "numSnippets", snippetsByQuery.get(qid).size(),
            "numDocuments", qresults.get(qid).size()));
      }
      return Parameters.parseArray("queries", qp);
    });

    // Looks on the classpath for the default model files.
    HtmlSentenceExtractor sentenceExtractor = new HtmlSentenceExtractor();
    OpenNlpSentenceChunker chunker = new OpenNlpSentenceChunker();
    ReVerbExtractor reverb = new ReVerbExtractor();
    ConfidenceFunction confFunc = new ReVerbOpenNlpConfFunction();

    methods.put("/snippets", (p) -> {
      String qid = p.getAsString("qid");
      int offset = p.get("offset", 0);
      int pageSize = p.get("size", 30);
      List<Parameters> snippets = new ArrayList<>();

      TagTokenizer tok = new TagTokenizer();
      for (QuerySnippet snippet : ListFns.slice(snippetsByQuery.get(qid), offset, offset + pageSize)) {
        Parameters sp = snippet.toJSON();
        sp.put("terms", tok.tokenize(sp.getString("text")).terms);

        List<Parameters> extractions = new ArrayList<>();
        for (String sentence : sentenceExtractor.extract(sp.getString("text"))) {
          //System.err.println("\t"+sentence);
          ChunkedSentence sent = chunker.chunkSentence(sentence);
          for (ChunkedBinaryExtraction extr : reverb.extract(sent)) {
            Parameters extP = Parameters.create();
            extP.put("confidence", confFunc.getConf(extr));
            extP.put("subject", extr.getArgument1().getText());
            extP.put("relation", extr.getRelation().getText());
            extP.put("object", extr.getArgument2().getText());
            extractions.add(extP);
          }
        }
        sp.put("reverb_extractions", extractions);
        snippets.add(sp);
      }
      return Parameters.parseArray(
          "total", snippetsByQuery.get(qid).size(), "offset", offset, "size", pageSize,
          "snippets", snippets);
    });

    WebServer start = JSONAPI.start(1234, methods);
    start.join();
  }
}
