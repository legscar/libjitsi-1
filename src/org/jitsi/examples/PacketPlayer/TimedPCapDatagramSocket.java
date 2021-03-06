package org.jitsi.examples.PacketPlayer;

import java.io.*;
import java.net.*;
import java.nio.*;

import net.sf.fmj.media.Log;

public class TimedPCapDatagramSocket extends PCapDatagramSocket
{
    // An offset between the time the packet was written and the current nano time reading
    // It's the amount of time to add to the media time to make it system time.
    long mediaTimeOffset = Long.MIN_VALUE;
    StreamIdentifier stream;

    public TimedPCapDatagramSocket(String xiFilename, StreamIdentifier stream) throws IOException
    {
        super(xiFilename);
        this.stream = stream;
    }

    @Override
    public synchronized void receive(DatagramPacket p) throws IOException
    {
        long timeStampNanoSeconds;
        RtpData d;
        do
        {
            d = new RtpData(p);
            super.receive(d);
            timeStampNanoSeconds = d.timestamp;
        }
        while (!stream.matches(d));

        p.setPort(d.dstPort);
        p.setLength(d.payloadLength);

        try
        {
            if (! hasMediaOffsetEverBeenSet())
            {
                // Before we start, sleep for a second.  This hopefully makes
                // the replay a bit more realistic (the audio is set up before
                // we start receiving packets).
                /*int snoozy_time = 1000;
                Log.annotate(this, "Sleep for " + snoozy_time +
                                           " millis before sending in packets");
                Thread.sleep(snoozy_time);*/ // TODO put this back in, probably
                // Set the media time offset to be the difference between the
                // current time and the time the packet was captured.
                mediaTimeOffset = System.nanoTime() - timeStampNanoSeconds;
            }
            else
            {
                // Don't return until we actually would have read the packet.
                // Sleeping is more accurate than using a timer.
                long currentTime = System.nanoTime();
                long timeToPlayPacket = convertMediaToSystemTime(timeStampNanoSeconds);

                long timeToSleepFor = timeToPlayPacket - currentTime;
                long timeToSleepForJustMillis = timeToSleepFor / 1000000;
                int timeToSleepForJustNanos = (int) (timeToSleepFor % 1000000);

                if (timeToSleepFor > 0)
                {
                    if (timeToSleepForJustMillis > 30)
                    {
                        System.out.println("Warning: Been asked to sleep for a long time " + timeToSleepForJustMillis + "ms.");
                    }

                    Thread.sleep(timeToSleepForJustMillis, timeToSleepForJustNanos);
                }
            }
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    private long convertMediaToSystemTime(long timeStampNanoSeconds)
    {
        return timeStampNanoSeconds + mediaTimeOffset;
    }

    private boolean hasMediaOffsetEverBeenSet()
    {
        return mediaTimeOffset != Long.MIN_VALUE;
    }
}
