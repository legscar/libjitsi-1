package org.jitsi.examples.PacketPlayer;

import java.awt.*;
import java.beans.*;
import java.io.*;
import java.util.*;
import java.util.List;

import javax.swing.*;

import org.jitsi.examples.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.service.neomedia.event.SimpleAudioLevelListener;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.util.Logger;
import org.jitsi.util.event.*;
import org.jitsi.util.swing.*;

class VideoFrame extends JFrame
{
    private static final long serialVersionUID = 1L;
    private VideoContainer vc;

    VideoFrame(Component video)
    {
        super("Video");
        vc = new VideoContainer(video, false);
        this.add(vc);
        this.setSize(1000, 1000);
    }
}

/**
 * Play RTP from a file.
 */
public class PlayRTP
{
    private static boolean started;

    private MediaStream mediaStream;

    final ValueBox<Integer> maxStreamAudioLevel =
        new ValueBox<Integer>(SimpleAudioLevelListener.MIN_LEVEL); // Default to lowest possible

    boolean foundVideo;

    private static final Logger logger
        = Logger.getLogger(PlayRTP.class);

    public PlayRTP()
    {
        initIfRequired();
    }

    private synchronized void checkForVideo()
    {
        if (!foundVideo)
        {
            System.out.println("Still finding video");

            List<Component> videos = ((VideoMediaStream) mediaStream).getVisualComponents();
            if (! videos.isEmpty())
            {
                System.out.println("Found Video!");

                foundVideo = true;
                final Component video = videos.get(0);
                SwingUtilities.invokeLater(new Runnable(){

                    @Override
                    public void run() {
                        System.out.println("Displaying Video!");

                        VideoFrame videoFrame = new VideoFrame(video);
                        videoFrame.setVisible(true);
                    }
                });
            }
        }
    }

    //private StreamConnector connector;
    /**
     * Initializes the receipt of audio.
     *
     * @return The stream connector that can be used to check if the socket is
     * still connected.
     * @throws Exception if anything goes wrong while initializing this instance
     */
    private StreamConnector playMedia(String filename, MediaFormat initialFormat,
        List<Byte> dynamicRTPPayloadTypes, MediaFormat dynamicFormat,
        int ssrc, boolean auto) throws Exception
    {
        /*
         * Prepare for the start of the transmission i.e. initialize the
         * MediaStream instances.
         */
        MediaService mediaService = LibJitsi.getMediaService();
        MediaDevice device = mediaService.getDefaultDevice(
            initialFormat.getMediaType(), MediaUseCase.CALL);
        mediaStream = mediaService.createMediaStream(device);
        mediaStream.setDirection(MediaDirection.RECVONLY);

        if (initialFormat.getMediaType().equals(MediaType.VIDEO))
        {
            foundVideo = false;

            ((VideoMediaStream) mediaStream).addVideoListener(new VideoListener(){

                @Override
                public void videoAdded(VideoEvent event)
                {
                    checkForVideo();
                }

                @Override
                public void videoRemoved(VideoEvent event)
                {
                    checkForVideo();
                }

                @Override
                public void videoUpdate(VideoEvent event)
                {
                    checkForVideo();
                }
            });

            PropertyChangeListener pchange = new PropertyChangeListener(){
                @Override
                public synchronized void propertyChange(PropertyChangeEvent evt)
                {
                    System.out.println("Change event: " + evt);
                    checkForVideo();
                }
            };

            mediaStream.addPropertyChangeListener(pchange);
            pchange.propertyChange(null);
        }


        /*
         * The MediaFormat instances which do not have a static RTP payload type
         * number association must be explicitly assigned a dynamic RTP payload
         * type number.
         */
        for (byte dynamicPT : dynamicRTPPayloadTypes)
        {
            mediaStream.addDynamicRTPPayloadType(dynamicPT, dynamicFormat);
        }

        mediaStream.setFormat(initialFormat);

        // connector
        final StreamConnector connector = new PCapStreamConnector(filename, ssrc);
        mediaStream.setConnector(connector);

        if ((mediaStream instanceof AudioMediaStream) && auto) {
          logger.info("Auto mode, with an AudioMediaStream");
          AudioMediaStream audioMediaStream = (AudioMediaStream)mediaStream;
          audioMediaStream.setStreamAudioLevelListener(new SimpleAudioLevelListener(){
        @Override
        public void audioLevelChanged(int level) {
          if (level > maxStreamAudioLevel.get()) {
            maxStreamAudioLevel.set(level);
            logger.debug("-- Max Stream Audio level increased to: " + level);
          }
          // If we got some audio when in auto mode, we can just skip
          // and carry on with the next iteration (we're looking for
          // the case where there was no audio).
          if (level > SimpleAudioLevelListener.MIN_LEVEL){
            logger.info("Got some audio - this one works, so move on to the next.");
            connector.getDataSocket().close();
            try {
              Thread.sleep(110); // Give the socket time to close so we don't get a bunch of new callbacks
            } catch (InterruptedException e) { e.printStackTrace(); }
            logger.debug("...end of sleep");
          }
          }});
        }

        mediaStream.start();

        return connector;
    }

    /**
     * Close the <tt>MediaStream</tt>s.
     */
    private void close()
    {
        if (mediaStream != null)
        {
            try
            {
                mediaStream.stop();
            }
            finally
            {
                mediaStream.close();
                mediaStream = null;
            }
        }
    }

    private static void initIfRequired()
    {
        if (!started)
            LibJitsi.start();
    }

    private static void shutdown()
    {
        LibJitsi.stop();
    }

  /*
     * Blocking
     */
    public void playFile(String filename, MediaFormat initialFormat,
        List<Byte> dynamicRTPPayloadTypes, MediaFormat dynamicFormat, int ssrc)
    {
        playFile(filename, initialFormat, dynamicRTPPayloadTypes, dynamicFormat,
            ssrc, false);
    }

    /**
     * Internal version.
     */
    private void playFile(String filename, MediaFormat initialFormat,
        List<Byte> dynamicRTPPayloadTypes, MediaFormat dynamicFormat, int ssrc, boolean auto)
    {
        // Now play the stream
        maxStreamAudioLevel.set(SimpleAudioLevelListener.MIN_LEVEL);
      
        close();
        initIfRequired();

        try
        {
            StreamConnector connector = playMedia(filename, initialFormat,
                dynamicRTPPayloadTypes, dynamicFormat, ssrc, auto);
            while (connector.getDataSocket().isConnected())
            {
                Thread.sleep(100);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            close();
            shutdown();
        }

        System.out.println("Finshed playing file.");
    }

    /**
     * Run RTPPlayer in automatic mode.
     * @param filename
     * @param initialFormat
     * @param dynamicPayloadTypes
     * @param dynamicFormat
     * @param ssrc
     * @param n_iterations - how many times to replay the audio sample. 0=infinite.
     * @param stop_if_iter_had_no_audio - true if the loop should quit as soon
     * as no audio was heard for that iteration (so that the diags are preserved)
     */
  public void playFileInAutoMode(String filename, MediaFormat initialFormat,
      List<Byte> dynamicPayloadTypes, MediaFormat dynamicFormat,
      int ssrc, int n_iterations, boolean stop_if_iter_had_no_audio)
  {
      int ix = 0;
      while ((ix < n_iterations) || (n_iterations == 0))
        {
            // Now play the stream
          logger.debug("Play file, attempt: " + (ix+1));
          maxStreamAudioLevel.set(SimpleAudioLevelListener.MIN_LEVEL);
          playFile(filename, initialFormat, dynamicPayloadTypes,
              dynamicFormat, ssrc, true);

          int audioLevel = maxStreamAudioLevel.get();
          logger.info("Max local audio level recorded: " + audioLevel);
          if (audioLevel <= SimpleAudioLevelListener.MIN_LEVEL) {
            logger.warn("!!  No audio on loop " + (ix+1) + " !!");
            if (stop_if_iter_had_no_audio) {
                break;
            }
          }
          ix++;
        }
        logger.info((ix < n_iterations) ?
            "Finished - stopped during attempt " + (ix+1) + " of " + n_iterations
            : "Finshed after completing " + n_iterations + " attempts");
    
  }

    public static void main(String[] args) throws Exception
    {
        // We need one parameter . For example,
        // ant run-example -Drun.example.name=PlayRTP
        // -Drun.example.arg.line="--filename=test.pcap"
        if (args.length < 1)
        {
            prUsage();
        }
        else
        {
            Map<String, String> argMap = AVTransmit2.parseCommandLineArgs(args);

            try
            {
                PlayRTP playRTP = new PlayRTP();
                List<Byte> pts = Arrays.asList((byte) 96);
                MediaFormat format = LibJitsi.getMediaService()
                    .getFormatFactory().createMediaFormat("SILK", (double)8000);
                playRTP.playFile(argMap.get(FILENAME), format, pts, format, -1);
            }
            finally
            {
                shutdown();
            }
            System.exit(0);
        }
    }

    /**
     * The filename to play
     */
    private static final String     FILENAME = "--filename=";

    /**
     * The list of command-line arguments accepted as valid.
     */
    private static final String[][] ARGS     = {{FILENAME,
            "The filename to play."          },};


    /**
     * Outputs human-readable description about the usage.
     */
    private static void prUsage()
    {
        PrintStream err = System.err;

        err.println("Usage: " + PlayRTP.class.getName() + " <args>");
        err.println("Valid args:");
        for (String[] arg : ARGS)
            err.println("  " + arg[0] + " " + arg[1]);
    }
}
