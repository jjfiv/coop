package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.string.StrUtil;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexOptions;
import org.lemurproject.galago.utility.Parameters;

import java.io.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.apache.lucene.document.Field.Store.NO;
import static org.apache.lucene.document.Field.Store.YES;

/**
 * @author jfoley
 */
public class ConstructCoopIndex {
  static final DateTimeFormatter datePattern = DateTimeFormatter.ofPattern("yyyyMMdd");
  public static LocalDate parseNYTDate(String input) {
    return LocalDate.parse(input, datePattern);
  }

  public static final FieldType CountsFieldType = new FieldType();
  static {
    CountsFieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    CountsFieldType.setTokenized(true);
    CountsFieldType.setStored(false);
  }

  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);

    try (Indexer indexer = new Indexer("test_anno.index", false);
         Indexer sentenceIndexer = new Indexer("test_anno.sentences.index", false)) {
      try (LinesIterable lines = LinesIterable.fromFile(argp.get("input", "/mnt/scratch/jfoley/199309.anno_plus"))) {
        for (String line : lines) {
          if(indexer.getMessage().ready()) {
            System.out.println(indexer.getMessage().estimate(indexer.numProcessed()));
          }
          String docName = StrUtil.takeBefore(line, "\t");

          String dateString = StrUtil.takeBefore(docName, "_");
          String uniqueId = StrUtil.takeAfter(docName, "_");

          LocalDate date = parseNYTDate(dateString);
          long unixTime = date.atStartOfDay().toInstant(ZoneOffset.UTC).getEpochSecond();

          String docJSON = StrUtil.takeAfter(line, "\t");
          Parameters annop = Parameters.parseString(docJSON);

          ArrayList<String> tokens = new ArrayList<>();
          TObjectIntHashMap<String> facets = new TObjectIntHashMap<>();
          StringBuilder facetText = new StringBuilder();
          List<Parameters> sentences = annop.getAsList("sentences", Parameters.class);

          for (int i = 0; i < sentences.size(); i++) {
            Parameters sentence = sentences.get(i);
            List<String> stokens = sentence.getAsList("tokens", String.class);
            tokens.addAll(stokens);


            StringBuilder sfacetText = new StringBuilder();
            TObjectIntHashMap<String> sfacets = new TObjectIntHashMap<>();
            for (Parameters phrase : sentence.getAsList("phrases", Parameters.class)) {
              //List<Long> positions = phrase.getAsList("positions", Long.class);
              //long start = positions.get(0);
              //long end = positions.get(positions.size() - 1);
              String text = phrase.getString("regular");
              sfacets.adjustOrPutValue(text, 1, 1);
              sfacetText.append(text.replace(' ', '_')).append(' ');
            }

            sfacets.forEachEntry((key, count) -> {
              facets.adjustOrPutValue(key, count, count);
              return true;
            });

            sentenceIndexer.pushDocument(
                new StringField("id", uniqueId, YES),
                new LongPoint("time", unixTime),
                new StoredField("time", unixTime),
                new IntPoint("index", i),
                new StoredField("index", i),
                new TextField("body", StrUtil.join(stokens), NO),
                new StoredField("json", sentence.toString()),
                new StoredField("facets", TroveFrequencyCoder.save(sfacets)),
                new Field("facet-phrase", sfacetText.toString(), CountsFieldType)
            );
            facetText.append(sfacetText);
          }

          indexer.pushDocument(
              new StringField("id", uniqueId, YES),
              new LongPoint("time", unixTime),
              new StoredField("time", unixTime),
              new TextField("body", StrUtil.join(tokens), NO),
              new StoredField("json", docJSON),
              new StoredField("facets", TroveFrequencyCoder.save(facets)),
              new Field("facet-phrase", facetText.toString(), CountsFieldType)
          );
        }
      }
    } // close indexer

  }
}
