package edu.umass.cs.jfoley.coop.erm;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.front.PhrasePositionsIndex;
import edu.umass.cs.jfoley.coop.phrases.PhraseHit;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitList;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitsReader;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.core.retrieval.Results;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.core.retrieval.prf.RelevanceModel1;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.lists.Ranked;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author jfoley
 */
public class ExpertSearchExperiment {
  public static class ExpertCandidate {
    final int number;
    final String name;
    final String email;
    final String id;

    public ExpertCandidate(int number, String name, String email, String id) {
      this.number = number;
      this.name = name;
      this.email = email;
      this.id = id;
    }
  }
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);
    Directory indexDir = Directory.Read(argp.get("index", "/mnt/scratch3/jfoley/w3c.ints"));

    final String method = argp.get("method", "drm");
    final String year = "06";

    HashMap<Integer, ExpertCandidate> kb = new HashMap<>();
    for (String line : LinesIterable.fromFile("/home/jfoley/data/enterprise_track/ent05.expert.candidates").slurp()) {
      String col[] = line.split("\\s+");
      if (col.length < 3) {
        continue;
      }
      List<String> cols = Arrays.asList(col);

      String id = col[0];
      String name = StrUtil.join(ListFns.slice(cols, 1, col.length - 1));
      String email = col[col.length - 1];
      assert (id.startsWith("candidate-"));
      int numericId = Integer.parseInt(StrUtil.takeAfter(id, "-"));

      kb.put(numericId, new ExpertCandidate(numericId, name, email, id));
    }

    String topics = IO.slurp(new File("/home/jfoley/data/enterprise_track/ent"+year+".expert.topics"));
    //System.out.println(topics);
    Document parse = Jsoup.parse(topics);

    LocalRetrieval galagoRet = new LocalRetrieval(argp.get("galago", "/mnt/scratch3/jfoley/w3c.galago"));
    Map<String, String> queries = new HashMap<>();
    TagTokenizer tok = new TagTokenizer();

    for (Element top : parse.select("top")) {
      String num = StrUtil.takeAfter(top.select("num").text(), "Number:").trim();
      String title = top.select("title").text();
      String desc = top.select("desc").text();
      System.out.println(num);

      queries.put(num, title);
    }

    int fbDocs = argp.get("fbDocs", 100);

    IntCoopIndex target = new IntCoopIndex(indexDir);
    PhraseHitsReader eIndex = new PhraseHitsReader(target, target.baseDir, "experts");
    PhrasePositionsIndex entitiesIndex = new PhrasePositionsIndex(eIndex, target.getTermVocabulary(), eIndex.getPhraseVocab(), eIndex.getDocumentsByPhrase());
    IOMap<Integer, IntList> mentionToEntities = eIndex.getAmbiguousPhrases();

    try (PrintWriter trecrun = IO.openPrintWriter(argp.get("output", method+".expert20"+year+".n"+fbDocs+".trecrun"))) {
      for (Map.Entry<String, String> kv : queries.entrySet()) {
        String qid = kv.getKey();
        String text = kv.getValue();

        List<String> terms = tok.tokenize(text).terms;

        Node sdm = new Node("sdm");
        sdm.addTerms(terms);

        Parameters qp = argp.clone();
        qp.put("requested", fbDocs);
        Results res = galagoRet.transformAndExecuteQuery(sdm, qp); // mu, uniw, odw, uww
        Map<String, Double> logstoposteriors = MapFns.mapKeys(RelevanceModel1.logstoposteriors(res.scoredDocuments), ScoredDocument::getName);
        TIntObjectHashMap<String> idToName = new TIntObjectHashMap<>();
        IntList topDocIds = new IntList();
        for (Pair<String, Integer> docIdPair : target.getNames().getReverse(new ArrayList<>(logstoposteriors.keySet()))) {
          topDocIds.add(docIdPair.getValue());
          idToName.put(docIdPair.getValue(), docIdPair.getKey());
        }

        long start = System.currentTimeMillis();
        List<Pair<Integer, PhraseHitList>> inBulk = eIndex.getDocumentHits().getInBulk(topDocIds);
        long end = System.currentTimeMillis();
        System.out.println("Data pull: " + (end - start) + "ms. for " + topDocIds.size() + " documents.");

        Map<Integer, TIntIntHashMap> countsByDoc = new HashMap<>();
        HashSet<Integer> allEntities = new HashSet<>();

        for (Pair<Integer, PhraseHitList> pair : inBulk) {
          TIntIntHashMap ecounts = countsByDoc.computeIfAbsent(pair.getKey(), missing -> new TIntIntHashMap());
          PhraseHitList dochits = pair.getValue();
          for (PhraseHit dochit : dochits) {
            allEntities.add(dochit.id());
            ecounts.adjustOrPutValue(dochit.id(), 1, 1);
          }
          ecounts.put(-1, dochits.size()); // length
          assert (ecounts.getNoEntryValue() == 0) : "No entry value should be zero!";
        }

        start = System.currentTimeMillis();
        TIntIntHashMap freq = entitiesIndex.getCollectionFrequencies(new IntList(allEntities));
        end = System.currentTimeMillis();
        System.out.println("Pull efrequencies: " + (end - start) + "ms.");

        TObjectIntHashMap<String> entityTimesScored = new TObjectIntHashMap<>();
        TIntObjectHashMap<List<String>> mentionIdToStringNames = new TIntObjectHashMap<>();
        for (int eid : allEntities) {
          IntList ids = mentionToEntities.get(eid);
          if (ids == null) {
            throw new RuntimeException();
          }
          HashSet<String> names = new HashSet<>();
          for (Integer id : ids) {
            ExpertCandidate cc = kb.get(id);
            if (cc == null) continue;
            names.add(cc.id);
          }
          if (names.isEmpty()) continue;
          mentionIdToStringNames.put(eid, new ArrayList<>(names));
        }
        double clen = entitiesIndex.getCollectionLength();

        final TopKHeap<ScoredDocument> topEntities = new TopKHeap<>(1000);

        Map<String, Double> entityWeights = new HashMap<>();

        mentionIdToStringNames.forEachEntry((mid, names) -> {
          double score = 0;
          double cf = freq.get(mid);
          for (int i = 0; i < topDocIds.size(); i++) {
            int docId = topDocIds.getQuick(i);
            TIntIntHashMap bagOfEntities = countsByDoc.get(docId);
            if(bagOfEntities == null) continue;
            int count = bagOfEntities.get(mid);
            if (count == 0) continue;
            double length = bagOfEntities.get(-1);
            double docProb = logstoposteriors.get(idToName.get(docId));

            switch (method) {
              case "rm":
                // relevance model:
                score += docProb * (count / length);
                assert (Double.isFinite(score));
                break;
              case "drm":
              case "lce":
                // LCE:
                //score += docProb * (count / length) / (cf / clen);
                double odds = (clen * count) / (length * cf);
                score += docProb * odds;
                assert (Double.isFinite(score));
                break;
              case "gdrm":
              case "and-lce":
                score += Math.log(docProb) + Math.log(count / length) - Math.log(cf / clen);
                assert (Double.isFinite(score));
                break;
              case "gnb":
              case "pmi":
                // reduced-PMI:
                score += Math.log(count / length) - Math.log(cf / clen); // leaving out constant p(q) which would be in denominator
                assert (Double.isFinite(score));
                break;
              default:
                throw new UnsupportedOperationException(method);
            }
          }

          assert (score != 0);
          for (String name : names) {
            double before = entityWeights.getOrDefault(name, 0.0);
            entityWeights.put(name, before + score);
            entityTimesScored.adjustOrPutValue(name, 1, 1);
            //topEntities.offer(new ScoredDocument(name, -1, score));
          }

          return true;
        });

        for (Map.Entry<String, Double> enameScore : entityWeights.entrySet()) {
          String name = enameScore.getKey();
          double score = enameScore.getValue();
          int n = entityTimesScored.get(name);
          topEntities.offer(new ScoredDocument(name, -1, score  / ((double) n)));
        }

        // print results
        List<ScoredDocument> scoredEntities = topEntities.getSorted();
        Ranked.setRanksByScore(scoredEntities);

        for (ScoredDocument entity : scoredEntities) {
          trecrun.println(entity.toTRECformat(qid, method));
        }
      } // for queries
    } // close trecrun
  } // main
}
