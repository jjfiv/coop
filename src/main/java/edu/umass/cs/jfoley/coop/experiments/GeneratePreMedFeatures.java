package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.string.StrUtil;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StringPooler;
import org.lemurproject.galago.utility.tools.Arguments;

import java.io.File;
import java.io.IOException;
import java.util.*;

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
    System.out.println(input.getName());
    List<Parameters> facts = Parameters.parseStream(IO.openInputStream(input)).getAsList("facts", Parameters.class);

    StringPooler.disable();
    TagTokenizer tok = new TagTokenizer();

    int qNum = 0;
    int numFacts = 0;

    for (Parameters factJ : facts) {
      YFQServer.YearFact fact = YFQServer.YearFact.parseJSON(factJ);

      List<YFQServer.UserSubmittedQuery> relQueries = new ArrayList<>();
      for (YFQServer.UserSubmittedQuery usq : fact.getQueries()) {
        if (usq.isDeleted()) continue;
        if (usq.user.contains("jfoley")) continue; // throw out my queries, not scientific
        relQueries.add(usq);
      }

      if(!relQueries.isEmpty()) {
        numFacts++;
        System.err.println(fact.getHtml());
        Map<List<String>, List<YFQServer.UserSubmittedQuery>> oldestQueries = new HashMap<>();
        for (YFQServer.UserSubmittedQuery usq : relQueries) {
          List<String> qt = tok.tokenize(usq.query).terms;
          if(qt.isEmpty()) continue;

          MapFns.extendListInMap(oldestQueries, qt, usq);
        }

        for (Map.Entry<List<String>, List<YFQServer.UserSubmittedQuery>> pair : oldestQueries.entrySet()) {
          List<YFQServer.UserSubmittedQuery> queries = pair.getValue();
          Collections.sort(queries, (lhs, rhs) -> Long.compareUnsigned(lhs.time, rhs.time));
          YFQServer.UserSubmittedQuery oldest = queries.get(0);
          Set<String> users = new HashSet<>();
          for (YFQServer.UserSubmittedQuery query : queries) {
            users.add(query.user);
          }
          int index = qNum++;
          System.err.println("\tfact["+numFacts+"]"+"q["+index+"]=\t"+oldest.id+"\t"+ StrUtil.join(pair.getKey()) + "\t--\t" + StrUtil.join(new ArrayList<>(users)));
          System.err.println("\t\t"+ ListFns.map(fact.getJudgments(), j -> j.item));
        }
      }
    }
  }
}
