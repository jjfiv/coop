package edu.umass.cs.jfoley.coop.experiments.synthesis;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.time.Debouncer;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.front.TermPositionsIndex;
import edu.umass.cs.jfoley.coop.front.eval.EvaluateBagOfWordsMethod;
import edu.umass.cs.jfoley.coop.front.eval.FindHitsMethod;
import edu.umass.cs.jfoley.coop.front.eval.NearbyTermFinder;
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StringPooler;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author jfoley
 */
public class TimeDocSetPull {
  public static class CalculateDocSetsByQuery {
    public static void main(String[] args) throws IOException {
      Parameters argp = Parameters.parseArgs(args);
      String dataset = "clue12";

      Map<String, String> queries = new HashMap<>();
      for (String tsvLine : LinesIterable.fromFile("/home/jfoley/code/queries/robust04/rob04.titles.tsv").slurp()) {
        String col[] = tsvLine.trim().split("\t");
        queries.put(col[0], col[1]);
      }

      IntCoopIndex target = new IntCoopIndex(Directory.Read(argp.get("target", "/mnt/scratch3/jfoley/robust.ints")));
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

      try (ObjectOutputStream oos = new ObjectOutputStream(IO.openOutputStream("robust.docHits.javaser.gz"))) {
        oos.writeObject(docHits);
      }

    }
  }

  @SuppressWarnings("unchecked")
  static Map<String, List<TermSlice>> load(String what) throws IOException, ClassNotFoundException {
    try (ObjectInputStream oos = new ObjectInputStream(IO.openInputStream(what))) {
      return (Map<String, List<TermSlice>>) oos.readObject();
    }
  }

  public static class PullToTextFormat {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
      IntCoopIndex target = new IntCoopIndex(Directory.Read("/mnt/scratch3/jfoley/robust.ints"));
      Map<String, List<TermSlice>> docHits = load("robust.docHits.javaser.gz");
      try (PrintWriter pw = IO.openPrintWriter("robust.dochits.tsv.gz")) {
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

      Map<String, List<TermSlice>> docHits = load("clue12.docHits.javaser.gz");

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

  public static class PullSnippetsGalagoBased {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
      boolean flash = true;
      IntCoopIndex ic = new IntCoopIndex(Directory.Read("/mnt/scratch3/jfoley/clue12a.sdm.ints"));
      LocalRetrieval target = new LocalRetrieval(flash ? "/mnt/scratch3/jfoley/Clue12A.subset.galago/Clue12A-Subindex-FULL" : "/mnt/scratch/jfoley/Clue12A.subset.galago/Clue12A-Subindex-FULL");

      StringPooler.disable();

      TIntObjectHashMap<String> names = new TIntObjectHashMap<>();
      Map<String, List<TermSlice>> docHits = load("clue12.docHits.javaser.gz");
      docHits.forEach((qid, slices) -> {
        for (TermSlice slice : slices) {
          try {
            String docId = ic.getNames().getForward(slice.document);
            names.put(slice.document, docId);
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      });

      for (int iter = 0; iter < 3; iter++) {
        for (Map.Entry<String, List<TermSlice>> kv : docHits.entrySet()) {
          System.err.print(kv.getKey()+"\t"+kv.getValue().size());
          TObjectIntHashMap<String> termCounts = new TObjectIntHashMap<>(kv.getValue().size()*4);
          int totalOccurrences = 0;
          long startTime = System.currentTimeMillis();

          List<String> namesForSlices = new ArrayList<>();
          for (TermSlice slice : kv.getValue()) {
            String docId = names.get(slice.document);
            namesForSlices.add(docId);
          }
          Map<String, Document> documents = target.getDocuments(namesForSlices, Document.DocumentComponents.All);

          for (TermSlice slice : kv.getValue()) {
            String docId = names.get(slice.document);
            Document gdoc = documents.get(docId);
            List<String> terms = ListFns.slice(gdoc.terms, slice.start, slice.end);
            for (String term : terms) {
              termCounts.adjustOrPutValue(term, 1, 1);
            }
          }
          long endTime = System.currentTimeMillis();

          System.err.print("\t"+totalOccurrences);
          System.err.print("\t"+termCounts.size());
          System.err.println("\t"+(endTime - startTime));
        }
      }
    }
  }
  public static class PullRawSnippetsRobustGalagoBased {
    public static void main(String[] args) throws IOException, ClassNotFoundException {

      String dataset = "robust";
      LocalRetrieval ret = new LocalRetrieval("/mnt/scratch3/jfoley/robust04.galago");
      Cache<String, Document> docCache = Caffeine.newBuilder().maximumSize(50_000).build();

      Debouncer msg = new Debouncer();
      try (PrintWriter rawSnippet = IO.openPrintWriter("/mnt/scratch3/jfoley/snippets/"+dataset+".rawsnippets.tsv.gz")) {
        try (LinesIterable snippetLines = LinesIterable.fromFile("/mnt/scratch3/jfoley/snippets/" + dataset + ".snippets.tsv.gz")) {
          for (String snippetLine : snippetLines) {
            String[] cols = snippetLine.split("\t");
            String qid = cols[0];
            String docId = cols[1];
            int begin = Math.max(0, Integer.parseInt(StrUtil.takeBefore(cols[2], ",")));
            int end = Integer.parseInt(StrUtil.takeAfter(cols[2], ","));


            Document doc = docCache.get(docId, (id) -> {
              try {
                return ret.getDocument(docId, Document.DocumentComponents.All);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });

            int real_end = Math.min(end, doc.terms.size() - 1);

            int rawStart = doc.termCharBegin.get(begin);
            int rawEnd = doc.termCharEnd.get(real_end);

            // highly-robust specific for now...
            String rawText =
                StrUtil.compactSpaces(
                    StrUtil.transformBetween(
                        StrUtil.transformBetween(
                            doc.text.substring(rawStart, rawEnd), Pattern.compile("<!--"), Pattern.compile("-->"), (input) -> " ")
                        , Pattern.compile("</?"), Pattern.compile(">"), (input) -> " "))
                    .replaceAll("&hyph;", "-")
                    .replaceAll("&mdash;", "-")
                    .replaceAll("&ndash;", "-")
                    .replaceAll("&amp;", "&")
                    .replaceAll("&lt;", "<")
                    .replaceAll("&gt;", ">");

            if(msg.ready()) {
              System.err.println(qid + ", " + docId + ", " + begin + ", " + end);
              System.err.println(rawText);
              System.err.println(StrUtil.join(ListFns.slice(doc.terms, begin, end), " "));
              System.err.println();
            }

            rawSnippet.printf("%s\t%s\t%d,%d\t%d,%d\t%s\n", qid, docId, begin, real_end, rawStart, rawEnd, rawText);
          }
        }
      }




    }
  }
}
