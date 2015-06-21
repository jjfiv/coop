package edu.umass.cs.jfoley.coop.conll.server;

import ciir.jfoley.chai.collections.ArrayListMap;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.Comparing;
import edu.umass.cs.ciir.waltz.dociter.movement.AnyOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.IdSetMover;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;
import edu.umass.cs.jfoley.coop.conll.SentenceIndexedToken;
import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import edu.umass.cs.jfoley.coop.conll.classifier.ClassifiedToken;
import edu.umass.cs.jfoley.coop.conll.classifier.Classifier;
import edu.umass.cs.jfoley.coop.conll.classifier.SparseBooleanFeatures;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jfoley.
 */
public class RankByClassifierFn extends IndexServerFn {
  public RankByClassifierFn(TermBasedIndexReader index) {
    super(index);
  }

  @Override
  public Parameters handleRequest(Parameters input) throws IOException {
    String classifierName = input.getString("classifier");
    Classifier classifier = index.classifiers.getOrTrain(classifierName);
    if(classifier == null) throw new IllegalArgumentException("No such classifier: "+classifierName);

    IntList features = new IntList();
    for (int fid : classifier.getSparseFeatures().keys()) {
      features.add(fid);
    }
    Map<Integer, Mover> featurePostings = new ArrayListMap<>();
    for (Pair<Integer, String> kv : index.features.forwardReader.getInBulk(features)) {
      featurePostings.put(kv.getKey(), new IdSetMover(index.featureIndex.get(kv.getValue())));
    }

    List<Mover> actualFeatures = new ArrayList<>(featurePostings.values());
    AnyOfMover<Mover> orMover = new AnyOfMover<>(actualFeatures);

    TopKHeap<ClassifiedToken> heap = new TopKHeap<>(1000, Comparing.defaultComparator());
    for( ;!orMover.isDone(); orMover.next()) {
      int id = orMover.currentKey();
      IntList active = new IntList(featurePostings.size());
      for (Map.Entry<Integer, Mover> kv : featurePostings.entrySet()) {
        if(kv.getValue().matches(id)) {
          active.add(kv.getKey());
        }
      }
      SparseBooleanFeatures fv = new SparseBooleanFeatures(active);
      boolean pred = classifier.predict(fv);
      double score = classifier.rank(fv);
      ClassifiedToken ctoken = new ClassifiedToken(classifierName, pred, score, null);
      heap.offer(ctoken);
    }

    Map<Integer, ClassifiedToken> tokensById = new HashMap<>(1000);
    IntList resultIds = new IntList(1000);
    for (ClassifiedToken ctoken : heap.getUnsortedList()) {
      resultIds.add(ctoken.getTokenId());
      tokensById.put(ctoken.getTokenId(), ctoken);
    }

    for (Pair<Integer, SentenceIndexedToken> kv : index.tokenCorpus.getInBulk(resultIds)) {
      tokensById.get(kv.getKey()).token = kv.getValue();
    }

    List<Parameters> output = new ArrayList<>(1000);
    for (ClassifiedToken classifiedToken : heap.getSorted()) {
      Parameters ctoken = Parameters.create();
      ctoken.put("token", classifiedToken.token.toJSON());
      ctoken.put("positive", classifiedToken.positive);
      ctoken.put("score", classifiedToken.score);
      output.add(ctoken);
    }

    return Parameters.parseArray("results", output);
  }
}
