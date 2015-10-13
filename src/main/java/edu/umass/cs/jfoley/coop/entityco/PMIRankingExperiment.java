package edu.umass.cs.jfoley.coop.entityco;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.jfoley.coop.PMITerm;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.front.PhrasePositionsIndex;
import edu.umass.cs.jfoley.coop.front.TermPositionsIndex;
import edu.umass.cs.jfoley.coop.front.eval.EvaluateBagOfWordsMethod;
import edu.umass.cs.jfoley.coop.front.eval.FindHitsMethod;
import edu.umass.cs.jfoley.coop.front.eval.NearbyTermFinder;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitList;
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.core.retrieval.Results;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
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
public class PMIRankingExperiment {

  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);
    String dataset = "robust04";
    List<EntityJudgedQuery> queries = ConvertEntityJudgmentData.parseQueries(new File(argp.get("queries", "coop/data/" + dataset + ".json")));

    Parameters jeffWikiP = Parameters.create();
    jeffWikiP.put("mu", 96400.0);
    jeffWikiP.put("uniw", 0.29);
    jeffWikiP.put("odw", 0.21);
    jeffWikiP.put("uww", 0.5);
    LocalRetrieval jeffWiki = new LocalRetrieval("/mnt/scratch/jfoley/jeff-wiki.galago/full-wiki-stanf3_context_g351");

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

    Collections.sort(queries, (lhs, rhs) -> lhs.qid.compareTo(rhs.qid));
    IntCoopIndex dbpedia = new IntCoopIndex(Directory.Read(argp.get("dbpedia", "dbpedia.ints")));
    IntCoopIndex target = new IntCoopIndex(Directory.Read(argp.get("target", index)));
    TermPositionsIndex tpos = target.getPositionsIndex("lemmas");
    PhrasePositionsIndex eIndex = target.getEntitiesIndex();

    int numEntities = argp.get("requested", 200);
    int minEntityFrequency = argp.get("minEntityFrequency", 2);

    IOMap<Integer, IntList> ambiguous = eIndex.getPhraseHits().getAmbiguousPhrases();
    assert(ambiguous != null);

    int passageSize = argp.get("passageSize", 250);
    long start, end;
    try (PrintWriter trecrun = IO.openPrintWriter(argp.get("output", dataset + ".dbpedia.wiki-pmi.m"+minEntityFrequency+".p"+passageSize+".trecrun"))) {
      for (EntityJudgedQuery query : queries) {
        String qid = query.qid;
        if(Objects.equals(qid, "302")) {
          query.text = "poliomyelitis";
        } else if(Objects.equals(qid, "316")) {
          query.text = "polygamy";
        } else if(Objects.equals(qid, "326")) {
          query.text = "ferry sinking";
        }

        System.out.println(qid + " " + query.text);
        Parameters queryP = Parameters.create();
        queryP.put("query", query.text);
        queryP.put("passageSize", passageSize);
        Parameters infoP = Parameters.create();
        FindHitsMethod hitsFinder = new EvaluateBagOfWordsMethod(queryP, infoP, tpos);
        ArrayList<DocumentResult<Integer>> hits = hitsFinder.computeTimed();
        int queryFrequency = hits.size();

        long startEntites = System.currentTimeMillis();
        NearbyTermFinder termFinder = new NearbyTermFinder(target, argp, infoP, passageSize);
        IOMap<Integer, PhraseHitList> documentHits = eIndex.getPhraseHits().getDocumentHits();

        HashMap<Integer, List<TermSlice>> slicesByDocument = termFinder.slicesByDocument(termFinder.hitsToSlices(hits));
        TIntIntHashMap ecounts = new TIntIntHashMap();

        start = System.currentTimeMillis();
        List<Pair<Integer, PhraseHitList>> inBulk = documentHits.getInBulk(new IntList(slicesByDocument.keySet()));
        end = System.currentTimeMillis();

        System.out.println("Data pull: " + (end - start) + "ms. for " + slicesByDocument.size() + " documents.");
        StreamingStats intersectTimes = new StreamingStats();

        for (Pair<Integer, PhraseHitList> pair : inBulk) {
          int doc = pair.getKey();
          PhraseHitList dochits = pair.getValue();

          List<TermSlice> localSlices = slicesByDocument.get(doc);
          for (TermSlice slice : localSlices) {
            start = System.nanoTime();
            IntList eids = dochits.find(slice.start, slice.size());
            end = System.nanoTime();
            intersectTimes.push((end - start) / 1e6);
            for (int eid : eids) {
              ecounts.adjustOrPutValue(eid, 1, 1);
            }
          }
        }

        System.out.println("# PhraseHitList.find time stats: " + intersectTimes);

        long stopEntities = System.currentTimeMillis();
        long millisForScoring = (stopEntities - startEntites);
        System.out.printf("Spent %d milliseconds scoring %d entities for %d locations; %1.2f ms/hit; %d candidates.\n", millisForScoring, ecounts.size(), hits.size(),
            ((double) millisForScoring / (double) hits.size()),
            ecounts.size());

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

            pmiEntities.add(new PMITerm<>(eid, cf, queryFrequency, frequency, collectionLength));
          }
          return true;
        });


        List<ScoredDocument> scoredEntities = new ArrayList<>();

        TObjectDoubleHashMap<String> entityScores = new TObjectDoubleHashMap<>();

        HashSet<String> entities = new HashSet<>();

        int items = 0;
        for (PMITerm<Integer> pmiEntity : pmiEntities.getSorted()) {
          int eid = pmiEntity.term;
          IntList eterms = eIndex.getPhraseVocab().getForward(eid);
          List<String> sterms = target.translateToTerms(eterms);

          IntList ids = ambiguous.get(eid);
          if (ids == null) {
            ids = new IntList();
            ids.push(eid);
          }

          List<String> names = new ArrayList<>(dbpedia.getNames().getForwardMap(ids).values());
          if (items++ < 10) {
            System.out.println(sterms + "\t" + pmiEntity.logPMI() + "\t" + names + "\t" + ids);
          }
          entities.addAll(names);
        }

        Parameters qp = Parameters.create();
        qp.put("working", new ArrayList<>(entities));
        qp.put("warnMissingDocuments", false);
        Node gq = new Node("sdm");
        TagTokenizer tok = new TagTokenizer();
        for (String term : tok.tokenize(query.text).terms) {
          gq.addChild(Node.Text(term));
        }
        System.err.println("Fetch WikiSDM prob for "+entities.size()+" entities: "+gq);
        Results results = jeffWiki.transformAndExecuteQuery(gq, qp);
        Map<String, Double> entityProbs = results.asDocumentFeatures();

        for (PMITerm<Integer> pmiEntity : pmiEntities.getSorted()) {
          int eid = pmiEntity.term;
          IntList ids = ambiguous.get(eid);
          if (ids == null) {
            ids = new IntList();
            ids.push(eid);
          }

          List<String> names = new ArrayList<>(dbpedia.getNames().getForwardMap(ids).values());


          double scoreProportion = pmiEntity.logPMI();
          for (String dbpediaName: names) {
            Double escore = entityProbs.get(dbpediaName);
            if(escore == null) continue;
            double score = scoreProportion + escore;
            entityScores.adjustOrPutValue(dbpediaName, score, score);
            //scoredEntities.add(new ScoredDocument(dbpediaName, -1, pmiEntity.pmi()));
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
