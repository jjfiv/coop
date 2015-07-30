package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.ShardedTextWriter;
import ciir.jfoley.chai.io.archive.ZipArchive;
import ciir.jfoley.chai.io.archive.ZipArchiveEntry;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.jfoley.coop.coders.KryoCoder;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.CoopToken;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author jfoley
 */
public class TagLearning {

  Set<String> allObjects = new HashSet<>();
  Set<String> allProps = new HashSet<>();
  Set<String> allTags = new HashSet<>();
  Set<String> allFeatures = new TreeSet<>();
  Map<Pair<String,String>, Integer> classFreqs = new HashMap<>();


  public static void main(String[] args) throws IOException {
    TagLearning dataCollector = new TagLearning();

    Directory output = new Directory("offline-learning");
    List<File> inputZips = new ArrayList<>();
    List<File> candidates = Directory.Read(".").children();
    for (File candidate : candidates) {
      if(candidate.getName().startsWith("clue_schemax")) {
        inputZips.add(candidate);
      }
    }
    KryoCoder<CoopDoc> coder = new KryoCoder<>(CoopDoc.class);

    Debouncer msg = new Debouncer(1000);

    long startTime = System.currentTimeMillis();
    for (File inputZip : inputZips) {
      try (ZipArchive zip = ZipArchive.open(inputZip)) {
        List<ZipArchiveEntry> listEntries = zip.listEntries();
        for (int i = 0; i < listEntries.size(); i++) {
          ZipArchiveEntry entry = listEntries.get(i);
          CoopDoc doc = coder.read(entry.getInputStream());
          if(msg.ready()) {
            System.err.println(i + "/" + listEntries.size() + " " + doc.getName());
            System.err.println("# "+msg.estimate(i, listEntries.size()));
          }
          dataCollector.handleDocument(doc);
        }
      }
    }
    long endParsingTime = System.currentTimeMillis();
    System.out.println("Total parsing time: "+(endParsingTime - startTime)+"ms.");

    System.out.println("NumClasses: "+dataCollector.allTags.size());
    System.out.println("NumObjClasses: "+dataCollector.allObjects.size());
    System.out.println("NumPropClasses: "+dataCollector.allProps.size());
    System.out.println("NumFeatures: "+dataCollector.allFeatures.size());
    System.out.println("Classes:"+ StrUtil.join(new ArrayList<>(dataCollector.allTags), "\n"));
    System.out.println("ClassFreqs:"+dataCollector.classFreqs);
    System.out.println("null.near:"+dataCollector.classFreqs.get(Pair.of("null", "near")));
    System.out.println("null.far:"+dataCollector.classFreqs.get(Pair.of("null", "far")));

    ArrayList<String> ids = new ArrayList<>();
    try (PrintWriter pw = IO.openPrintWriter(output.childPath("features"))) {
      for (String feature : dataCollector.allFeatures) {
        ids.add(feature);
        pw.println(feature);
      }
    }


    Map<Pair<String,String>, ShardedTextWriter> featureWriters = new HashMap<>();
    Map<Pair<String,String>,Integer> classIds = new HashMap<>();
    try (PrintWriter pw = IO.openPrintWriter(output.childPath("classes"))) {
      int idx = 1;
      for (Pair<String,String> classes : dataCollector.classFreqs.keySet()) {
        // skip low-freq classes:
        if(dataCollector.classFreqs.get(classes) < 1000) continue;

        int curIdx = idx++;
        classIds.put(classes, curIdx);
        pw.printf("%s\t%s\t%d\n", classes.left, classes.right, curIdx);
        Directory dir = output.childDir(classes.left);
        featureWriters.put(classes, new ShardedTextWriter(dir, classes.right, "svm",
        20000));
      }
    }

    // second pass, actually save features:
    msg = new Debouncer(1000);
    long startTime2 = System.currentTimeMillis();
    for (File inputZip : inputZips) {
      try (ZipArchive zip = ZipArchive.open(inputZip)) {
        List<ZipArchiveEntry> listEntries = zip.listEntries();
        for (int i = 0; i < listEntries.size(); i++) {
          ZipArchiveEntry entry = listEntries.get(i);
          CoopDoc doc = coder.read(entry.getInputStream());
          if(msg.ready()) {
            System.err.println(i + "/" + listEntries.size() + " " + doc.getName());
            System.err.println("# "+msg.estimate(i, listEntries.size()));
          }
          for (List<CoopToken> coopTokens : doc.getSentences()) {
            boolean sentencePositive = false;
            for (CoopToken coopToken : coopTokens) {
              if (!coopToken.getTags().isEmpty()) {
                sentencePositive = true;
              }
            }

            for (CoopToken coopToken : coopTokens) {
              StringBuilder svmF = new StringBuilder();

              Map<Pair<String,String>, Integer> classNums = new HashMap<>();
              List<Pair<String,String>> classes = getClassNames(coopToken);
              for (Pair<String, String> className : classes) {
                Integer classId = classIds.get(className);
                if(classId == null) continue;
                classNums.put(className, classId);
              }
              if(classNums.isEmpty()) {
                Pair<String,String> name = Pair.of("null", sentencePositive ? "near" : "far");
                classNums.put(name, 0);
              }

              Set<Integer> fset = new HashSet<>();
              for (String fname : coopToken.getIndicators()) {
                int id = Collections.binarySearch(ids, fname.toLowerCase());
                if(id < 0) continue;
                fset.add(id);
              }

              IntList features = new IntList(fset);
              features.sort();
              for (int id : features) {
                svmF.append(' ').append(id).append(":").append(1);
              }

              for (Map.Entry<Pair<String, String>, Integer> kv : classNums.entrySet()) {
                Integer classId = kv.getValue();
                if(classId == null) continue;
                featureWriters.get(kv.getKey()).println(classId+svmF.toString());
              }
            }
          }
        }
      }
    }

    for (ShardedTextWriter shardedTextWriter : featureWriters.values()) {
      shardedTextWriter.close();
    }
    long endParsingTime2 = System.currentTimeMillis();
    System.out.println("Total parsing time2: "+(endParsingTime2 - startTime2)+"ms.");
  }

  public static List<Pair<String,String>> getClassNames(CoopToken tok) {
    List<Pair<String,String>> output = new ArrayList<>();

    for (String tag : tok.getTags()) {
      String usefulTag = StrUtil.takeAfter(StrUtil.takeAfter(tag, "<m>:").toLowerCase(), "/");
      if(usefulTag.contains("/")) {
        System.err.println(usefulTag);
        continue;
      }

      String obj = usefulTag;
      String prop = null;
      if(usefulTag.contains(":")) {
        obj = StrUtil.takeBefore(usefulTag, ":");
        prop = StrUtil.takeAfter(usefulTag, ":");
      }

      output.add(Pair.of("obj", obj));
      if(prop != null) {
        output.add(Pair.of("prop", prop));
      } else {
        output.add(Pair.of("label", usefulTag));
      }
    }

    return output;
  }

  private void handleDocument(CoopDoc doc) {
    for (List<CoopToken> coopTokens : doc.getSentences()) {
      handleSentence(coopTokens);
    }
  }

  private void handleSentence(List<CoopToken> coopTokens) {
    HashSet<String> sentenceFeatures = new HashSet<>();

    List<Set<String>> nullFeatures = new ArrayList<>();
    boolean keep = false;
    for (CoopToken coopToken : coopTokens) {
      for (String feature : coopToken.getIndicators()) {
        sentenceFeatures.add(feature.toLowerCase());
      }

      List<Pair<String, String>> classNames = getClassNames(coopToken);
      if(!classNames.isEmpty()) {
        for (Pair<String, String> kv : classNames) {
          pushFeatures(kv.left, kv.right, coopToken.getIndicators());
          switch (kv.left) {
            case "prop": allProps.add(kv.right); break;
            case "obj": allObjects.add(kv.right); break;
            case "label": allTags.add(kv.right); break;
          }
          keep = true;
        }
      } else {
        nullFeatures.add(coopToken.getIndicators());
      }
    }

    allFeatures.addAll(sentenceFeatures);
    if(keep) {
      for (Set<String> nullFeature : nullFeatures) {
        pushFeatures("null", "near", nullFeature);
      }
    } else {
      for (Set<String> nullFeature : nullFeatures) {
        pushFeatures("null", "far", nullFeature);
      }
    }
  }

  private void pushFeatures(String kind, String name, Set<String> features) {
    MapFns.addOrIncrement(classFreqs, Pair.of(kind, name), 1);
  }
}
