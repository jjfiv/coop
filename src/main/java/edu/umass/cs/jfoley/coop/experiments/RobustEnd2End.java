package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.entityco.PMIRankingExperiment;
import edu.umass.cs.jfoley.coop.phrases.PhraseDetector;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jfoley
 */
public class RobustEnd2End {
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);

    Map<String, String> queryByQid = new HashMap<>();
    for (String qline : LinesIterable.fromFile("rob04.titles.tsv").slurp()) {
      String qcol[] = qline.split("\t");
      String qid = qcol[0];
      String query = qcol[1];
      queryByQid.put(qid, query);
    }

    LocalRetrieval jeffWiki = PMIRankingExperiment.openJeffWiki(argp);

    String index = "/mnt/scratch3/jfoley/robust.ints";
    IntCoopIndex dbpedia = new IntCoopIndex(Directory.Read(argp.get("dbpedia", "/mnt/scratch3/jfoley/dbpedia.ints")));
    IntCoopIndex target = new IntCoopIndex(Directory.Read(argp.get("target", index)));
    IOMap<Integer, IntList> ambiguousEntities = Objects.requireNonNull(target.getEntities().getAmbiguousPhrases());

    PhraseDetector tagger = dbpedia.loadPhraseDetector(10, target);
    TagTokenizer tok = new TagTokenizer();

    for (Map.Entry<String, String> kv : queryByQid.entrySet()) {
      String qid = kv.getKey();
      String qtext = kv.getValue();

      Node sdmQuery = new Node("sdm");
      IntList qids = new IntList();
      for (String term : tok.tokenize(qtext).terms) {
        qids.add(target.getTermId(term));
        sdmQuery.add(Node.Text(term));
      }

      System.out.println(qid+": "+qtext+" "+qids);

      IntList entityIds = new IntList();
      tagger.match(qids.asArray(), (phraseId, position, size) -> entityIds.add(phraseId));

      System.out.println("\t"+entityIds);
      for (int entityId : entityIds) {
        IntList ids = ambiguousEntities.get(entityId);
        if(ids == null) {
          ids = new IntList();
          ids.add(entityId);
        }

        List<String> entities = new ArrayList<>();
        for (int id : ids) {
          String entity = dbpedia.getNames().getForward(id);
          entities.add(entity);
        }

        Parameters ewqp = Parameters.create();
        ewqp.put("working", entities);
        ewqp.put("warnMissingDocuments", false);
        Map<String, Double> scoredEntities = jeffWiki.transformAndExecuteQuery(sdmQuery, ewqp).asDocumentFeatures();

        List<TopKHeap.Weighted<String>> weightedEntities = new ArrayList<>();
        for (Map.Entry<String, Double> eScore : scoredEntities.entrySet()) {
          weightedEntities.add(new TopKHeap.Weighted<>(eScore.getValue(), eScore.getKey()));
        }
        Collections.sort(weightedEntities, Comparator.reverseOrder());

        System.out.println("\t"+StrUtil.join(weightedEntities.stream().map(Object::toString).collect(Collectors.toList()), " "));
      }
      System.out.println();
    }
  }
}
