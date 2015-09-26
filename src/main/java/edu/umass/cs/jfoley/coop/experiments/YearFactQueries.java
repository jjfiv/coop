package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.random.ReservoirSampler;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.front.QueryEngine;
import edu.umass.cs.jfoley.coop.front.TermPositionsIndex;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.Tag;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.core.util.WordLists;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StringPooler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * @author jfoley
 */
public class YearFactQueries {

  public static class EntityMention {
    final String entityName;
    final String mentionText;
    final int begin;
    final int end;

    public EntityMention(String entityName, String mentionText, int begin, int end) {
      this.entityName = entityName;
      this.mentionText = mentionText;
      this.begin = begin;
      this.end = end;
    }

    int size() {
      return end - begin;
    }

    /**
     * @return true if the mention and the entity name are enough different to care.
     */
    public boolean interestingMention() {
      return !mentionText.replace(' ', '_').equalsIgnoreCase(entityName);
    }

    @Override
    public String toString() {
      if(interestingMention()) {
        return mentionText + ":" + entityName + ":" + begin + ":" + size();
      }
      return entityName + ":" + begin + ":" + size();
    }
  }
  public static class YearFactQuery {
    public final int year;
    public final String html;
    private ArrayList<EntityMention> mentions;
    private List<String> terms;

    public YearFactQuery(int year, String html) {
      this.year = year;
      this.html = html;
    }

    public void process() {
      if(this.mentions != null) return;
      StringPooler.disable();
      TagTokenizer tok = new TagTokenizer();
      tok.addField("a");
      Document doc = tok.tokenize(html);

      this.mentions = new ArrayList<>();
      for (Tag link : doc.tags) {
        String url = link.attributes.get("href");
        int begin = link.begin;
        int end = link.end;
        String mentionText = html.substring(link.charBegin, link.charEnd);
        String ent = StrUtil.takeAfter(url, "https://en.wikipedia.org/wiki/");
        mentions.add(new EntityMention(ent, mentionText, begin, end));
      }
      this.terms = doc.terms;
    }


    @Override
    public String toString() {
      process();
      StringBuilder sb = new StringBuilder();
      sb.append(year).append('\t');
      for (String term : terms) {
        sb.append(' ').append(term);
      }
      sb.append('\t');
      sb.append(mentions);
      return sb.toString();
    }

    public List<String> getTerms() {
      process(); // calc terms if needed
      return terms;
    }
  }

  public static void main(String[] args) throws IOException {
    IntCoopIndex index = new IntCoopIndex(Directory.Read("dbpedia.ints"));
    HashSet<String> stopwords = new HashSet<>(Objects.requireNonNull(WordLists.getWordList("inquery")));
    IntList stopInts = new IntList(index.translateFromTerms(new ArrayList<>(stopwords)));

    Parameters json = Parameters.parseStream(IO.openInputStream("coop/data/ecir15.wiki-year-facts.json.gz"));
    List<Parameters> facts = json.getAsList("data", Parameters.class);
    System.out.println(facts.size());
    System.out.println(facts.get(0).toPrettyString());

    List<YearFactQuery> keep = new ArrayList<>();
    ReservoirSampler<YearFactQuery> random = new ReservoirSampler<>(50);
    for (Parameters fact : facts) {
      String yearStr = fact.getString("year");
      if (yearStr.endsWith("BC")) continue;
      int year = Integer.parseInt(yearStr);
      if (year >= 1000 && year <= 1925) {
        YearFactQuery yfq = new YearFactQuery(year, fact.getString("fact"));
        keep.add(yfq);
        random.add(yfq);
      }
    }

    StreamingStats queryTimeStats = new StreamingStats();
    StreamingStats querySizeStats = new StreamingStats();
    System.err.println("Found " + keep.size() + " queries from ECIR15");
    for (YearFactQuery yfq : random) {
      IntList termIds = new IntList();

      for (int term : index.translateFromTerms(yfq.getTerms())) {
        if(term == -1 || stopInts.containsInt(term)) {
          continue;
        }
        termIds.push(term);
        if(termIds.size() >= 10) break;
      }


      System.err.println(yfq);
      if(termIds.isEmpty()) {
        System.err.println("EMPTY!");
      } else {
        System.err.println(termIds);
        querySizeStats.push(termIds.size());

        TermPositionsIndex tpi = index.getPositionsIndex("lemmas");

        List<QueryEngine.QCNode<Double>> pnodes = new ArrayList<>();
        for (Integer termId : termIds) {
          QueryEngine.QCNode<Integer> node = tpi.getUnigram(termId);
          assert(node != null);
          pnodes.add(new QueryEngine.LinearSmoothingNode(node));
        }

        QueryEngine.QCNode<Double> ql = new QueryEngine.CombineNode(pnodes);

        long start = System.currentTimeMillis();
        TopKHeap<ScoredDocument> topK = new TopKHeap<>(10, new ScoredDocument.ScoredDocumentComparator());
        Mover m = QueryEngine.createMover(ql);
        ql.setup(tpi);
        for (int i = 0; i < 1; i++) {
          topK.clear();
          m.execute((doc) -> {
            double score = ql.score(tpi, doc);
            if (!Double.isNaN(score)) {
              topK.offer(new ScoredDocument(doc, score));
            }
          });
          m.reset();
        }
        long end = System.currentTimeMillis();

        queryTimeStats.push(end-start);
        System.err.println("# scoring in "+(end-start)+"ms.");
        for (ScoredDocument scoredDocument : topK.getSorted()) {
          String name = index.getNames().getForward((int) scoredDocument.document);
          System.err.println("\t"+name+"\t"+scoredDocument.score);
        }
      }
    }


  }
}
