package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.random.ReservoirSampler;
import ciir.jfoley.chai.string.StrUtil;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.Tag;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    public YearFactQuery(int year, String html) {
      this.year = year;
      this.html = html;
    }

    @Override
    public String toString() {
      TagTokenizer tok = new TagTokenizer();
      tok.addField("a");
      Document doc = tok.tokenize(html);

      List<EntityMention> mentions = new ArrayList<>();
      for (Tag link : doc.tags) {
        String url = link.attributes.get("href");
        int begin = link.begin;
        int end = link.end;
        String mentionText = html.substring(link.charBegin, link.charEnd);
        String ent = StrUtil.takeAfter(url, "https://en.wikipedia.org/wiki/");
        mentions.add(new EntityMention(ent, mentionText, begin, end));
      }

      StringBuilder sb = new StringBuilder();
      sb.append(year).append('\t');
      for (String term : doc.terms) {
        sb.append(' ').append(term);
      }
      sb.append('\t');
      sb.append(mentions);
      return sb.toString();
    }
  }

  public static void main(String[] args) throws IOException {
    Parameters json = Parameters.parseStream(IO.openInputStream("coop/data/ecir15.wiki-year-facts.json.gz"));
    List<Parameters> facts = json.getAsList("data", Parameters.class);
    System.out.println(facts.size());
    System.out.println(facts.get(0).toPrettyString());

    List<YearFactQuery> keep = new ArrayList<>();
    ReservoirSampler<YearFactQuery> random = new ReservoirSampler<>(50);
    for (Parameters fact : facts) {
      String yearStr = fact.getString("year");
      if(yearStr.endsWith("BC")) continue;
      int year = Integer.parseInt(yearStr);
      if(year >= 1000 && year <= 1925) {
        YearFactQuery yfq = new YearFactQuery(year, fact.getString("fact"));
        keep.add(yfq);
        random.add(yfq);
      }
    }

    System.err.println("Found "+keep.size()+" queries from ECIR15");
    for (YearFactQuery yfq : random) {
      System.err.println(yfq);
    }


  }
}
