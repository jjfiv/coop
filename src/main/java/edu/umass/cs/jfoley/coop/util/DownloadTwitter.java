package edu.umass.cs.jfoley.coop.util;

import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.time.Debouncer;
import gnu.trove.set.hash.TLongHashSet;
import org.lemurproject.galago.utility.Parameters;
import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jfoley
 */
public class DownloadTwitter {
  public static void main(String[] args) throws IOException, TwitterException, InterruptedException {
    ConfigurationBuilder builder = new ConfigurationBuilder();
    builder.setJSONStoreEnabled(true);
    TwitterFactory factory = new TwitterFactory(builder.build());
    Twitter twitter = factory.getInstance();

    Directory place = Directory.Read("/mnt/scratch3/jfoley/signal-news/");
    TLongHashSet collected = new TLongHashSet();
    File alreadyTweetsFile = place.child("collected-tweets.ids");
    if(alreadyTweetsFile.exists()) {
      try(LinesIterable lines = LinesIterable.fromFile(alreadyTweetsFile)) {
        for (String line : lines) {
          try {
            collected.add(Long.parseLong(line));
          } catch (NumberFormatException nfe) {
            continue;
          }
        }
      }
    }

    long total = 3274089;
    final AtomicLong completed = new AtomicLong(0);
    try (PrintWriter output = IO.openPrintWriter(place.childPath("tweets.jsonl.gz"))) {
      Debouncer msg = new Debouncer();
      long[] ids = new long[100];
      try (LinesIterable tweets = LinesIterable.fromFile(place.childPath("tweet_data.txt.gz"))) {

        Iterable<Long> leftOverIds = IterableFns.filter(IterableFns.map(tweets, (form) -> {
          try {
            completed.incrementAndGet();
            return Long.parseLong(form);
          } catch (NumberFormatException nfe) {
            return -1L;
          }
        }), (id) -> !collected.contains(id));

        for (List<Long> t100 : IterableFns.batches(leftOverIds, 100)) {
          if(ids.length != t100.size()) {
            ids = new long[t100.size()];
          }
          for (int i = 0; i < t100.size(); i++) {
            ids[i] = t100.get(i);
          }

          while(true) {
            try {
              // fetch
              ResponseList<Status> lookup = twitter.lookup(ids);
              for (Status status : lookup) {
                String rawJSON = TwitterObjectFactory.getRawJSON(status);
                output.println(rawJSON);
              }
              RateLimitStatus rateLimitStatus = lookup.getRateLimitStatus();
              if (rateLimitStatus != null && rateLimitStatus.getRemaining() == 0) {
                try {
                  int sleepTime = Math.max(rateLimitStatus.getSecondsUntilReset()+1, 1);
                  System.out.println("Rate Limit: Sleeping for: " + sleepTime+" seconds");
                  Thread.sleep(1000 * sleepTime);
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
              }
              break; // done if we get here, happily.

            } catch (TwitterException twe) {
              if (twe.getErrorCode() == 88) { // rate limit exceeded
                int sleepTime = Math.max(1, twe.getRateLimitStatus().getSecondsUntilReset()+1);
                System.out.println("# Rate Limit Exceeded: Sleeping for: "+sleepTime+" seconds");
                Thread.sleep(sleepTime*1000);
                continue;
              } else {
                System.out.println("# TwitterException: ");
                twe.printStackTrace(System.out);
                throw new RuntimeException(twe);
              }
            }
          }

          if(msg.ready()) {
            System.out.println(msg.estimate(completed.get(), total));
          }
        }

      }
    }

  }

  public static final class FixForContinue {
    public static void main(String[] args) throws IOException {
      try (PrintWriter tweetsDone = IO.openPrintWriter("/mnt/scratch3/jfoley/signal-news/collected-tweets.ids")){
        try (LinesIterable input = LinesIterable.fromFile("/mnt/scratch3/jfoley/signal-news/tweets.jsonl.gz")) {
          long done = 0;
          Debouncer msg = new Debouncer();
          for (String line : input) {
            Parameters p = Parameters.parseString(line);
            done++;
            if(!p.isLong("id")) {
              continue;
            }
            tweetsDone.println(p.getLong("id"));
            if(msg.ready()) {
              System.out.println(msg.estimate(done));
            }
          }
        } catch (Exception ioe) {

        }
      }

    }
  }
}
