package edu.umass.cs.jfoley.coop.entityco;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.jfoley.coop.PMITerm;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.front.PhrasePositionsIndex;
import edu.umass.cs.jfoley.coop.phrases.PhraseHit;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.lists.Ranked;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author jfoley
 */
public class TopDocsPMIRankingExperiment {
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);

    String dataset = "clue12";
    Map<String, Set<String>> lauraDocsByQuery = new HashMap<>();

    String index;

    switch(dataset) {
      case "robust04": {
        try (LinesIterable lines = LinesIterable.fromFile("rob_document_mentions.data")) {
          List<String> header = Arrays.asList(lines.readLine().split("\t"));
          int docIndex = header.indexOf("rob04_doc");
          int qidIndex = header.indexOf("rob04_qid");
          for (String line : lines) {
            String[] row = line.split("\t");
            String doc = row[docIndex];
            String qid = row[qidIndex];
            MapFns.extendSetInMap(lauraDocsByQuery, qid, doc);
          }
        }
        index = "robust.ints";
      }
      break;
      case "clue12": {
        try (LinesIterable lines = LinesIterable.fromFile("clueweb_sdm_top20docs.data")) {
          for (String line : lines) {
            String[] row = line.split("\t");
            String qid = row[0];
            String doc = row[2];
            MapFns.extendSetInMap(lauraDocsByQuery, qid, doc);
          }
        }
        index = "/mnt/scratch/jfoley/clue12a.sdm.ints";
      }
      break;
      default: throw new UnsupportedOperationException("dataset="+dataset);
    }

    List<EntityJudgedQuery> queries = ConvertEntityJudgmentData.parseQueries(new File(argp.get("queries", "coop/data/" + dataset + ".json")));

    assert(queries.size() > 0);

    Collections.sort(queries, (lhs, rhs) -> lhs.qid.compareTo(rhs.qid));
    IntCoopIndex dbpedia = new IntCoopIndex(Directory.Read(argp.get("dbpedia", "dbpedia.ints")));
    IntCoopIndex target = new IntCoopIndex(Directory.Read(argp.get("target", index)));
    PhrasePositionsIndex eIndex = target.getEntitiesIndex();
    IOMap<Integer, IntList> ambiguous = eIndex.getPhraseHits().getAmbiguousPhrases();
    assert(ambiguous != null);

    int numEntities = argp.get("requested", 5000);
    int minEntityFrequency = argp.get("minEntityFrequency", 2);

    long start, end;
    try (PrintWriter trecrun = IO.openPrintWriter(argp.get("output", dataset + ".top20.logpmi.m" + minEntityFrequency + ".trecrun"))) {
      for (EntityJudgedQuery query : queries) {
        String qid = query.qid;

        List<String> topDocs = new ArrayList<>(lauraDocsByQuery.get(qid));
        System.out.println(qid + " " + query.text+" topDocs: "+topDocs.size());

        IntList topDocIds = new IntList();
        for (Pair<String, Integer> docIdPair : target.getNames().getReverse(topDocs)) {
          topDocIds.add(docIdPair.getValue());
        }

        start = System.currentTimeMillis();
        List<Pair<Integer, PhraseHitList>> inBulk = eIndex.getPhraseHits().getDocumentHits().getInBulk(topDocIds);
        end = System.currentTimeMillis();

        System.out.println("Data pull: "+(end-start)+"ms. for "+topDocIds.size()+" documents.");
        TIntIntHashMap ecounts = new TIntIntHashMap();

        for (Pair<Integer, PhraseHitList> pair : inBulk) {
          int doc = pair.getKey();
          PhraseHitList dochits = pair.getValue();

          for (PhraseHit dochit : dochits) {
            ecounts.adjustOrPutValue(dochit.id(), 1, 1);
          }
        }

        start = System.currentTimeMillis();
        TIntIntHashMap freq = eIndex.getCollectionFrequencies(new IntList(ecounts.keys()));
        end = System.currentTimeMillis();
        System.out.println("Pull efrequencies: " + (end - start) + "ms.");

        TopKHeap<PMITerm<Integer>> pmiEntities = new TopKHeap<>(numEntities);
        double collectionLength = target.getCollectionLength();
        ecounts.forEachEntry((eid, frequency) -> {
          if (frequency >= minEntityFrequency) {
            int cf = 1;
            cf = freq.get(eid);
            if (cf == freq.getNoEntryValue()) {
              cf = 1;
            }

            pmiEntities.add(new PMITerm<>(eid, cf, topDocIds.size(), frequency, collectionLength));
          }
          return true;
        });


        List<ScoredDocument> scoredEntities = new ArrayList<>();

        TObjectDoubleHashMap<String> entityScores = new TObjectDoubleHashMap<>();

        for (PMITerm<Integer> pmiEntity: pmiEntities.getSorted()) {
          int eid = pmiEntity.term;
          IntList eterms = eIndex.getPhraseVocab().getForward(eid);
          Parameters ep = pmiEntity.toJSON();
          List<String> sterms = target.translateToTerms(eterms);
          ep.put("term", StrUtil.join(sterms));
          ep.put("eId", eid);

          IntList ids = ambiguous.get(eid);
          if(ids == null) {
            ids = new IntList();
            ids.push(eid);
            ep.put("ids", ids);
          }

          double count = ids.size();
          double scoreProportion = pmiEntity.logPMI() / count; // consider FACC popularity

          for (String dbpediaName: dbpedia.getNames().getForwardMap(ids).values()) {
            entityScores.adjustOrPutValue(dbpediaName, scoreProportion, scoreProportion);
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
          trecrun.println(entity.toTRECformat(qid, "jfoley-topdocs-pmi"));
        }
      }
    }
  }
}
