package dev.edgewave;

import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** Feed s16le mono 24kHz PCM; no reference timestamps or track-specific rules. */
public class TrackPatternCheck {
  public static void main(String[] args) throws Exception {
    byte[] bytes = Files.readAllBytes(Path.of(args[0]));
    short[] pcm = new short[bytes.length / 2];
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
    int limit = args.length > 1 ? Integer.parseInt(args[1]) * 1000 : pcm.length / 24;
    PcmSpectrum fft = new PcmSpectrum();
    TrackPatterns analyzer = new TrackPatterns();
    Map<TrackPatterns.Type, Integer> events = new EnumMap<>(TrackPatterns.Type.class);
    int[] requests = new int[10];
    long lastBottom = -8000;
    for (int ms = 0; ms < Math.min(limit, pcm.length / 24); ms += 20) {
      TrackPatterns.Event e = analyzer.analyze(fft.analyze(pcm, ms * 24, 24000), ms, 0);
      if (e == null) continue;
      events.merge(e.type, 1, Integer::sum);
      int mask = MusicalDirector.choose(e, 1023, true);
      for (int k = 0; k < 10; k++)
        if ((mask & (1 << k)) != 0) {
          if (k == 5) {
            if (ms - lastBottom < 8000) continue;
            lastBottom = ms;
          }
          requests[k]++;
        }
    }
    System.out.println("Duration seconds: " + Math.min(limit, pcm.length / 24) / 1000);
    System.out.println("Acoustic event counts: " + events);
    System.out.println("Requested effect families 0..9: " + Arrays.toString(requests));
    System.out.println(
        "Requests precede scene concurrency limits. Counts are not recognition-accuracy"
            + " measurements; no hand-labeled ground truth supplied.");
  }
}
