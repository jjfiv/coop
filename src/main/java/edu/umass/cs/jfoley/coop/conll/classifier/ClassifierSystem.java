package edu.umass.cs.jfoley.coop.conll.classifier;

import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.io.IO;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.MapCoder;
import edu.umass.cs.jfoley.coop.coders.KryoCoder;
import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import org.lemurproject.galago.utility.Parameters;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
  public static final MapCoder<String,ClassifiedData> coder = new MapCoder<>(CharsetCoders.utf8, new KryoCoder<>(ClassifiedData.class));

  public ClassifierSystem(TermBasedIndexReader index) throws IOException {
    this.index = index;
    featuresAboveThreshold = IterableFns.intoList(index.features.reverseReader.keys());
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
    output.put("positiveCount", cd.positiveExamples.size());
    output.put("negativeCount", cd.negativeExamples.size());
    return output;
  }

  public synchronized void deleteClassifier(String classifier) {
    this.dataByClassifier.remove(classifier);
  }

  public synchronized void addLabels(String classifier, List<String> positive, List<String> negative) throws IOException {
    ClassifiedData cd = dataByClassifier.get(classifier);
    boolean changed = false;
    if(cd == null) {
      cd = new ClassifiedData();
      dataByClassifier.put(classifier, cd);
      changed = true;
    }
    changed |= cd.positiveExamples.addAll(positive);
    changed |= cd.negativeExamples.addAll(negative);

    if(changed) {
      save();
    }
  }

}
