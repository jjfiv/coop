package edu.umass.cs.jfoley.coop.conll.classifier;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.io.IO;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.InterleavedMapCoder;
import edu.umass.cs.jfoley.coop.coders.KryoCoder;
import edu.umass.cs.jfoley.coop.conll.SentenceIndexedToken;
import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import org.lemurproject.galago.utility.Parameters;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jfoley
 */
public class ClassifierSystem {
  public final TermBasedIndexReader index;
  public final List<String> featuresAboveThreshold;
  private final File saveFile;
  Map<String, ClassifiedData> dataByClassifier;
  Map<String, Classifier> cachedTrainedClassifier;
  public static final InterleavedMapCoder<String,ClassifiedData> coder = new InterleavedMapCoder<>(CharsetCoders.utf8, new KryoCoder<>(ClassifiedData.class));

  public ClassifierSystem(TermBasedIndexReader index) throws IOException {
    this.index = index;
    featuresAboveThreshold = IterableFns.intoList(index.features.reverseReader.keys());
    this.cachedTrainedClassifier = new HashMap<>();
    this.saveFile = index.input.child("dataByClassifier");
    if(saveFile.exists()) {
      load();
    } else {
      dataByClassifier = new ConcurrentHashMap<>();
    }
  }

  public synchronized void load() throws IOException {
    try (InputStream is = IO.openInputStream(saveFile)) {
      dataByClassifier = new ConcurrentHashMap<>(coder.readImpl(is));
    }
  }

  public synchronized void save() throws IOException {
    try (OutputStream out = IO.openOutputStream(saveFile)) {
      coder.write(out, dataByClassifier);
    }
  }

  public synchronized Parameters getInfo(String classifier) {
    Parameters output = Parameters.create();
    ClassifiedData cd = dataByClassifier.get(classifier);
    if(cd == null) return output;
    return cd.getInfo();
  }

  public synchronized void deleteClassifier(String classifier) {
    this.dataByClassifier.remove(classifier);
  }

  public synchronized void addLabels(String classifier, List<LabeledToken> labels) throws IOException {
    ClassifiedData cd = dataByClassifier.get(classifier);
    boolean changed = false;
    if(cd == null) {
      cd = new ClassifiedData();
      dataByClassifier.put(classifier, cd);
      changed = true;
    }
    changed |= cd.add(labels);

    if(changed) {
      save();
    }
  }

  public synchronized Classifier train(String classifierName) throws IOException {
    ClassifiedData cd = dataByClassifier.get(classifierName);
    if(cd == null) return null;
    IntList pos = cd.positive();
    IntList neg = cd.negative();

    List<FeatureVector> posF = index.pullFeatures(pos);
    List<FeatureVector> negF = index.pullFeatures(neg);

    Classifier classifier = new PerceptronClassifier(index.numFeatures());
    classifier.train(posF, negF);
    cachedTrainedClassifier.put(classifierName, classifier);
    return classifier;
  }

  public synchronized Classifier getOrTrain(String classifierName) throws IOException {
    return cachedTrainedClassifier.getOrDefault(classifierName, train(classifierName));
  }


  public synchronized List<ClassifiedToken> classifyTokens(String classifierName, List<Integer> tokens) throws IOException {
    Classifier classifier = cachedTrainedClassifier.getOrDefault(classifierName, train(classifierName));
    if(classifier == null) {
      throw new IllegalArgumentException("No such classifier: " + classifierName);
    }

    List<ClassifiedToken> ctokens = new ArrayList<>();
    for (Pair<SentenceIndexedToken, FeatureVector> kv : index.TokenFeatures(tokens)) {
      SentenceIndexedToken tok = kv.getKey();
      FeatureVector fv = kv.getValue();
      boolean pred = classifier.predict(fv);
      double score = classifier.rank(fv);
      ctokens.add(new ClassifiedToken(classifierName, pred, score, tok));
    }

    return ctokens;
  }


}
