package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.web.WebServer;
import ciir.jfoley.chai.web.json.JSONAPI;
import ciir.jfoley.chai.web.json.JSONMethod;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.lemurproject.galago.utility.Parameters;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class CoopServer {
  private final CoopIndex docs;
  private final CoopIndex sentences;
  StandardQueryParser qp = new StandardQueryParser();
  Map<String, JSONMethod> methods = new HashMap<>();

  public CoopServer(Directory dir, String name) throws IOException {
    this.docs = new CoopIndex(dir.childPath(name+".index"));
    this.sentences = new CoopIndex(dir.childPath(name+".sentences.index"));
    this.methods.put("/search", this::search);
  }

  public void run() {
    WebServer api = JSONAPI.start(1234, methods);
    api.join();
  }

  public static void main(String[] args) throws IOException {
    CoopServer server = new CoopServer(Directory.Read("."), "test_anno");
    server.run();
  }

  public Parameters search(Parameters p) throws QueryNodeException, IOException {
    Parameters output = Parameters.create();

    // pull out data we need:
    boolean perDocAllPhrases = p.get("resultPhrases", false);
    Query lq = qp.parse(p.getString("q"), p.get("field", "body"));
    int limit = p.get("limit", 200);
    int n = p.get("n", 20);
    int minTF = p.get("min", 2);
    int minDF = p.get("minDF", 5);
    boolean documents = !p.get("sentences", false);
    boolean json = !documents && p.get("json", false);

    CoopIndex target = documents ? docs : sentences;
    Set<String> fields = new HashSet<>(Arrays.asList("id", "facets", "time"));
    if(!documents) {
      fields.add("index");
    }
    if(json) {
      fields.add("json");
    }

    // structures to fill:
    TObjectIntHashMap<String> overall = new TObjectIntHashMap<>();
    List<Parameters> results = new ArrayList<>();

    long startSearch = System.currentTimeMillis();
    TopDocs docResults = target.searcher.search(lq, limit);
    long endSearch = System.currentTimeMillis();
    output.put("searchTime", (endSearch - startSearch));

    long startIOSum = System.currentTimeMillis();
    for (ScoreDoc scoreDoc : docResults.scoreDocs) {
      Parameters out = Parameters.create();
      out.put("score", scoreDoc.score);

      Document document = target.reader.document(scoreDoc.doc, fields);

      out.put("id", document.get("id"));
      out.put("time", document.getField("time").numericValue());
      if(!documents) {
        out.put("index", document.getField("index").numericValue());
      }

      Parameters forJSON = Parameters.create();

      TObjectIntHashMap<String> facets = TroveFrequencyCoder.read(document.getBinaryValue("facets"));
      facets.forEachEntry((phrase, count) -> {
        if(perDocAllPhrases && count > minTF) {
          forJSON.put(phrase, count);
        }
        overall.adjustOrPutValue(phrase, count, count);
        return true;
      });
      if(json) {
        out.put("json", Parameters.parseString(document.get("json")));
      }
      if(perDocAllPhrases) {
        out.put("phrases", forJSON);
      }
      results.add(out);
    }
    long endIOSum = System.currentTimeMillis();
    output.put("ioTime", (endIOSum - startIOSum));

    long rankTermsStart = System.currentTimeMillis();
    TopKHeap<PhraseFacet> topTerms = new TopKHeap<>(n);
    overall.forEachEntry((phrase, count) -> {
      if(count >= minTF) {
        CountStats fstats = target.getTermStatistics("facet-phrase", phrase.replace(' ', '_'));
        if(fstats.documentFrequency >= minDF) {
          topTerms.offer(new PhraseFacet(phrase, count, fstats));
        }
      }
      return true;
    });
    long rankTermsEnd = System.currentTimeMillis();
    output.put("rankTermsTime", (rankTermsEnd - rankTermsStart));

    output.put("terms", ListFns.map(topTerms.getSorted(), PhraseFacet::toJSON));
    output.put(documents ? "docs" : "sentences", results);
    return output;
  }

  public static class PhraseFacet implements Comparable<PhraseFacet> {
    public final String surface;
    public final double count;
    public final CountStats stats;
    private final double score;

    public PhraseFacet(String surface, double count, CountStats stats) {
      this.surface = surface;
      this.count = count;
      this.stats = stats;
      this.score = count / (double) stats.collectionFrequency;
    }


    @Override
    public int compareTo(@Nonnull PhraseFacet rhs) {
      return Double.compare(this.score, rhs.score);
    }

    public Parameters toJSON() {
      return Parameters.parseArray(
          "text", surface,
          "count", count,
          "cf", stats.collectionFrequency,
          "df", stats.documentFrequency,
          "score", score
      );
    }
  }
}
