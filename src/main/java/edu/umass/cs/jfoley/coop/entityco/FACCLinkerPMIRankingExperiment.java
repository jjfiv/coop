package edu.umass.cs.jfoley.coop.entityco;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.jfoley.coop.PMITerm;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.front.PhrasePositionsIndex;
import edu.umass.cs.jfoley.coop.front.TermPositionsIndex;
import edu.umass.cs.jfoley.coop.front.eval.EvaluateBagOfWordsMethod;
import edu.umass.cs.jfoley.coop.front.eval.FindHitsMethod;
import edu.umass.cs.jfoley.coop.front.eval.NearbyTermFinder;
import edu.umass.cs.jfoley.coop.phrases.PhraseDetector;
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.lists.Ranked;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author jfoley
 */
public class FACCLinkerPMIRankingExperiment {

  public static class EntityDistribution {
    int id;
    int totalCount;
    TObjectIntHashMap<String> entities;

    public EntityDistribution(int id) {
      this.id = id;
      totalCount = 0;
      entities = new TObjectIntHashMap<>();
    }

    public void push(String ent, int count) {
      totalCount += count;
      entities.adjustOrPutValue(ent, count, count);
    }

    public List<String> getEntities() {
      return new ArrayList<>(entities.keySet());
    }

    public double logprob(String ent) {
      return Math.log(entities.get(ent) / (double) totalCount);
    }
  }
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);
    String dataset = "robust04";
    List<EntityJudgedQuery> queries = ConvertEntityJudgmentData.parseQueries(new File(argp.get("queries", "coop/data/" + dataset + ".json")));

    assert(queries.size() > 0);

    String index;
    switch (dataset) {
      case "robust04":
        index = "robust.ints";
        break;
      case "clue12":
        index = "/mnt/scratch/jfoley/clue12a.sdm.ints";
        break;
      default: throw new UnsupportedOperationException("dataset="+dataset);
    }

    IntCoopIndex target = new IntCoopIndex(Directory.Read(argp.get("target", index)));

    List<EntityDistribution> mentionToEntities = new ArrayList<>(4_000_000);

    Debouncer msg = new Debouncer();
    int N = 10;
    PhraseDetector detector = new PhraseDetector(N);
    try (LinesIterable lines = LinesIterable.fromFile("facc_09_mcounts.tsv.gz")) {
      for (String line : lines) {
        String row[] = line.split("\t");
        List<String> mt = Arrays.asList(row[0].split(" "));
        IntList mIds = target.translateFromTerms(mt);
        if(mIds.isEmpty() || mIds.containsInt(-1)) continue;

        int idForMention = mentionToEntities.size();
        EntityDistribution einfo = new EntityDistribution(idForMention);

        for (int i = 1; i < row.length; i++) {
          String ent = StrUtil.takeBefore(row[i], " ");
          int count = Integer.parseInt(StrUtil.takeAfter(row[i], " "));
          einfo.push(ent, count);
        }

        mentionToEntities.add(einfo);
        detector.addPattern(mIds, idForMention);

        if(msg.ready()) {
          System.err.println("Loading phrases: "+msg.estimate(idForMention, 4_000_000));
        }
      }
    }

    System.out.println("Loaded Phrase Detector: " + detector);

    Collections.sort(queries, (lhs, rhs) -> lhs.qid.compareTo(rhs.qid));
    IntCoopIndex dbpedia = new IntCoopIndex(Directory.Read(argp.get("dbpedia", "dbpedia.ints")));
    TermPositionsIndex tpos = target.getPositionsIndex("lemmas");
    PhrasePositionsIndex eIndex = target.getEntitiesIndex();

    int numEntities = argp.get("requested", 5000);
    int minEntityFrequency = argp.get("minEntityFrequency", 2);

    IOMap<Integer, IntList> ambiguous = eIndex.getPhraseHits().getAmbiguousPhrases();
    assert(ambiguous != null);

    int passageSize = argp.get("passageSize", 100);
    try (PrintWriter trecrun = IO.openPrintWriter(argp.get("output", dataset + ".dbpedia.faccpmi.m"+minEntityFrequency+".p"+passageSize+".trecrun"))) {
      for (EntityJudgedQuery query : queries) {
        String qid = query.qid;

        System.out.println(qid + " " + query.text);
        Parameters queryP = Parameters.create();
        queryP.put("query", query.text);
        queryP.put("passageSize", passageSize);
        Parameters infoP = Parameters.create();
        FindHitsMethod hitsFinder= new EvaluateBagOfWordsMethod(queryP, infoP, tpos);
        ArrayList<DocumentResult<Integer>> hits = hitsFinder.computeTimed();
        int queryFrequency = hits.size();

        long startEntites = System.currentTimeMillis();
        NearbyTermFinder termFinder = new NearbyTermFinder(target, argp, infoP, passageSize);

        TIntIntHashMap ecounts = new TIntIntHashMap();
        for (Pair<TermSlice, IntList> docRegion : target.pullTermSlices(termFinder.hitsToSlices(hits))) {
          int[] slice = docRegion.right.asArray();
          detector.match(slice, (phraseId, position, size) -> ecounts.adjustOrPutValue(phraseId, 1, 1));
        }

        long stopEntities = System.currentTimeMillis();
        long millisForScoring = (stopEntities - startEntites);
        System.out.printf("Spent %d milliseconds scoring %d entities for %d locations; %1.2f ms/hit; %d candidates.\n", millisForScoring, ecounts.size(), hits.size(),
            ((double) millisForScoring / (double) hits.size()),
            ecounts.size());

        TopKHeap<PMITerm<Integer>> pmiEntities = new TopKHeap<>(numEntities);
        double collectionLength = target.getCollectionLength();
        ecounts.forEachEntry((eid, frequency) -> {
          if (frequency >= minEntityFrequency) {
            int cf = 1;
            cf = mentionToEntities.get(eid).totalCount;
            pmiEntities.add(new PMITerm<>(eid, cf, queryFrequency, frequency, collectionLength));
          }
          return true;
        });


        List<ScoredDocument> scoredEntities = new ArrayList<>();
        TObjectDoubleHashMap<String> entityScores = new TObjectDoubleHashMap<>();

        for (PMITerm<Integer> pmiEntity: pmiEntities.getSorted()) {
          int eid = pmiEntity.term;

          EntityDistribution dist = mentionToEntities.get(eid);
          List<String> entities = dist.getEntities();
          for (String entity : entities) {
            double scoreProportion = pmiEntity.logPMI() + dist.logprob(entity); // consider FACC popularity
            entityScores.adjustOrPutValue(entity, scoreProportion, scoreProportion);
          }
        }

        entityScores.forEachEntry((ent, score) -> {
          scoredEntities.add(new ScoredDocument(ent, -1, score));
          return true;
        });
        Ranked.setRanksByScore(scoredEntities);

        for (ScoredDocument entity : scoredEntities) {
          if(entity.rank < 4) {
            System.out.println("\t"+entity.documentName+" "+entity.score);
            Integer docId = dbpedia.getNames().getReverse(entity.documentName);
            if(docId == null) {
              continue;
            }
            IntList terms = new IntList(dbpedia.getCorpus().getDocument(docId));
            System.out.println("\t\t"+ StrUtil.join(dbpedia.translateToTerms(terms)));
          }
          trecrun.println(entity.toTRECformat(qid, "jfoley-pmi"));
        }
      }
    }
  }
}
