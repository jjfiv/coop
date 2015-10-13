package edu.umass.cs.jfoley.coop.entityco;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.kinds.compress.LZFCoder;
import edu.umass.cs.ciir.waltz.coders.map.impl.WaltzDiskMapReader;
import edu.umass.cs.jfoley.coop.PMITerm;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.utility.Parameters;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class WikiSDMTopDocsToCategorySet {
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);

    Directory dbpediaDir = Directory.Read("dbpedia.ints");

    String dataset = "robust04";
    List<EntityJudgedQuery> queries = ConvertEntityJudgmentData.parseQueries(new File(argp.get("queries", "coop/data/" + dataset + ".json")));
    final int depth = argp.get("depth", 100);
    final int minCount = argp.get("minCount", 2);

    // for mention->entity probs:
    LocalRetrieval jeffWiki = PMIRankingExperiment.openJeffWiki(argp);

    Collections.sort(queries, (lhs, rhs) -> lhs.qid.compareTo(rhs.qid));
    WaltzDiskMapReader<String, String> catReader = new WaltzDiskMapReader<>(dbpediaDir, "category_by_article", CharsetCoders.utf8, new LZFCoder<>(CharsetCoders.utf8));
    WaltzDiskMapReader<String, Integer> catCountsReader = new WaltzDiskMapReader<>(dbpediaDir, "category_counts", CharsetCoders.utf8, FixedSize.ints);

    double total = Objects.requireNonNull(catCountsReader.get("__TOTAL__"));
    for (EntityJudgedQuery query : queries) {

      Parameters qp = Parameters.create();
      qp.put("requested", depth);
      Node gq = new Node("sdm");
      TagTokenizer tok = new TagTokenizer();
      for (String term : tok.tokenize(query.text).terms) {
        gq.addChild(Node.Text(term));
      }

      Set<String> pages = jeffWiki.transformAndExecuteQuery(gq, qp).resultSet();

      TObjectIntHashMap<String> catFreqs = new TObjectIntHashMap<>();
      for (Pair<String, String> kv : catReader.getInBulk(new ArrayList<>(pages))) {
        for (String cat : Arrays.asList(kv.getValue().split("\t"))) {
          catFreqs.adjustOrPutValue(cat, 1, 1);
        }
      }

      TopKHeap<PMITerm<String>> topCategories = new TopKHeap<>(5);
      //List<PMITerm<String>> topCategories = new ArrayList<>();
      catFreqs.forEachEntry((cat, freq) -> {
        try {
          if(freq > minCount) {
            topCategories.add(new PMITerm<>(cat, Objects.requireNonNull(catCountsReader.get(cat)), depth, freq, total));
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        return true;
      });

      //Collections.sort(topCategories);
      System.out.println(query.qid + " " + query.text);
      for (PMITerm<String> pmiTerm : topCategories.getSorted()) {
        System.out.println("\t"+pmiTerm.term+"\t"+pmiTerm.logPMI()+"\t"+pmiTerm.queryProxFrequency);
      }

    }
  }
}
