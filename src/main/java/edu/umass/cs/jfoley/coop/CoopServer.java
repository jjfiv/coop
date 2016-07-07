package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.web.WebServer;
import ciir.jfoley.chai.web.json.JSONAPI;
import ciir.jfoley.chai.web.json.JSONMethod;
import edu.umass.cs.jfoley.coop.lucene.AllMatchesCollectorManager;
import edu.umass.cs.jfoley.coop.lucene.OneDocPerTerm;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.*;
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
    this.methods.put("/rookie", this::rookie);
  }

  public void run() {
    WebServer api = JSONAPI.start(1235, methods);
    api.join();
  }

  public static void main(String[] args) throws IOException {
    CoopServer server = new CoopServer(Directory.Read("."), "test_anno");
    server.run();
  }

  /**
   * Document Search, Facet Ranking, and Top Sentences for each Document Match.
   * @param p get/post parameters
   * @return JSON output
   */
  public Parameters rookie(Parameters p) throws IOException {
    Parameters output = Parameters.create();
    int minTF = p.get("min", 2);
    int minDF = p.get("minDF", 5);
    int numFacets = p.get("numFacets", 20);
    boolean firstSentenceMatchOnly = p.get("firstSentenceMatchOnly", false);

    // Do document search:
    Query lq;
    try {
      lq = qp.parse(p.getString("q"), p.get("field", "body"));
    } catch (QueryNodeException e) {
      throw new IllegalArgumentException("Error parsing input query: ", e);
    }

    long startSearch = System.currentTimeMillis();
    IntList docResults = docs.searcher.search(lq, new AllMatchesCollectorManager());
    long endSearch = System.currentTimeMillis();
    output.put("totalHits", docResults.size());
    output.put("searchTime", (endSearch - startSearch));

    Set<String> fields = new HashSet<>(Arrays.asList("id", "facets", "time"));

    List<Term> documentsAsTerms = new ArrayList<>();
    List<OneDocPerTerm.TermScoredDoc> sentencesPerDoc = new ArrayList<>();
    TObjectIntHashMap<Term> docTermToIndex = new TObjectIntHashMap<>();
    TObjectIntHashMap<String> overall = new TObjectIntHashMap<>();

    List<String> results = new ArrayList<>();
    long startDocumentDecorationTime = System.currentTimeMillis();
    for (int docId : docResults) {
      Document document = docs.reader.document(docId, fields);

      final String name = document.get("id");
      long time = (long) document.getField("time").numericValue();
      final String docData = name + "\t" + time;
      results.add(docData);

      // add this to our sentence-search query:
      final Term docAsTerm = new Term("id", name);
      docTermToIndex.put(docAsTerm, documentsAsTerms.size());
      documentsAsTerms.add(docAsTerm);
      sentencesPerDoc.add(null); // put a null here for now.

      TObjectIntHashMap<String> facets = TroveFrequencyCoder.read(document.getBinaryValue("facets"));
      facets.forEachEntry((phrase, count) -> {
        overall.adjustOrPutValue(phrase, count, count);
        return true;
      });
    }
    output.put("documentDecorationTime", (System.currentTimeMillis() - startDocumentDecorationTime));
    output.put("docs", results);

    long rankSentenceTime = System.currentTimeMillis();
    final List<OneDocPerTerm.TermScoredDoc> oneSentencePerDoc = sentences.searcher.search(lq, new OneDocPerTerm(documentsAsTerms, firstSentenceMatchOnly));
    for (OneDocPerTerm.TermScoredDoc termScoredDoc : oneSentencePerDoc) {
      int idx = docTermToIndex.get(termScoredDoc.term);
      sentencesPerDoc.set(idx, termScoredDoc);
    }
    output.put("sentenceRankingTime", (System.currentTimeMillis() - rankSentenceTime));

    long jsonSentenceTime = System.currentTimeMillis();
    List<Parameters> sjson = new ArrayList<>();
    for (int i = 0; i < sentencesPerDoc.size(); i++) {
      OneDocPerTerm.TermScoredDoc termScoredDoc = sentencesPerDoc.get(i);
      if(termScoredDoc == null) {
        // pick first sentence if no match:
        // TODO, do this in OneDocPerTerm instead?
        termScoredDoc = new OneDocPerTerm.TermScoredDoc(documentsAsTerms.get(i));
        BooleanQuery.Builder firstSentence = new BooleanQuery.Builder();
        firstSentence.add(new TermQuery(termScoredDoc.term), BooleanClause.Occur.MUST);
        firstSentence.add(IntPoint.newExactQuery("index", 0), BooleanClause.Occur.MUST);
        final IntList found = sentences.searcher.search(firstSentence.build(), new AllMatchesCollectorManager());
        assert(!found.isEmpty()) : "Unexpected empty document: "+termScoredDoc.term;
        assert(found.size() == 1) : "Unexpected many first sentences in document: "+termScoredDoc.term;
        termScoredDoc.doc = found.get(0);
      }

      sjson.add(Parameters.parseString(sentences.getField(termScoredDoc.doc, "json")));
    }
    output.put("sentences", sjson);
    output.put("sentenceJSONTime", (System.currentTimeMillis() - jsonSentenceTime));

    // rank terms:
    long rankTermsStart = System.currentTimeMillis();
    TopKHeap<PhraseFacet> topTerms = new TopKHeap<>(numFacets);
    overall.forEachEntry((phrase, count) -> {
      if(count >= minTF) {
        CountStats fstats = docs.getTermStatistics("facet-phrase", phrase.replace(' ', '_'));
        if(fstats.documentFrequency >= minDF) {
          topTerms.offer(new PhraseFacet(phrase, count, fstats));
        }
      }
      return true;
    });
    output.put("terms", ListFns.map(topTerms.getSorted(), PhraseFacet::toTSV));

    long rankTermsEnd = System.currentTimeMillis();
    output.put("rankTermsTime", (rankTermsEnd - rankTermsStart));
    return output;
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

    public String toTSV() {
      return surface+"\t"+score;
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
