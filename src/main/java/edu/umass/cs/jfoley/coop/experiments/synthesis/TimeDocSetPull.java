package edu.umass.cs.jfoley.coop.experiments.synthesis;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.front.TermPositionsIndex;
import edu.umass.cs.jfoley.coop.front.eval.EvaluateBagOfWordsMethod;
import edu.umass.cs.jfoley.coop.front.eval.FindHitsMethod;
import edu.umass.cs.jfoley.coop.front.eval.NearbyTermFinder;
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import gnu.trove.map.hash.TIntIntHashMap;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author jfoley
 */
public class TimeDocSetPull {
  public static class CalculateDocSetsByQuery {
    public static void main(String[] args) throws IOException {
      Parameters argp = Parameters.parseArgs(args);
      String dataset = "clue12";

      Map<String, String> queries = new HashMap<>();
      for (String tsvLine : LinesIterable.fromFile("/home/jfoley/code/queries/clue12/web2014.topics.txt").slurp()) {
        String col[] = tsvLine.trim().split("\t");
        queries.put(col[0], col[1]);
      }

      //List<EntityJudgedQuery> queries = ConvertEntityJudgmentData.parseQueries(new File(argp.get("queries", "coop/data/" + dataset + ".json")));
      IntCoopIndex target = new IntCoopIndex(Directory.Read(argp.get("target", "/mnt/scratch3/jfoley/clue12a.sdm.ints")));
      TermPositionsIndex tpos = target.getPositionsIndex("lemmas");
      int passageSize = argp.get("passageSize", 50);

      Map<String, List<TermSlice>> docHits = new HashMap<>();

      StreamingStats queryTimes = new StreamingStats();

      for (int i = 0; i < 1; i++) {
        for (Map.Entry<String, String> query : queries.entrySet()) {
          String qid = query.getKey();
          String text = query.getValue();

          System.err.println(qid + " " + text);
          Parameters queryP = Parameters.create();
          queryP.put("query", text);
          queryP.put("passageSize", passageSize);
          Parameters infoP = Parameters.create();

          FindHitsMethod hitsFinder = new EvaluateBagOfWordsMethod(queryP, infoP, tpos);
          List<DocumentResult<Integer>> hits = hitsFinder.computeTimed();
          //if(i >= 5) {
            queryTimes.push(hitsFinder.output.getLong("queryTime"));
          //}
          int queryFrequency = hits.size();
          if(queryFrequency == 0) {
            System.err.println("# no results found for query="+qid+" "+text);
            continue;
          }

          NearbyTermFinder termFinder = new NearbyTermFinder(target, argp, infoP, passageSize);
          List<TermSlice> merged = IterableFns.intoList(termFinder.hitsToMergedSlices(hits));
          System.err.println("results: "+hits.size()+" afterMerge: "+merged.size());
          docHits.put(qid, merged);
        }
      }

      System.err.println(queryTimes);

      try (ObjectOutputStream oos = new ObjectOutputStream(IO.openOutputStream("clue12.topics14.docHits.javaser.gz"))) {
        oos.writeObject(docHits);
      }

    }
  }

  @SuppressWarnings("unchecked")
  static Map<String, List<TermSlice>> load() throws IOException, ClassNotFoundException {
    try (ObjectInputStream oos = new ObjectInputStream(IO.openInputStream("clue12.topics14.docHits.javaser.gz"))) {
      return (Map<String, List<TermSlice>>) oos.readObject();
    }
  }

  public static class PullToTextFormat {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
      IntCoopIndex target = new IntCoopIndex(Directory.Read("/mnt/scratch3/jfoley/clue12a.sdm.ints"));
      Map<String, List<TermSlice>> docHits = load();
      try (PrintWriter pw = IO.openPrintWriter("clue12a.sdm.topics14.dochits.tsv.gz")) {
        docHits.forEach((qid, hits) -> {
          System.err.println("# "+qid);
          try {
            for (TermSlice hit : hits) {
              String docId = target.getNames().getForward(hit.document);
              List<String> tokens = new ArrayList<>();
              for (Pair<TermSlice, IntList> pair : target.pullTermSlices(Collections.singletonList(hit))) {
                List<String> terms = target.getTermVocabulary().translateForward(pair.right, null);
                tokens.addAll(terms);
              }
              pw.printf("%s\t%s\t%d,%d\t%s\n", qid, docId, hit.start, hit.end, StrUtil.join(tokens));
            }
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
      }
    }
  }

  public static class RunDocSetPullsIntCorpus {


    public static void main(String[] args) throws IOException, ClassNotFoundException {
      boolean flash = false;
      IntCoopIndex target = new IntCoopIndex(Directory.Read(flash ? "/mnt/scratch3/jfoley/clue12a.sdm.ints" : "/mnt/scratch/jfoley/clue12a.sdm.ints"));

      Map<String, List<TermSlice>> docHits = load();

      for (int iter = 0; iter < 3; iter++) {
        for (Map.Entry<String, List<TermSlice>> kv : docHits.entrySet()) {
          System.err.print(kv.getKey()+"\t"+kv.getValue().size());
          TIntIntHashMap termCounts = new TIntIntHashMap(kv.getValue().size()*4);
          int totalOccurrences = 0;
          long startTime = System.currentTimeMillis();
          for (Pair<TermSlice, IntList> doc : target.pullTermSlices(kv.getValue())) {
            IntList terms = doc.getValue();
            for (int i = 0; i < terms.size(); i++) {
              int tid = terms.getQuick(i);
              termCounts.adjustOrPutValue(tid, 1, 1);
            }
            totalOccurrences += terms.size();
          }
          long endTime = System.currentTimeMillis();

          System.err.print("\t"+totalOccurrences);
          System.err.print("\t"+termCounts.size());
          System.err.println("\t"+(endTime - startTime));
        }
      }

    }

  }
}
