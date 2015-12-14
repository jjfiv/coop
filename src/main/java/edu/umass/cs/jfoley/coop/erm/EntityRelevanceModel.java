package edu.umass.cs.jfoley.coop.erm;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.entityco.ConvertEntityJudgmentData;
import edu.umass.cs.jfoley.coop.entityco.EntityJudgedQuery;
import edu.umass.cs.jfoley.coop.front.PhrasePositionsIndex;
import edu.umass.cs.jfoley.coop.phrases.PhraseHit;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.core.retrieval.Results;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.core.retrieval.prf.RelevanceModel1;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.core.util.WordLists;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.lists.Ranked;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author jfoley
 */
public class EntityRelevanceModel {
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);

    String dataset = "robust04";
    int fbDocs = argp.get("fbDocs", 50);

    String index = "/mnt/scratch3/jfoley/robust.ints";
    Set<String> stopwords = WordLists.getWordListOrDie("inquery");

    List<EntityJudgedQuery> queries = ConvertEntityJudgmentData.parseQueries(new File(argp.get("queries", "coop/data/" + dataset + ".json")));

    assert(queries.size() > 0);

    Collections.sort(queries, (lhs, rhs) -> lhs.qid.compareTo(rhs.qid));

    LocalRetrieval galagoRet = new LocalRetrieval(argp.get("galago", "/mnt/scratch3/jfoley/robust.galago"));

    IntCoopIndex dbpedia = new IntCoopIndex(Directory.Read(argp.get("dbpedia", "/mnt/scratch3/jfoley/dbpedia.ints")));
    IntCoopIndex target = new IntCoopIndex(Directory.Read(argp.get("target", index)));
    PhrasePositionsIndex eIndex = target.getEntitiesIndex();
    IOMap<Integer, IntList> ambiguous = eIndex.getPhraseHits().getAmbiguousPhrases();
    assert(ambiguous != null);

    TagTokenizer tok = new TagTokenizer();

    HashMap<String, Results> resultsForQuery = new HashMap<>();

    for (String method : Arrays.asList("binary-pmi", "pmi", "set-pmi", "rm", "and-rm", "and-lce", "lce", "bayes-lce")) {
      long start, end;
      try (PrintWriter trecrun = IO.openPrintWriter(argp.get("output", method+"."+dataset + ".n"+fbDocs+".trecrun"))) {
        for (EntityJudgedQuery query : queries) {
          String qid = query.qid;

          System.out.println(qid);

          Results res = resultsForQuery.computeIfAbsent(qid, missing -> {
            List<String> tokens = tok.tokenize(query.getText()).terms;

            Node sdm = new Node("sdm");
            sdm.addTerms(tokens);
            Parameters qp = argp.clone();
            qp.put("requested", fbDocs);
            return galagoRet.transformAndExecuteQuery(sdm, qp); // mu, uniw, odw, uww
          });

          Map<String, Double> logstoposteriors = MapFns.mapKeys(RelevanceModel1.logstoposteriors(res.scoredDocuments), ScoredDocument::getName);


          TIntObjectHashMap<String> idToName = new TIntObjectHashMap<>();
          IntList topDocIds = new IntList();
          for (Pair<String, Integer> docIdPair : target.getNames().getReverse(new ArrayList<>(logstoposteriors.keySet()))) {
            topDocIds.add(docIdPair.getValue());
            idToName.put(docIdPair.getValue(), docIdPair.getKey());
          }

          start = System.currentTimeMillis();
          List<Pair<Integer, PhraseHitList>> inBulk = eIndex.getPhraseHits().getDocumentHits().getInBulk(topDocIds);
          end = System.currentTimeMillis();

          System.out.println("Data pull: "+(end-start)+"ms. for "+topDocIds.size()+" documents.");

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
            assert(ecounts.getNoEntryValue() == 0) : "No entry value should be zero!";
          }

          start = System.currentTimeMillis();
          TIntIntHashMap freq = eIndex.getCollectionFrequencies(new IntList(allEntities));
          end = System.currentTimeMillis();
          System.out.println("Pull efrequencies: " + (end - start) + "ms.");

          TIntObjectHashMap<List<String>> mentionIdToStringNames = new TIntObjectHashMap<>();
          for (int eid : allEntities) {
            IntList ids = ambiguous.get(eid);
            if (ids == null) {
              ids = new IntList();
              ids.push(eid);
            }
            List<String> names = new ArrayList<>();
            for (String dbpediaName: dbpedia.getNames().getForwardMap(ids).values()) {
              List<String> terms = tok.tokenize(IntCoopIndex.parseDBPediaTitle(dbpediaName)).terms;
              if(terms.size() == 1) {
                if(stopwords.contains(terms.get(0))) break;
              }
              names.add(dbpediaName);
            }
            if(names.isEmpty()) continue;
            mentionIdToStringNames.put(eid, names);
          }
          double clen = eIndex.getCollectionLength();
          double numDocs = eIndex.getNumDocuments();

          TopKHeap<ScoredDocument> topEntities = new TopKHeap<>(1000);

          mentionIdToStringNames.forEachEntry((eid, names) -> {
            Map<String, Double> scores = new HashMap<>();
            double score = 0;
            int relDocFreq = 0;
            int relDocCount = 0;
            int relDocLength = 0;
            double cf = freq.get(eid);
            for (int i = 0; i < topDocIds.size(); i++) {
              int docId = topDocIds.getQuick(i);
              int count = countsByDoc.get(docId).get(eid);
              if(count == 0) continue;
              double length = countsByDoc.get(docId).get(-1);
              double docProb = logstoposteriors.get(idToName.get(docId));

              relDocFreq++;

              switch (method) {
                case "rm":
                  // relevance model:
                  // mAP = .02
                  score += docProb * (count / length);
                  assert(Double.isFinite(score));
                  break;
                case "lce":
                  // LCE:
                  // mAP = .05
                  //score += docProb * (count / length) / (cf / clen);
                  double odds = (clen * count) / (length * cf);
                  score += docProb * odds;
                  assert(Double.isFinite(score));
                  break;
                case "and-rm":
                  score += Math.log(docProb) + Math.log(count / length);
                  assert(Double.isFinite(score));
                  break;
                case "and-lce":
                  // mAP = .09
                  score += Math.log(docProb) + Math.log(count / length) - Math.log(cf / clen);
                  assert(Double.isFinite(score));
                  break;
                case "pmi":
                  // reduced-PMI:
                  // mAP = .03
                  score += Math.log(count / length) - Math.log(cf / clen); // leaving out constant p(q) which would be in denominator
                  assert(Double.isFinite(score));
                  break;
                case "binary-pmi":
                case "set-pmi":
                  relDocCount += count;
                  relDocLength += length;
                  break;
                case "bayes-lce":
                  // filtered LCE:
                  // mAP = .05
                  double logOdds = Math.log(count / length) - Math.log(cf / clen);
                  if(logOdds > 0) {
                    // LCE:
                    //score += docProb * (count / length) / (cf / clen);
                    score += docProb * Math.exp(logOdds);
                    assert(Double.isFinite(score));
                  }
                  break;
                default:
                  throw new UnsupportedOperationException(method);
              }
            }


            switch (method) {
              case "binary-pmi": {
                if(relDocFreq == 0 || eIndex.getDF(eid) == 0) return true;
                double probEQ = relDocFreq / (double) fbDocs;
                double probE = eIndex.getDF(eid) / numDocs;
                //double probQ = fbDocs / numDocs;
                score = Math.log(probEQ) - Math.log(probE); // - Math.log(probQ);
                assert(probEQ > 0) : Parameters.parseArray("probEQ", probEQ, "relDocFreq", relDocFreq, "relDocCount", topDocIds.size(), "fbDocs", fbDocs).toString();
                assert(probE > 0);
                assert(Double.isFinite(Math.log(probE)));
                assert(Double.isFinite(Math.log(probEQ)));
                assert(Double.isFinite(Math.log(probE)));
                assert(Double.isFinite(score));
              } break;
              case "set-pmi": {
                if(relDocCount == 0 || freq.get(eid) == 0) return true;
                double probEQ = relDocCount / (double) relDocLength;
                double probE = freq.get(eid) / clen;
                //double probQ = relDocLength / clen;
                score = Math.log(probEQ) - Math.log(probE); // - Math.log(probQ);
                assert(Double.isFinite(score));
              } break;
              default: break;
            }

            if(scores.isEmpty()) {
              for (String name : names) {
                topEntities.offer(new ScoredDocument(name, -1, score));
              }
            } else {
              for (Map.Entry<String, Double> kv : scores.entrySet()) {
                topEntities.offer(new ScoredDocument(kv.getKey(), -1, kv.getValue()));
              }
            }

            return true;
          });

          List<ScoredDocument> scoredEntities = topEntities.getSorted();
          Ranked.setRanksByScore(scoredEntities);

          for (ScoredDocument entity : scoredEntities) {
            if(entity.rank < 4) {
              System.out.println("\t"+entity.documentName+" "+entity.score);
            }
            trecrun.println(entity.toTRECformat(qid, "jfoley-entrm"));
          }
        }
      }
    }
  }
}
