package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.random.ReservoirSampler;
import ciir.jfoley.chai.string.StrUtil;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.Tag;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.util.WordLists;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StringPooler;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * @author jfoley
 */
public class SelectQueriesRandomly {

  public static void main(String[] args) throws IOException {
    HashSet<String> stopwords = new HashSet<>(Objects.requireNonNull(WordLists.getWordList("inquery")));
    Parameters json = Parameters.parseStream(IO.openInputStream("coop/data/ecir15.wiki-year-facts.json.gz"));
    List<Parameters> factJSON = json.getAsList("data", Parameters.class);

    // tag tokenizer:
    TagTokenizer tok = new TagTokenizer();
    tok.addField("a");
    StringPooler.disable();

    int id = 1;
    ReservoirSampler<YFQServer.YearFact> facts = new ReservoirSampler<>(100);
    for (Parameters fact : factJSON) {
      String yearStr = fact.getString("year");
      if (yearStr.endsWith("BC")) continue;
      int year = Integer.parseInt(yearStr);
      if (year >= 1000 && year <= 1925) {
        String html = fact.getString("fact");
        boolean nonStopword = false;
        Document doc = tok.tokenize(html);
        for (String term : doc.terms) {
          if(stopwords.contains(term)) continue;
          nonStopword = true; break;
        }

        if(nonStopword) {
          YFQServer.YearFact yf = new YFQServer.YearFact(id++, year, html, doc.terms);

          for (Tag tag : doc.tags) {
            String url = tag.attributes.get("href");
            if(url == null) continue;
            String ent = StrUtil.takeAfter(url, "https://en.wikipedia.org/wiki/");
            yf.addJudgment(new YFQServer.UserRelevanceJudgment(ent, "__enwiki__", 0, 3));
          }
          yf.addJudgment(new YFQServer.UserRelevanceJudgment(Integer.toString(year), "__enwiki__", 0, 3));
          facts.add(yf);
        }
      }
    }

    System.err.println(facts);

    try (PrintWriter output = IO.openPrintWriter("coop/data/sampled-facts.json")) {
      output.println(Parameters.parseArray("facts", ListFns.map(facts, YFQServer.YearFact::asJSON)).toPrettyString());
    }
  }
}
