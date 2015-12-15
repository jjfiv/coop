package edu.umass.cs.jfoley.coop.experiments.generic;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.random.ReservoirSampler;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.xml.ChaiXML;
import ciir.jfoley.chai.xml.XNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.lemurproject.galago.core.eval.QuerySetResults;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.json.JSONUtil;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author jfoley
 */
public class PoolJudgmentsToCSV {
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);
    List<String> runs = argp.getAsList("runs", String.class);
    int cutoff = argp.get("depth", 5);

    LocalRetrieval docStore = null;
    if(argp.isString("index")) {
      docStore=new LocalRetrieval(argp.getString("index"));
    }

    Map<String, Set<String>> toJudgeByQuery = new HashMap<>();
    for (String run : runs) {
      QuerySetResults qres = new QuerySetResults(run);
      for (String qid : qres.getQueryIterator()) {
        qres.get(qid).forEach(evalDoc -> {
          if (evalDoc.getRank() <= cutoff) {
            toJudgeByQuery.computeIfAbsent(qid, missing -> new HashSet<>()).add(evalDoc.getName());
          }
        });
      }
    }

    boolean skipNull = argp.get("skipNull", docStore != null);

    final LocalRetrieval finalDocStore = docStore;
    Cache<String, String> docText = Caffeine.newBuilder().build();
    toJudgeByQuery.forEach((qid, docs) -> {
      for (String doc : docs) {
        String summary = docText.get(doc, missing -> {
          try {
            if (finalDocStore == null) {
              return null;
            }
            Document gdoc = finalDocStore.getDocument(doc, Document.DocumentComponents.JustText);
            if(gdoc == null) return null;
            return JSONUtil.escape(StrUtil.preview(gdoc.text.replace("<TEXT>", "").replace("</TEXT>", ""), 1024));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
        if(skipNull && summary == null) continue;
        System.out.println(qid + "," + doc + ",\"" + summary+"\"");
      }
    });
  }

  public static class CookMTurkInputFile {

    //description,query,e1title,e1abs,e1dblink,e1wlink,e2title,e2abs,e2dblink,e2wlink,e3title,e3abs,e3dblink,e3wlink,e4title,e4abs,e4dblink,e4wlink,e5title,e5abs,e5dblink,e5wlink

    public static void main(String[] args) throws IOException {
      Map<String, String> descriptions = new HashMap<>();
      Map<String, String> titles = new HashMap<>();

      LinesIterable.fromFile("coop/ecir2016runs/mturk/rob04.descs.tsv").slurp().forEach((line) -> {
        String[] col = line.split("\t");
        descriptions.put(col[0], col[1]);
      });
      LinesIterable.fromFile("coop/ecir2016runs/mturk/rob04.titles.tsv").slurp().forEach((line) -> {
        String[] col = line.split("\t");
        titles.put(col[0], col[1]);
      });

      Map<String, String> entToAbs = new HashMap<>();
      Map<String, List<String>> qidToEnt = new HashMap<>();

      try (CSVReader csv = new CSVReader(IO.openReader("coop/ecir2016runs/mturk/robust.needed.csv"))) {
        for (String[] row : csv.readAll()) {
          entToAbs.put(row[1], row[2]);
          qidToEnt.computeIfAbsent(row[0], missing -> new ArrayList<>()).add(row[1]);
        }
      }

      String[] headers = new String[] {
          "description","query","e1title","e1abs","e2title","e2abs","e3title","e3abs","e4title","e4abs","e5title","e5abs"
      };
      List<String[]> allJobs = new ArrayList<>();

      int numDuplicates = 0;
      int pageSize = 5;
      for (Map.Entry<String, List<String>> kv : qidToEnt.entrySet()) {
        String qid = kv.getKey();
        List<String> entitiesToJudge = kv.getValue();

        if(entitiesToJudge.size() < pageSize) {
          System.err.println(qid+"\t"+descriptions.get(qid)+"\t"+titles.get(qid)); // title
          System.err.println("\t"+entitiesToJudge);
          continue;
        }

        int amount = entitiesToJudge.size() % pageSize;
        if(amount > 0) {
          List<String> padLastPage = ReservoirSampler.take(pageSize - amount, entitiesToJudge);
          numDuplicates+= padLastPage.size();
          entitiesToJudge.addAll(padLastPage);
        }

        // assert we fixed the last page:
        assert(entitiesToJudge.size() % pageSize == 0);


        for (List<String> pageOfEntities : IterableFns.batches(entitiesToJudge, 5)) {
          List<String> pageData = new ArrayList<>();
          pageData.add(descriptions.get(qid)); // description
          pageData.add(titles.get(qid)); // title
          for (String ent : pageOfEntities) {
            pageData.add(ent); // e${n}title
            pageData.add(entToAbs.get(ent)); // e${n}abs
          }
          allJobs.add(pageData.toArray(new String[0]));
        }
      }

      System.err.println("Duplicates: "+numDuplicates);

      try (CSVWriter writer = new CSVWriter(IO.openPrintWriter("mturk_robust.csv"))) {
        writer.writeNext(headers);

        for (String[] job : allJobs) {
          writer.writeNext(job);
        }
      }
    }
  }

  public static class Clue12TopicXMLToDescriptions {
    public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException {
      Map<String, String> descriptions = new HashMap<>();
      for (String path : Arrays.asList("/home/jfoley/code/queries/clue12/trec-2014.topics.refined.xml", "/home/jfoley/code/queries/clue12/trec2013-topics.xml")) {
        XNode xNode = ChaiXML.fromFile(path);
        for (XNode topic : xNode.selectByTag("topic")) {
          String qid = topic.attr("number");
          List<XNode> descs = topic.selectByTag("description");
          assert(descs.size() == 1);
          String desc = descs.get(0).getText();
          descriptions.put(qid, desc);
        }
      }

      System.out.println(descriptions.size());

      try(PrintWriter pw = IO.openPrintWriter("/home/jfoley/code/queries/clue12/web1314.descs.tsv")) {
        for (String qid : IterableFns.sorted(descriptions.keySet())) {
          pw.println(qid+"\t"+descriptions.get(qid));
        }
      }
    }
  }

  public static class CookClue12MTurkInputFile {

    //description,query,e1title,e1abs,e1dblink,e1wlink,e2title,e2abs,e2dblink,e2wlink,e3title,e3abs,e3dblink,e3wlink,e4title,e4abs,e4dblink,e4wlink,e5title,e5abs,e5dblink,e5wlink

    public static void main(String[] args) throws IOException {
      Map<String, String> descriptions = new HashMap<>();
      Map<String, String> titles = new HashMap<>();

      LinesIterable.fromFile("coop/ecir2016runs/mturk/clue12.descs.tsv").slurp().forEach((line) -> {
        String[] col = line.split("\t");
        descriptions.put(col[0], col[1]);
      });
      LinesIterable.fromFile("coop/ecir2016runs/mturk/clue12.titles.tsv").slurp().forEach((line) -> {
        String[] col = line.split("\t");
        titles.put(col[0], col[1]);
      });

      Map<String, String> entToAbs = new HashMap<>();
      Map<String, List<String>> qidToEnt = new HashMap<>();

      try (CSVReader csv = new CSVReader(IO.openReader("coop/ecir2016runs/mturk/clue12.needed.csv"))) {
        for (String[] row : csv.readAll()) {
          entToAbs.put(row[1], row[2]);
          qidToEnt.computeIfAbsent(row[0], missing -> new ArrayList<>()).add(row[1]);
        }
      }

      String[] headers = new String[] {
          "description","query","e1title","e1abs","e2title","e2abs","e3title","e3abs","e4title","e4abs","e5title","e5abs"
      };
      List<String[]> allJobs = new ArrayList<>();

      int numDuplicates = 0;
      int pageSize = 5;
      for (Map.Entry<String, List<String>> kv : qidToEnt.entrySet()) {
        String qid = kv.getKey();
        List<String> entitiesToJudge = kv.getValue();

        if(entitiesToJudge.size() < pageSize) {
          System.err.println(qid+"\t"+descriptions.get(qid)+"\t"+titles.get(qid)); // title
          System.err.println("\t"+entitiesToJudge);
          continue;
        }

        int amount = entitiesToJudge.size() % pageSize;
        if(amount > 0) {
          List<String> padLastPage = ReservoirSampler.take(pageSize - amount, entitiesToJudge);
          numDuplicates+= padLastPage.size();
          entitiesToJudge.addAll(padLastPage);
        }

        // assert we fixed the last page:
        assert(entitiesToJudge.size() % pageSize == 0);


        for (List<String> pageOfEntities : IterableFns.batches(entitiesToJudge, 5)) {
          List<String> pageData = new ArrayList<>();
          pageData.add(descriptions.get(qid)); // description
          pageData.add(titles.get(qid)); // title
          for (String ent : pageOfEntities) {
            pageData.add(ent); // e${n}title
            pageData.add(entToAbs.get(ent)); // e${n}abs
          }
          allJobs.add(pageData.toArray(new String[0]));
        }
      }

      System.err.println("Duplicates: "+numDuplicates);

      try (CSVWriter writer = new CSVWriter(IO.openPrintWriter("mturk_clue12.csv"))) {
        writer.writeNext(headers);

        for (String[] job : allJobs) {
          writer.writeNext(job);
        }
      }
    }
  }

}
