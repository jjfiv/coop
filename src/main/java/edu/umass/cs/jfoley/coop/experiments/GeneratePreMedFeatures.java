package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.string.StrUtil;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StringPooler;
import org.lemurproject.galago.utility.tools.Arguments;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author jfoley
 */
public class GeneratePreMedFeatures {
  public static class FactQueryObject {
    YFQServer.YearFact fact;
    List<String> tokens;
    List<YFQServer.UserSubmittedQuery> labels;
  }

  public static void main(String[] args) throws IOException {
    Parameters argp = Arguments.parse(args);
    Directory factsDir = Directory.Read(argp.get("save", "coop/sampled.db.saves"));
    File input = Objects.requireNonNull(YFQServer.getNewestSave(factsDir));
    List<Parameters> facts = Parameters.parseStream(IO.openInputStream(input)).getAsList("facts", Parameters.class);

    StringPooler.disable();
    TagTokenizer tok = new TagTokenizer();

    int qNum = 0;
    int numFacts = 0;

    for (Parameters factJ : facts) {
      YFQServer.YearFact fact = YFQServer.YearFact.parseJSON(factJ);
      if(fact.hasQueries()) {
        numFacts++;
        System.err.println(fact.getHtml());
        Map<List<String>,YFQServer.UserSubmittedQuery> oldestQueries = new HashMap<>();
        for (YFQServer.UserSubmittedQuery usq : fact.getQueries()) {
          if(usq.isDeleted()) continue;
          List<String> qt = tok.tokenize(usq.query).terms;
          if(qt.isEmpty()) continue;

          if(oldestQueries.containsKey(qt)) {
            // keep the oldest:
            oldestQueries.computeIfPresent(qt, (terms, query) -> {
              if (query.time < usq.time) {
                return query;
              } else {
                return usq;
              }
            });
          } else {
            oldestQueries.put(qt, usq);
          }
        }

        for (Map.Entry<List<String>, YFQServer.UserSubmittedQuery> pair : oldestQueries.entrySet()) {
          int index = qNum++;
          System.err.println("\tfact["+numFacts+"]"+"q["+index+"]=\t"+pair.getValue().id+"\t"+ StrUtil.join(pair.getKey()));
        }
      }
    }
  }
}
