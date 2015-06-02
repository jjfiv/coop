package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.jfoley.coop.index.BuildIndex;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.AppFunction;

import java.util.ArrayList;
import java.util.List;

public class Main {

  public static List<AppFunction> functions = new ArrayList<>();
  static {
    functions.add(new FindKWIC());
    functions.add(new BuildIndex());
    functions.add(new RankTerms());
  }

  public static void printHelpAndDie() {
    System.err.println("try one of --fn=\n");
    for (AppFunction function : functions) {
      System.err.println(StrUtil.indent(function.getHelpString(), "\t"));
      System.err.println();
    }
    System.exit(-1);
  }

  public static void main(String[] args) throws Exception {
    Parameters argp = Parameters.parseArgs(args);
    String fn = argp.get("fn", (String) null);
    if(fn == null) {
      printHelpAndDie();
    }
    for (AppFunction function : functions) {
      if(function.getName().equals(fn)) {
        function.run(argp, System.out);
        return;
      }
    }
    System.err.println("--fn= " + fn + " not found!");
    printHelpAndDie();
  }
}
