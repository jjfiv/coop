package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.Comparing;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.random.ReservoirSampler;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.ints.IntsCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.DeltaIntListCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.dociter.movement.AllOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.io.postings.PositionsListCoder;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.sys.PositionsIndexFile;
import edu.umass.cs.ciir.waltz.sys.PostingIndex;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.Arguments;

import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class AndQueryPerformance {
  public static PostingIndex.PostingsConfig<Integer,PositionsIndexFile.PositionsCountMetadata, PositionsList> getCfg(String what) {
    int blockSize;
    IntsCoder docsCoder;

//AND Results: {total=2586000.0, min=1724.0, variance=0.0, max=1724.0, mean=1724.0, count=1500.0, stddev=0.0}
//OR Results: {total=5796000.0, min=3864.0, variance=0.0, max=3864.0, mean=3864.0, count=1500.0, stddev=0.0}
    switch (what) {
//Time to compute AND: {total=27.621801673999997, min=0.01757542, variance=6.4521113543075415E-6, max=0.066615237, mean=0.018414534449333306, count=1500.0, stddev=0.0025401006583022534}
     //Time to compute AND.pull: {total=197.41887378799953, min=0.125967717, variance=4.006355699884853E-5, max=0.228630896, mean=0.13161258252533314, count=1500.0, stddev=0.006329577947924216}
      case "positions":
        blockSize = 128;
        docsCoder = new DeltaIntListCoder();
        break;

      //Time to compute AND: {total=26.811990208999983, min=0.017078665, max=0.083336225, variance=8.685144205738402E-6, mean=0.017874660139333358, count=1500.0, stddev=0.0029470568718194773}
     // Time to compute OR: {total=45.359020480000055, min=0.028875015, variance=1.4112820130411703E-5, max=0.10346278, mean=0.030239346986666676, count=1500.0, stddev=0.0037567033593846216}
      //Time to compute AND.pull: {total=66.83807683900001, min=0.129580105, variance=5.7199459874298295E-5, max=0.215042732, mean=0.13367615367800004, count=500.0, stddev=0.007563032452283826}
      case "upositions":
        blockSize = 128;
        docsCoder = new DeltaIntListCoder(VarUInt.instance, VarUInt.instance);
        break;

     //Time to compute AND: {total=26.96598929000001, min=0.016934723, max=0.215823924, variance=4.119278375674504E-5, mean=0.017977326193333334, count=1500.0, stddev=0.006418160465175753}
     // Time to compute OR: {total=45.02783459400002, min=0.028765423, variance=2.814424523583346E-5, max=0.138144068, mean=0.03001855639599999, count=1500.0, stddev=0.005305115006843251}
     // Time to compute AND.pull: {total=68.92293945799997, min=0.13206452, max=0.272646274, variance=8.875294364984844E-5, mean=0.13784587891600006, count=500.0, stddev=0.009420878072125148}
      case "p256":
        blockSize = 256;
        docsCoder = new DeltaIntListCoder(VarUInt.instance, VarUInt.instance);
        break;
     // Time to compute AND: {total=27.468614638999995, min=0.017179428, variance=2.6837262732128046E-5, max=0.159000902, mean=0.01831240975933336, count=1500.0, stddev=0.005180469354424177}
      //Time to compute OR: {total=45.43383761899991, min=0.028832518, variance=1.4959066513673269E-5, max=0.107375356, mean=0.030289225079333303, count=1500.0, stddev=0.0038676952457081296}
      case "p512":
        blockSize = 512;
        docsCoder = new DeltaIntListCoder(VarUInt.instance, VarUInt.instance);
        break;

      // Time to compute AND: {total=18.22282244800001, min=0.011085718, max=0.180091519, variance=4.288220095037301E-5, mean=0.01214854829866666, count=1500.0, stddev=0.0065484502708941}
      // Time to compute OR: {total=45.65927288399996, min=0.028864116, max=0.296642395, variance=6.11430209632479E-5, mean=0.03043951525600003, count=1500.0, stddev=0.007819400294347892}
      // Time to compute AND.pull: {total=204.4864220810002, min=0.130118374, variance=5.888853208308047E-5, max=0.341883195, mean=0.13632428138733327, count=1500.0, stddev=0.007673886374131459}
      case "p8":
        blockSize = 1;
        docsCoder = new DeltaIntListCoder(VarUInt.instance, VarUInt.instance);
        break;
      default: throw new RuntimeException();
    }

    PostingIndex.PostingsConfig<Integer, PositionsIndexFile.PositionsCountMetadata, PositionsList> config = new PostingIndex.PostingsConfig<>(
        FixedSize.ints,
        new PositionsIndexFile.PositionsCountMetadataCoder(),
        new PositionsListCoder(),
        Comparing.defaultComparator(),
        new PositionsIndexFile.PositionsCountMetadata()
    );

    config.blockSize = blockSize;
    config.docsCoder = docsCoder;

    return config;
  }

  public static void main(String[] args) throws IOException {
    Parameters argp = Arguments.parse(args);
    Directory input = Directory.Read(argp.get("input", "robust.ints"));

    String target = "p256";
    PostingIndex.PostingsConfig<Integer,PositionsIndexFile.PositionsCountMetadata,PositionsList> cfg = getCfg(target);
    IOMap<Integer, PostingMover<PositionsList>> index = cfg.openReader(input, target);
    IdMaps.Reader<String> vocab = GalagoIO.openIdMapsReader(input.childPath("vocab"), FixedSize.ints, CharsetCoders.utf8);

    List<String> phraseTerms = Arrays.asList("to", "be", "or", "not", "to", "be");

    // position -> index:
    IntList termIdMapping = new IntList();
    List<String> uniqueTerms = new ArrayList<>();

    for (int i = 0; i < phraseTerms.size(); i++) {
      String t_i = phraseTerms.get(i);
      int first_pos = phraseTerms.indexOf(t_i);
      termIdMapping.add(first_pos);
      if (first_pos == i) {
        uniqueTerms.add(t_i);
      }
    }

    IntList phraseTermIds = new IntList();
    Map<String, Integer> termMapping = new HashMap<>();
    for (Pair<String, Integer> kv : vocab.reverseReader.getInBulk(uniqueTerms)) {
      termMapping.put(kv.getKey(), kv.getValue());
    }
    for (String phraseTerm : phraseTerms) {
      phraseTermIds.add(termMapping.get(phraseTerm));
    }
    System.out.println(phraseTermIds);
    System.out.println(termIdMapping);

    // and iters:
    List<PostingMover<PositionsList>> iters = new ArrayList<>();
    for (Integer termId : phraseTermIds) {
      PostingMover<PositionsList> iter = index.get(termId);
      int numDocuments = iter != null ? iter.totalKeys() : 0;
      System.out.println("termId: " + termId + " numDocs: " + numDocuments);
      if (iter != null) iters.add(iter);
    }

    AllOfMover<?> andMover = new AllOfMover<>(iters);
    //AnyOfMover<?> andMover = new AnyOfMover<>(iters);
    long startMovement;
    long endMovement;

    // calculate document intersection time:
    ReservoirSampler<Integer> highestNums = new ReservoirSampler<>(1000);
    StreamingStats sizes = new StreamingStats();
    StreamingStats performance = new StreamingStats();
    StreamingStats results = new StreamingStats();
    for (int k = 0; k < 500; k++) {
      highestNums.clear();
      startMovement = System.nanoTime();
      andMover.execute((x) -> {
        highestNums.add(x);
        for (PostingMover<PositionsList> iter : iters) {
          sizes.push((double) iter.getCurrentPosting().size());
        }
      });
      andMover.reset();
      endMovement = System.nanoTime();
      performance.push((endMovement - startMovement) / 1e9);
      results.push(highestNums.total());

    }
    System.out.println("Time to compute AND.pull: " + performance);
    System.out.println("Results: "+results);
    System.out.println("Sizes: "+sizes);

  }
}
