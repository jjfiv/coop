package edu.umass.cs.jfoley.coop.experiments.synthesis;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.postings.extents.Span;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.phrases.PhraseHit;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitList;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitsReader;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author jfoley
 */
public class FindExampleDocument {
  public static void main(String[] args) throws IOException {
    IntCoopIndex intIndex = new IntCoopIndex(Directory.Read("robust.ints"));
    IntCoopIndex dbpedia = new IntCoopIndex(Directory.Read("dbpedia.ints"));
    LocalRetrieval ret = new LocalRetrieval("/mnt/scratch/jfoley/robust04.galago");

    String id = "LA071790-0136";

    String rawText = ret.getDocument(id, Document.DocumentComponents.JustText).text;

    TagTokenizer tok = new TagTokenizer();
    Document doc = tok.tokenize(rawText);

    int docId = Objects.requireNonNull(intIndex.getNames().getReverse(id));

    PhraseHitsReader entities = intIndex.getEntities();
    PhraseHitList hits = entities.getDocumentHits().get(docId);
    assert hits != null;
    IOMap<Integer, IntList> ambigMap = Objects.requireNonNull(entities.getAmbiguousPhrases());

    int spanStart = 7;
      Span query = new Span(spanStart, spanStart+7);
      List<PhraseHit> toRender = new ArrayList<>();
      for (PhraseHit hit : hits) {
        if(hit.getSpan().overlaps(query)) {
          toRender.add(hit);
          /*for (int eid : list) {
            toRender.add(new PhraseHit(hit.start(), hit.size(), eid));
          }*/
        }
      }

      List<List<PhraseHit>> linearized = new ArrayList<>();
      linearized.add(new ArrayList<>()); // add a single bucket
      for (PhraseHit candidate : toRender) {
        boolean difFindBucket = false;
        for (List<PhraseHit> bucket : linearized) {

          // can fit?
          boolean canFitInBucket = true;
          for (PhraseHit inBucket : bucket) {
            if(inBucket.getSpan().overlaps(candidate.getSpan())) {
              canFitInBucket = false;
              break;
            }
          }
          if(canFitInBucket) {
            difFindBucket = true;
            bucket.add(candidate);
            break;
          }
        }

        if(!difFindBucket) {
          List<PhraseHit> newBucket = new ArrayList<>();
          newBucket.add(candidate);
          linearized.add(newBucket);
        }
      }

    StringBuilder cols = new StringBuilder();
    StringBuilder output = new StringBuilder();
    for (int i = query.begin; i < query.end; i++) {
      int start = doc.termCharBegin.get(i);
      int end = doc.termCharEnd.get(i);
      cols.append('l');
      output.append(doc.text.substring(start, end));
      if(i +1 == query.end) {
        output.append(" \\\\");
      } else {
        output.append(" &");
      }

    }
    System.out.println(doc.text.substring(doc.termCharBegin.get(query.begin), doc.termCharEnd.get(query.end)));

    System.err.println(StrUtil.join(intIndex.translateToTerms(intIndex.getCorpus().getSlice(docId, query.begin, query.end - query.begin))));

    System.err.println("\\begin{tabular}{"+cols.toString()+"}");
    System.err.println(output.toString());
    for (List<PhraseHit> bucket : linearized) {
      StringBuilder ann = new StringBuilder();
      int col = query.begin;

      Collections.sort(bucket, (lhs, rhs) -> Integer.compare(lhs.start(), rhs.start()));

      for (PhraseHit mark : bucket) {
        int start = mark.start();
        int end = mark.end();
        String annotation = StrUtil.takeBefore(dbpedia.getNames().getForward(mark.id()), "_(");
        int width = end - start;

        int count = 1;
        IntList list = ambigMap.get(mark.id());
        if(list != null) {
          count = list.size();
        }

        while(col < start) {
          ann.append(" & ");
          col++;
        }
        if(width != 1) {
          ann.append("\\multicolumn{").append(width).append("}").append("{c}{");
        }
        //ann.append("\\verb|").append(annotation).append("|");
        ann.append(count);
        if(width != 1) {
          ann.append("}");
          col+=width;
        } else {
          col++;
        }
        if(col >= query.end) {
          ann.append("\\\\");
        } else {
          ann.append(" & ");
        }
      }
      while(col < query.end) {
        col++;
        if(col >= query.end) {
          ann.append("\\\\");
        } else {
          ann.append(" & ");
        }
      }

      System.err.println(ann.toString());
    }
    System.err.println("\\end{tabular}");

    System.err.println(toRender);
  }

  //System.err.println(rawText);
}
