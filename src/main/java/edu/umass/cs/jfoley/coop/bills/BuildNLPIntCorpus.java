package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.collections.IntRange;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.errors.FatalError;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.TemporaryDirectory;
import ciir.jfoley.chai.io.inputs.InputContainer;
import ciir.jfoley.chai.io.inputs.InputFinder;
import ciir.jfoley.chai.io.inputs.InputStreamable;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.time.Debouncer;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.Arguments;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jfoley
 */
public class BuildNLPIntCorpus {

  public static void main(String[] args) throws IOException {
    Parameters argp = Arguments.parse(args);

    Directory output = new Directory(argp.get("output", "bills.ints"));

    InputFinder inputFinder = InputFinder.Default();
    List<? extends InputContainer> inputs = inputFinder.findAllInputs(argp.get("input", "/mnt/scratch/jfoley/bills-data/billsanno.zip"));

    Debouncer msg = new Debouncer(1000);

    int N = 99776; // hard-coded; number of final bill versions in our 2014 corpus; TODO, use metadata class to figure this out.
    TreeMap<Integer, InputStreamable> files = new TreeMap<>();
    for (InputContainer ic : inputs) {
      for (InputStreamable in : ic.getInputs()) {
        String name = StrUtil.removeBack((new File(in.getName())).getName(), ".anno");
        assert(name.startsWith("bill"));
        int billIndex = Integer.parseInt(name.substring(4));
        if(billIndex < N) {
          files.put(billIndex, in);
        }
      }
    }

    //ForkJoinPool jobs = ForkJoinPool.commonPool();
    ForkJoinPool jobs = new ForkJoinPool(5);
    System.out.println("Job Pool: "+jobs.getPoolSize());

    List<TemporaryDirectory> shards = new ArrayList<>();
    List<ForkJoinTask<?>> tasks = new ArrayList<>();
    AtomicInteger totalComplete = new AtomicInteger(0);

    for (List<Integer> jobIds : ListFns.partition(IntRange.exclusive(0, N), 50)) {
      final TemporaryDirectory jobTmpDir = new TemporaryDirectory();
      final IntList myJobIds = new IntList(jobIds);
      shards.add(jobTmpDir);

      ForkJoinTask<?> task = jobs.submit(() -> {
        try (IntVocabBuilder.IntVocabWriter writer = new IntVocabBuilder.IntVocabWriter(jobTmpDir)) {
          for (int i : myJobIds) {
            List<Parameters> info = Parameters.parseReader(files.get(i).getReader()).getList("sentences", Parameters.class);
            processSentences(Integer.toString(i), info, "lemmas", writer);

            int total = totalComplete.incrementAndGet();
            if (msg.ready()) {
              System.out.println("Running threads: "+jobs.getActiveThreadCount());
              System.out.println(msg.estimate(total, N));
            }
          }
        } catch (IOException e) {
          throw new FatalError(e);
        }
      });

      tasks.add(task);
    }

    for (int i = 0; i < tasks.size(); i++) {
      ForkJoinTask<?> task = tasks.get(i);
      System.err.println("#join "+(i+1)+"/"+tasks.size()+" "+msg.estimate(i, tasks.size()));
      task.join();
    }

    // merge shards:
    try (IntVocabBuilder.IntVocabWriter finalWriter = new IntVocabBuilder.IntVocabWriter(output)) {
      for (int i = 0; i < shards.size(); i++) {
        System.err.println("Merged: "+(i+1)+"/"+shards.size()+" "+msg.estimate(i, shards.size()));
        TemporaryDirectory shard = shards.get(i);
        finalWriter.put(new IntVocabBuilder.IntVocabReader(shard));
      }
    }

    try (IntCoopIndex index = new IntCoopIndex(output)) {
      System.out.println(index.getCollectionLength());
    }

  }

  private static void processSentences(String id, List<Parameters> info, String field, IntVocabBuilder.IntVocabWriter writer) throws IOException {
    List<String> items = new ArrayList<>();
    for (Parameters sentence : info) {
      items.addAll(sentence.getAsList(field, String.class));
    }

    // finish doc:
    writer.process(id, items);
  }

}
