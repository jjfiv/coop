package edu.umass.cs.jfoley.coop.erm;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.entityco.ConvertEntityJudgmentData;
import edu.umass.cs.jfoley.coop.entityco.EntityJudgedQuery;
import edu.umass.cs.jfoley.coop.entityco.PMIRankingExperiment;
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

  public static HashMap<String, Double> staticPrior(String fileName) {
    HashMap<String, Double> vals = new HashMap<>();

    try (LinesIterable lines = LinesIterable.fromFile(fileName)) {
      for (String line : lines) {
        String[] data = line.split("\t");
        String id = data[0];
        double score = Double.parseDouble(data[1]);
        vals.put(id, score);
      }
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
    return vals;
  }

  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);

    String dataset = argp.get("dataset", "robust04");
    int fbDocs = argp.get("fbDocs", 100);

    String index = "/mnt/scratch3/jfoley/robust.ints";
    Set<String> stopwords = WordLists.getWordListOrDie("inquery");

    List<EntityJudgedQuery> queries = ConvertEntityJudgmentData.parseQueries(new File(argp.get("queries", "coop/data/" + dataset + ".json")));

    //Map<String, Map<String, Double>> staticPriors = new HashMap<>();
    //staticPriors.put("pagerank", staticPrior("wikipagerank.tsv.gz"));
    //staticPriors.put("facc", staticPrior("facc_09_ecounts.tsv.gz"));

    assert(queries.size() > 0);

    Collections.sort(queries, (lhs, rhs) -> lhs.qid.compareTo(rhs.qid));

    LocalRetrieval galagoRet = new LocalRetrieval(argp.get("galago", "/mnt/scratch3/jfoley/robust.galago"));

    IntCoopIndex dbpedia = new IntCoopIndex(Directory.Read(argp.get("dbpedia", "/mnt/scratch3/jfoley/dbpedia.ints")));
    IntCoopIndex target = new IntCoopIndex(Directory.Read(argp.get("target", index)));
    PhrasePositionsIndex eIndex = target.getEntitiesIndex();
    IOMap<Integer, IntList> ambiguous = eIndex.getPhraseHits().getAmbiguousPhrases();
    assert(ambiguous != null);

    TagTokenizer tok = new TagTokenizer();

    // for mention->entity probs:
    LocalRetrieval jeffWiki = PMIRankingExperiment.openJeffWiki(argp);

    HashMap<String, Results> resultsForQuery = new HashMap<>();
    HashMap<String, Results> entityPriorResultsForQuery = new HashMap<>();

    //for (String method : Arrays.asList("wrm")) {
    for (String method : Arrays.asList("rm", "wrm", "wpmi", "and-lce", "lce", "lce-prior", "and-lce-prior")) {
      long start, end;
      try (PrintWriter trecrun = IO.openPrintWriter(argp.get("output", method+"."+dataset + ".n"+fbDocs+".trecrun"))) {
        for (EntityJudgedQuery query : queries) {
          String qid = query.qid;

          // generate query:
          List<String> tokens = tok.tokenize(query.getText()).terms;
          Node sdm = new Node("sdm");
          sdm.addTerms(tokens);

          System.out.println(qid+" "+tokens);

          Results res = resultsForQuery.computeIfAbsent(qid, missing -> {
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
          Set<String> allWikiEntities = new HashSet<>();
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
            allWikiEntities.addAll(names);
            mentionIdToStringNames.put(eid, names);
          }
          double clen = eIndex.getCollectionLength();
          double numDocs = eIndex.getNumDocuments();

          TopKHeap<ScoredDocument> topEntities = new TopKHeap<>(1000);

          Results eRes = entityPriorResultsForQuery.computeIfAbsent(qid, missing -> {
            Parameters eqp = Parameters.create();
            eqp.put("working", new ArrayList<>(allWikiEntities));
            eqp.put("warnMissingDocuments", false);
            eqp.put("requested", allWikiEntities.size());
            System.out.println("Score "+allWikiEntities.size()+" wiki pages.");
            return jeffWiki.transformAndExecuteQuery(sdm, eqp);
          });

          Map<String, Double> ePosterior = MapFns.mapKeys(RelevanceModel1.logstoposteriors(eRes.scoredDocuments), ScoredDocument::getName);

          mentionIdToStringNames.forEachEntry((eid, names) -> {
            Map<String, Double> scores = new HashMap<>();
            boolean multiScore = false;
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
                  score += docProb * (count / length);
                  assert(Double.isFinite(score));
                  break;
                case "wrm": {
                  multiScore = true;
                  for (String name : names) {
                    Double namePosterior = ePosterior.get(name);
                    if(namePosterior == null) {
                      continue;
                    }
                    double nameScore = scores.getOrDefault(name, 0.0);
                    nameScore += Math.exp(Math.log(docProb) +Math.log(count / length) + Math.log(namePosterior));
                    assert(nameScore != 0);
                    scores.put(name, nameScore);
                  }
                } break;
                case "lce":
                  // LCE:
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
                  score += Math.log(docProb) + Math.log(count / length) - Math.log(cf / clen);
                  assert(Double.isFinite(score));
                  break;
                case "lce-prior": { // wlce
                  multiScore = true;
                  for (String name : names) {
                    Double namePosterior = ePosterior.get(name);
                    if(namePosterior == null) continue;
                    double nameScore = scores.computeIfAbsent(name, missing -> 0.0);
                    nameScore += Math.exp(Math.log(docProb) + Math.log(count / length) - Math.log(cf / clen) + Math.log(namePosterior));
                    assert (Double.isFinite(nameScore)) : "name: "+name;
                    scores.put(name, nameScore);
                  }
                } break;
                case "and-lce-prior": { //wandlce
                  multiScore = true;
                  for (String name : names) {
                    Double namePosterior = ePosterior.get(name);
                    if(namePosterior == null) continue;
                    double nameScore = scores.computeIfAbsent(name, missing -> 0.0);
                    nameScore += Math.log(docProb) + Math.log(count / length) - Math.log(cf / clen) + Math.log(namePosterior);
                    assert (Double.isFinite(nameScore)) : "name: "+name;
                    scores.put(name, nameScore);
                  }
                } break;
                /*case "lce-priors": {
                  multiScore = true;
                  for (String name : names) {
                    Double namePosterior = ePosterior.get(name);
                    if(namePosterior == null) continue;
                    Double pagerank = staticPriors.get("pagerank").get(name);
                    Double facc09prob = staticPriors.get("facc").get(name);
                    if(pagerank == null || facc09prob == null) continue;

                    double nameScore = scores.computeIfAbsent(name, missing -> 0.0);
                    nameScore += Math.exp(Math.log(docProb) + Math.log(count / length) - Math.log(cf / clen) + Math.log(namePosterior) + facc09prob + pagerank);
                    assert (Double.isFinite(nameScore)) : "name: "+name;
                    scores.put(name, nameScore);
                  }
                } break;*/
                case "wpmi": {
                  multiScore = true;
                  for (String name : names) {
                    Double namePosterior = ePosterior.get(name);
                    if (namePosterior == null) continue;
                    double nameScore = scores.computeIfAbsent(name, missing -> 0.0);
                    nameScore += Math.log(namePosterior) - Math.log(cf / clen); // leaving out constant p(q) which would be in denominator
                    assert (Double.isFinite(nameScore)) : "name: " + name;
                    scores.put(name, nameScore);
                  }
                  assert (Double.isFinite(score));
                } break;
                case "pmi":
                  // reduced-PMI:
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

            if(!multiScore) {
              assert(score != 0);
              for (String name : names) {
                Double namePosterior = ePosterior.get(name);
                if (namePosterior == null) continue;
                topEntities.offer(new ScoredDocument(name, -1, score));
              }
            } else {
              for (Map.Entry<String, Double> kv : scores.entrySet()) {
                double raw = kv.getValue();
                if(raw > 0) {
                  topEntities.offer(new ScoredDocument(kv.getKey(), -1, Math.log(raw)));
                } else {
                  topEntities.offer(new ScoredDocument(kv.getKey(), -1, raw));
                }
              }
            }

            return true;
          });

          List<ScoredDocument> scoredEntities = topEntities.getSorted();
          Ranked.setRanksByScore(scoredEntities);

          for (ScoredDocument entity : scoredEntities) {
            /*if(entity.rank < 4) {
              System.out.println("\t"+entity.documentName+" "+entity.score);
            }*/
            trecrun.println(entity.toTRECformat(qid, "jfoley-entrm"));
          }
        }
      }
    }
  }
}
