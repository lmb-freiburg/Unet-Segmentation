/**************************************************************************
 *
 * Copyright (C) 2018 Thorsten Falk
 *
 *        Image Analysis Lab, University of Freiburg, Germany
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 **************************************************************************/

package de.unifreiburg.unet;

// ImageJ stuff
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.gui.PointRoi;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.process.ImageProcessor;
import ij.process.FloatProcessor;
import ij.process.ByteProcessor;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.CompositeConverter;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;

import java.awt.Color;
import java.awt.Point;
import java.awt.geom.Point2D;

// Java utilities
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Vector;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// For remote SSH connections
import com.jcraft.jsch.Session;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

// HDF5 stuff
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ch.systemsx.cisd.hdf5.HDF5FloatStorageFeatures;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Writer;
import ch.systemsx.cisd.hdf5.IHDF5WriterConfigurator;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ch.systemsx.cisd.hdf5.HDF5DataSetInformation;
import ch.systemsx.cisd.base.mdarray.MDFloatArray;


public class Tools {

/*======================================================================*/
/*!
 *   Remove the file with given path from the remote host via sftp.
 *
 *   \param path The absolute file path on the remote host
 *   \param session The open SSH session
 *   \param job If not null, progress will be reported to the given Job
 *     otherwise it will be shown in the ImageJ status bar
 *
 *   \exception JSchException if the SSH session is not open
 *   \exception IOException if the remote file could not be removed
 *   \exception InterruptedException if the user requests thread termination
 *   \exception SftpException if the Sftp connection fails
 */
/*======================================================================*/
  public static void removeFile(String path, Session session, Job job)
      throws JSchException, IOException, InterruptedException, SftpException {
    job.progressMonitor().init(0, "", "", 1);
    job.progressMonitor().count(
        "Removing file '" + session.getHost() + ":" + path + "'", 0);
    IJ.log(session.getUserName() + "@" + session.getHost() + " $ rm \"" +
           path + "\"");
    Channel channel = session.openChannel("sftp");
    channel.connect();
    ChannelSftp c = (ChannelSftp)channel;
    c.rm(path);
    channel.disconnect();
    job.progressMonitor().count(1);
  }

/*======================================================================*/
/*!
 *   Rename the file with given path from the remote host via sftp.
 *
 *   \param oldpath The absolute file path on the remote host
 *   \param newpath The new absolute file path on the remote host
 *   \param session The open SSH session
 *   \param job If not null, progress will be reported to the given Job
 *     otherwise it will be shown in the ImageJ status bar
 *
 *   \exception JSchException if the SSH session is not open
 *   \exception IOException if the remote file could not be removed
 *   \exception InterruptedException if the user requests thread termination
 *   \exception SftpException if the Sftp connection fails
 */
/*======================================================================*/
  public static void renameFile(
      String oldpath, String newpath, Session session, Job job)
      throws JSchException, IOException, InterruptedException, SftpException {
    job.progressMonitor().init(0, "", "", 1);
    job.progressMonitor().count(
        "Renaming file '" + session.getHost() + ":" + oldpath + "' to '" +
        newpath + "'", 0);
    IJ.log(session.getUserName() + "@" + session.getHost() + " $ mv \"" +
           oldpath + "\" \"" + newpath + "\"");
    Channel channel = session.openChannel("sftp");
    channel.connect();
    ChannelSftp c = (ChannelSftp)channel;
    c.rename(oldpath, newpath);
    channel.disconnect();
    job.progressMonitor().count(1);
  }

/*======================================================================*/
/*!
 *   Remove the folder with given path from the remote host via sftp.
 *   The folder must be empty before it can be removed.
 *
 *   \param path The absolute folder path on the remote host
 *   \param session The open SSH session
 *   \param job If not null, progress will be reported to the given Job
 *     otherwise it will be shown in the ImageJ status bar
 *
 *   \exception JSchException if the SSH session is not open
 *   \exception IOException if the remote folder could not be removed
 *   \exception InterruptedException if the user requests thread termination
 *   \exception SftpException if the Sftp connection fails
 */
/*======================================================================*/
  public static void removeFolder(String path, Session session, Job job)
      throws JSchException, IOException, InterruptedException, SftpException {
    job.progressMonitor().init(0, "", "", 1);
    job.progressMonitor().count(
        "Removing folder '" + session.getHost() + ":" + path + "'", 0);
    IJ.log(session.getUserName() + "@" + session.getHost() + " $ rmdir \"" +
           path + "\"");
    Channel channel = session.openChannel("sftp");
    channel.connect();
    ChannelSftp c = (ChannelSftp)channel;
    c.rmdir(path);
    channel.disconnect();
    job.progressMonitor().count(1);
  }

/*======================================================================*/
/*!
 *   Upload the given local file to the remote host via sftp. Required
 *   folders on the remote host will be created and returned in reverse
 *   creation order to allow to easily clean up again later.
 *
 *   \param inFile The file to copy on the local host
 *   \param outFileName The absolute file path on the remote host
 *   \param session The open SSH session
 *   \param job If not null, progress will be reported to the given Job
 *     otherwise it will be shown in the ImageJ status bar
 *
 *   \exception JSchException if the SSH session is not open
 *   \exception IOException if the file could not be copied
 *   \exception InterruptedException if the user requests thread termination
 *   \exception SftpException if the Sftp connection fails
 *
 *   \return A Vector containing the absolute paths of created folders on
 *     the remote host. The vector is sorted in reverse creation order to
 *     allow simple forward iteration for removing the folders again
 */
/*======================================================================*/
  public static Vector<String> put(
      File inFile, String outFileName, Session session, Job job)
      throws JSchException, IOException, InterruptedException, SftpException {
    Channel channel = session.openChannel("sftp");
    channel.connect();
    ChannelSftp c = (ChannelSftp)channel;

    // Recursively create parent folders
    String[] folders = outFileName.split("/");
    Vector<String> createdFolders = new Vector<String>();
    String currentFolder = "/";
    c.cd("/");
    for (int i = 0; i < folders.length - 1; ++i) {
      if (folders[i].length() > 0) {
        try {
          c.cd(folders[i]);
          currentFolder += "/" + folders[i];
        }
        catch (SftpException e) {
          job.progressMonitor().count(
              "Creating folder '" + currentFolder + "/" + folders[i] +
              "' on host '" + session.getHost() + "'", 0);
          IJ.log(
              session.getUserName() + "@" + session.getHost() +
              " $ mkdir \"" + currentFolder + "/" + folders[i] + "\"");
          c.mkdir(folders[i]);
          c.cd(folders[i]);
          if (!currentFolder.endsWith("/")) currentFolder += "/";
          currentFolder += folders[i];
          createdFolders.add(0, currentFolder);
        }
      }
    }

    // Copy the file
    job.progressMonitor().count(
        "Copying '" + inFile.getName() + "' to host '" +
        session.getHost() + "'", 0);
    IJ.log(
        "$ sftp \"" + inFile.getAbsolutePath() + "\" \"" +
        session.getUserName() + "@" + session.getHost() + ":" +
        session.getPort() + ":" + outFileName + "\"");
    c.put(inFile.getAbsolutePath(), outFileName, job.progressMonitor(),
          ChannelSftp.OVERWRITE);
    if (job.progressMonitor().canceled()) {
      c.rm(outFileName);
      for (int i = 0; i < createdFolders.size(); ++i)
          c.rmdir(createdFolders.get(i));
      channel.disconnect();
      throw new InterruptedException("Upload canceled by user");
    }
    channel.disconnect();

    return createdFolders;
  }

/*======================================================================*/
/*!
 *   Fetch the remote file with given path via sftp. The file will be marked
 *   for deletion (deleteOnExit()) on Java virtual machine shutdown. So if
 *   you want to keep it you have to explicitly remove this flag from the
 *   File after the copy process.
 *
 *   \param inFileName The file to copy on the remote host
 *   \param outFile The File on the local host to create
 *   \param session The open SSH session
 *   \param job If not null, progress will be reported to the given Job
 *     otherwise it will be shown in the ImageJ status bar
 *
 *   \exception JSchException if the SSH session is not open
 *   \exception IOException if the file could not be copied
 *   \exception InterruptedException if the user requests thread termination
 *   \exception SftpException if the Sftp connection fails
 */
/*======================================================================*/
  public static void get(
      String inFileName, File outFile, Session session, Job job)
      throws JSchException, IOException, InterruptedException, SftpException {
    Channel channel = session.openChannel("sftp");
    channel.connect();
    ChannelSftp c = (ChannelSftp)channel;

    // Recursively create parent folders
    Vector<File> createdFolders = new Vector<File>();
    File folder = outFile.getParentFile();
    while (folder != null && !folder.isDirectory()) {
      createdFolders.add(folder);
      folder = folder.getParentFile();
    }
    for (int i = createdFolders.size() - 1; i >= 0; --i) {
      job.progressMonitor().count(
          "Creating folder '" + createdFolders.get(i) + "'", 0);
      IJ.log("$ mkdir \"" + createdFolders.get(i) + "\"");
      if (!createdFolders.get(i).mkdir())
          throw new IOException(
              "Could not create folder '" +
              createdFolders.get(i).getAbsolutePath() + "'");
      createdFolders.get(i).deleteOnExit();
    }

    // Copy the file
    job.progressMonitor().count(
        "Fetching '" + inFileName + "' from host '" + session.getHost() + "'",
        0);
    IJ.log(
        "$ sftp \"" + session.getUserName() +
        "@" + session.getHost() + ":" + session.getPort() + ":" +
        inFileName + "\" \"" + outFile.getAbsolutePath() + "\"");
    outFile.deleteOnExit();
    c.get(inFileName, outFile.getAbsolutePath(), job.progressMonitor(),
          ChannelSftp.OVERWRITE);

    channel.disconnect();

    if (job.progressMonitor().canceled())
        throw new InterruptedException("Download canceled by user");
  }

/*======================================================================*/
/*!
 *   If the given ImagePlus is a color image (stack), a new ImagePlus will be
 *   created with color components split to individual channels.
 *   For grayscale images calling this method is a noop and a reference to
 *   the input ImagePlus is returned
 *
 *   \param imp The ImagePlus to convert to a composite multi channel image
 *   \param job Task progress will be reported via this Job.
 *
 *   \return The newly created ImagePlus or if no conversion was required
 *     a reference to imp
 */
/*======================================================================*/
  public static ImagePlus makeComposite(ImagePlus imp, Job job) {
    if (imp.getType() != ImagePlus.COLOR_256 &&
        imp.getType() != ImagePlus.COLOR_RGB) return imp;

    job.progressMonitor().count("Splitting color channels", 0);
    ImagePlus out = CompositeConverter.makeComposite(imp);
    out.setTitle(imp.getTitle() + " - composite");
    out.setCalibration(imp.getCalibration());
    return out;
  }

/*======================================================================*/
/*!
 *   If the given ImagePlus is not 32Bit, a new ImagePlus will be
 *   created with the content of the given ImagePlus to 32Bit float.
 *   For 32Bit images calling this method is a noop and a reference to
 *   the input ImagePlus is returned
 *
 *   \param imp The ImagePlus to convert to float
 *   \param job Task progress will be reported via this Job.
 *
 *   \return The newly created ImagePlus or if no conversion was required
 *     a reference to imp
 */
/*======================================================================*/
  public static ImagePlus convertToFloat(ImagePlus imp, Job job)
      throws InterruptedException {
    if (imp.getBitDepth() == 32) return imp;

    job.progressMonitor().init(0, "", "", imp.getImageStackSize());
    job.progressMonitor().count("Converting hyperstack to float", 0);
    ImagePlus out = IJ.createHyperStack(
        imp.getTitle() + " - 32-Bit", imp.getWidth(), imp.getHeight(),
        imp.getNChannels(), imp.getNSlices(), imp.getNFrames(), 32);
    out.setCalibration(imp.getCalibration());

    if (imp.getStackSize() == 1) {
      job.progressMonitor().count(1);
      out.setProcessor(imp.getProcessor().duplicate().convertToFloat());
      return out;
    }

    for (int i = 1; i <= imp.getImageStackSize(); i++) {
      if (job.interrupted())
          throw new InterruptedException("Aborted by user");
      job.progressMonitor().count(1);
      out.getStack().setProcessor(
          imp.getStack().getProcessor(i).duplicate().convertToFloat(), i);
    }
    return out;
  }

/*======================================================================*/
/*!
 *   If the model definition of the given Job requires 2-D data, both
 *   time and z will be interpreted as time. The stack layout is changed
 *   accordingly. For 3-D models or if the image only contains one slice
 *   per time point, this method is a noop and a reference to
 *   the input ImagePlus is returned
 *
 *   \param imp The ImagePlus to rearrange
 *   \param job The Job containing the model definition. Task progress
 *     will be reported via this Job.
 *
 *   \return The newly created ImagePlus or if no conversion was required
 *     a reference to imp
 */
/*======================================================================*/
  public static ImagePlus fixStackLayout(ImagePlus imp, Job job)
      throws InterruptedException {
    if (job.model().nDims() == 3 || imp.getNSlices() == 1) return imp;
    if (!IJ.showMessageWithCancel(
            "2-D model selected", "The selected model " +
            "requires 2-D Input.\n" +
            "Applying 2-D segmentation to all slices."))
        throw new InterruptedException();

    ImagePlus out = IJ.createHyperStack(
        imp.getTitle() + " - reordered", imp.getWidth(), imp.getHeight(),
        imp.getNChannels(), 1, imp.getNSlices() * imp.getNFrames(), 32);
    Calibration cal = imp.getCalibration().copy();
    cal.pixelDepth = 1;
    out.setCalibration(cal);
    job.progressMonitor().init(0, "", "", imp.getImageStackSize());
    job.progressMonitor().count("Fixing stack layout", 0);
    for (int i = 1; i <= imp.getImageStackSize(); ++i) {
      if (job.interrupted())
          throw new InterruptedException("Aborted by user");
      job.progressMonitor().count(1);
      out.getStack().setProcessor(
          imp.getStack().getProcessor(i).duplicate(), i);
    }
    return out;
  }

  public static ImagePlus rescaleXY(
      ImagePlus imp, int interpolationMethod, Job job)
      throws InterruptedException {
    Calibration cal = imp.getCalibration().copy();
    int offs = (job.model().elementSizeUm.length == 2) ? 0 : 1;
    cal.pixelHeight = job.model().elementSizeUm[offs];
    cal.pixelWidth = job.model().elementSizeUm[offs + 1];

    float[] scales = new float[2];
    scales[0] = (float)(imp.getCalibration().pixelHeight / cal.pixelHeight);
    scales[1] = (float)(imp.getCalibration().pixelWidth / cal.pixelWidth);
    if (scales[0] == 1 && scales[1] == 1) return imp;

    job.progressMonitor().init(0, "", "", imp.getImageStackSize());
    job.progressMonitor().count("Rescaling hyperstack (xy)", 0);
    IJ.log("Rescaling Hyperstack (xy) from [" +
           imp.getCalibration().pixelDepth + ", " +
           imp.getCalibration().pixelHeight + ", " +
           imp.getCalibration().pixelWidth + "] to [" +
           cal.pixelDepth + ", " +
           cal.pixelHeight + ", " +
           cal.pixelWidth + "]");

    ImagePlus out = IJ.createHyperStack(
        imp.getTitle() + " - rescaled (xy)",
        (int)(imp.getWidth() * scales[1]),
        (int)(imp.getHeight() * scales[0]), imp.getNChannels(),
        imp.getNSlices(), imp.getNFrames(), 32);
    out.setCalibration(cal);

    // ImageJ interpolation method NEAREST_NEIGHBOR seems to be broken...
    // To ensure proper interpolation we do the interpolation ourselves
    int W = imp.getWidth();
    int H = imp.getHeight();
    for (int i = 0; i < imp.getImageStackSize(); ++i) {
      if (job.interrupted())
          throw new InterruptedException("Aborted by user");
      job.progressMonitor().count(1);
      ImageProcessor ipIn =
          (imp.getImageStackSize() == 1) ? imp.getProcessor() :
          imp.getStack().getProcessor(i + 1);
      ImageProcessor ipOut =
          (imp.getImageStackSize() == 1) ? out.getProcessor() :
          out.getStack().getProcessor(i + 1);
      for (int y = 0; y < out.getHeight(); ++y) {
        float yRd = y / scales[0];
        int yBase = (int)Math.floor(yRd);
        float dy = yRd - yBase;
        for (int x = 0; x < out.getWidth(); ++x) {
          float xRd = x / scales[1];
          int xBase = (int)Math.floor(xRd);
          float dx = xRd - xBase;
          if (interpolationMethod == ImageProcessor.NEAREST_NEIGHBOR)
              ipOut.setf(x, y, ipIn.getf(xBase, yBase));
          else {
            ipOut.setf(
                x, y,
                (1 - dx) * (1 - dy) * ipIn.getf(xBase, yBase) +
                (1 - dx) * dy * ipIn.getf(xBase, Math.min(yBase + 1, H - 1)) +
                dx * (1 - dy) * ipIn.getf(Math.min(xBase + 1, W - 1), yBase) +
                dx * dy * ipIn.getf(
                    Math.min(xBase + 1, W - 1), Math.min(yBase + 1, H - 1)));
          }
        }
      }
    }
    return out;
  }

  public static ImagePlus rescaleZ(
      ImagePlus imp, int interpolationMethod, Job job)
      throws InterruptedException {
    if (job.model().elementSizeUm.length == 2 || imp.getNSlices() == 1)
        return imp;

    double scale = imp.getCalibration().pixelDepth /
        job.model().elementSizeUm[0];
    if (scale == 1) return imp;

    Calibration cal = imp.getCalibration().copy();
    cal.pixelDepth = job.model().elementSizeUm[0];
    ImagePlus out = IJ.createHyperStack(
        imp.getTitle() + " - rescaled (z)",
        imp.getWidth(), imp.getHeight(), imp.getNChannels(),
        (int)(imp.getNSlices() * scale), imp.getNFrames(), 32);
    out.setCalibration(cal);
    job.progressMonitor().init(0, "", "", imp.getImageStackSize());
    job.progressMonitor().count("Rescaling hyperstack (z)", 0);
    IJ.log("Rescaling Hyperstack (z) from [" +
           imp.getCalibration().pixelDepth + ", " +
           imp.getCalibration().pixelHeight + ", " +
           imp.getCalibration().pixelWidth + "] to [" +
           cal.pixelDepth + ", " +
           cal.pixelHeight + ", " +
           cal.pixelWidth + "] (scale factor = " + scale + ")");
    IJ.log("  Input shape = [" +
           imp.getNFrames() + ", " +
           imp.getNChannels() + ", " +
           imp.getNSlices() + ", " +
           imp.getHeight() + ", " +
           imp.getWidth() + "]");
    IJ.log("  Output shape = [" +
           out.getNFrames() + ", " +
           out.getNChannels() + ", " +
           out.getNSlices() + ", " +
           out.getHeight() + ", " +
           out.getWidth() + "]");

    for (int t = 1; t <= out.getNFrames(); ++t) {
      for (int z = 1; z <= out.getNSlices(); ++z) {
        double zTmp = (z - 1) / scale + 1;
        if (interpolationMethod == ImageProcessor.BILINEAR) {
          int zIn = (int)Math.floor(zTmp);
          double lambda = zTmp - zIn;
          for (int c = 1; c <= out.getNChannels(); ++c) {
            if (job.interrupted())
                throw new InterruptedException("Aborted by user");
            job.progressMonitor().count(1);
            ImageProcessor ip = imp.getStack().getProcessor(
                imp.getStackIndex(c, zIn, t)).duplicate();
            if (lambda != 0) {
              ip.multiply(1 - lambda);
              if (zIn + 1 <= imp.getNSlices()) {
                ImageProcessor ip2 = imp.getStack().getProcessor(
                    imp.getStackIndex(c, zIn + 1, t)).duplicate();
                float[] ipData = (float[]) ip.getPixels();
                float[] ip2Data = (float[]) ip2.getPixels();
                for (int i = 0; i < imp.getHeight() * imp.getWidth(); ++i)
                    ipData[i] += lambda * ip2Data[i];
              }
            }
            out.getStack().setProcessor(ip, out.getStackIndex(c, z, t));
          }
        }
        else {
          int zIn = (int)Math.round(zTmp);
          for (int c = 1; c <= out.getNChannels(); ++c) {
            if (job.interrupted())
                throw new InterruptedException("Aborted by user");
            job.progressMonitor().count(1);
            out.getStack().setProcessor(
                imp.getStack().getProcessor(
                    imp.getStackIndex(c, zIn, t)).duplicate(),
                out.getStackIndex(c, z, t));
          }
        }
      }
    }
    return out;
  }

  public static ImagePlus normalizeValues(ImagePlus imp, Job job)
      throws InterruptedException {
    if (job.model().normalizationType == 0) return imp;
    ImagePlus out = imp;
    float[] scales = new float[imp.getNFrames()];
    float[] offsets = new float[imp.getNFrames()];

    long nSteps = 2 * imp.getStackSize();
    if (job.model().normalizationType == 2) nSteps += imp.getStackSize();
    job.progressMonitor().init(0, "", "", nSteps);

    for (int t = 1; t <= imp.getNFrames(); ++t) {
      switch (job.model().normalizationType) {
      case 1: { // MIN/MAX
        float minValue = Float.POSITIVE_INFINITY;
        float maxValue = Float.NEGATIVE_INFINITY;
        for (int z = 1; z <= imp.getNSlices(); ++z) {
          for (int c = 1; c <= imp.getNChannels(); ++c) {
            if (job.interrupted())
                throw new InterruptedException("Aborted by user");
            job.progressMonitor().count(
                "Computing data min/max (t=" + t + ", z=" + z +
                ", c=" + c + ")", 1);
            float[] values = (float[])
                imp.getStack().getPixels(imp.getStackIndex(c, z, t));
            for (int i = 0; i < imp.getHeight() * imp.getWidth(); ++i) {
              if (values[i] > maxValue) maxValue = values[i];
              if (values[i] < minValue) minValue = values[i];
            }
          }
        }
        scales[t - 1] = (float)(1.0 / (maxValue - minValue));
        offsets[t - 1] = -minValue;
        break;
      }
      case 2: { // Zero mean, unit standard deviation
        float sum = 0;
        for (int z = 1; z <= imp.getNSlices(); ++z) {
          for (int c = 1; c <= imp.getNChannels(); ++c) {
            if (job.interrupted())
                throw new InterruptedException("Aborted by user");
            job.progressMonitor().count(
                "Computing data mean (t=" + t + ", z=" + z +
                ", c=" + c + ")", 1);
            float[] values = (float[])
                imp.getStack().getPixels(imp.getStackIndex(c, z, t));
            for (int i = 0; i < imp.getHeight() * imp.getWidth(); ++i)
                sum += values[i];
          }
        }
        offsets[t - 1] = -sum / (imp.getNSlices() * imp.getNChannels() *
                                 imp.getHeight() * imp.getWidth());
        sum = 0;
        for (int z = 1; z <= imp.getNSlices(); ++z) {
          for (int c = 1; c <= imp.getNChannels(); ++c) {
            if (job.interrupted())
                throw new InterruptedException("Aborted by user");
            job.progressMonitor().count(
                "Computing data standard deviation (t=" + t + ", z=" + z +
                ", c=" + c + ")", 1);
            float[] values = (float[])
                imp.getStack().getPixels(imp.getStackIndex(c, z, t));
            for (int i = 0; i < imp.getHeight() * imp.getWidth(); ++i)
                sum += (values[i] + offsets[t - 1]) *
                    (values[i] + offsets[t - 1]);
          }
        }
        scales[t - 1] = (float)Math.sqrt(
            (imp.getNSlices() * imp.getNChannels() * imp.getHeight() *
             imp.getWidth()) / sum);
        break;
      }
      case 3: { // Max norm 1
        float maxSqrNorm = 0;
        for (int z = 1; z <= imp.getNSlices(); ++z) {
          float[] sqrNorm = new float[imp.getHeight() * imp.getWidth()];
          Arrays.fill(sqrNorm, 0);
          for (int c = 1; c <= imp.getNChannels(); ++c) {
            if (job.interrupted())
                throw new InterruptedException("Aborted by user");
            job.progressMonitor().count(
                "Computing data norm (t=" + t + ", z=" + z +
                ", c=" + c + ")", 1);
            float[] values = (float[])
                imp.getStack().getPixels(imp.getStackIndex(c, z, t));
            for (int i = 0; i < imp.getHeight() * imp.getWidth(); ++i)
                sqrNorm[i] += values[i] * values[i];
          }
          for (int i = 0; i < imp.getHeight() * imp.getWidth(); ++i)
              if (sqrNorm[i] > maxSqrNorm) maxSqrNorm = sqrNorm[i];
        }
        offsets[t - 1] = 0;
        scales[t - 1] = (float)(1.0 / Math.sqrt(maxSqrNorm));
        break;
      }
      default:
        break;
      }

      IJ.log("t = " + t + ": scale = " + scales[t - 1] + ", offset = " +
             offsets[t - 1]);

      if (imp == out && (scales[t - 1] != 1 || offsets[t - 1] != 0)) {
        out = IJ.createHyperStack(
            imp.getTitle() + " - normalized", imp.getWidth(),
            imp.getHeight(), imp.getNChannels(), imp.getNSlices(),
            imp.getNFrames(), 32);
        out.setCalibration(imp.getCalibration().copy());

        // Special treatment for single image
        if (imp.getImageStackSize() == 1) {
          job.progressMonitor().count("Normalizing", 1);
          ImageProcessor ip = imp.getProcessor().duplicate();
          ip.add(offsets[t - 1]);
          ip.multiply(scales[t - 1]);
          out.setProcessor(ip);
          return out;
        }

        for (int z = 1; z <= imp.getNSlices(); ++z) {
          for (int c = 1; c <= imp.getNChannels(); ++c) {
            job.progressMonitor().count(
                "Normalizing (t=" + t + ", z=" + z +
                ", c=" + c + ")", 1);
            ImageProcessor ip =
                imp.getStack().getProcessor(
                    imp.getStackIndex(c, z, t)).duplicate();
            ip.add(offsets[t - 1]);
            ip.multiply(scales[t - 1]);
            out.getStack().setProcessor(ip, out.getStackIndex(c, z, t));
          }
        }
      }
      else {
        for (int z = 1; z <= imp.getNSlices(); ++z) {
          for (int c = 1; c <= imp.getNChannels(); ++c) {
            job.progressMonitor().count(
                "Normalizing (t=" + t + ", z=" + z +
                ", c=" + c + ")", 1);
            ImageProcessor ip =
                imp.getStack().getProcessor(
                    imp.getStackIndex(c, z, t)).duplicate();
            ip.add(offsets[t - 1]);
            ip.multiply(scales[t - 1]);
            out.getStack().setProcessor(
                ip, out.getStackIndex(c, z, t));
          }
        }
      }
    }
    return out;
  }

/*======================================================================*/
/*!
 *   Convert the given ImagePlus to comply to the given unet model. If the
 *   unet model is 2D, both, time and z dimension are treated as time
 *   leading to slicewise segmentation. If the given ImagePlus already
 *   fulfills the requirements of the model it will be simply returned, so
 *   be careful to check whether the returned ImagePlus equals the given
 *   ImagePlus before performing unwanted destructive changes. If a new
 *   ImagePlus is created it will be initially hidden.
 *
 *   \param imp The ImagePlus to convert to unet model compliant format
 *   \param job The unet job asking for the resize operation. Its model
 *     will be queried for the required output format. Task progress will
 *     be reported via the Job.
 *
 *   \exception InterruptedException The user can abort this operation
 *     resulting in this error
 *
 *   \return The unet compatible ImagePlus correponding to the input
 *     ImagePlus. If the input was already compatible a reference to
 *     the input is returned, otherwise a new ImagePlus created.
 */
/*======================================================================*/
  public static ImagePlus convertToUnetFormat(
      ImagePlus imp, Job job, boolean keepOriginal, boolean show)
      throws InterruptedException {
    ImagePlus out = normalizeValues(
        rescaleZ(
            rescaleXY(
                fixStackLayout(
                    convertToFloat(makeComposite(imp, job), job), job),
                ImageProcessor.BILINEAR, job),
            ImageProcessor.BILINEAR, job),
        job);

    if (imp != out) {
      if (!keepOriginal) {
        imp.changes = false;
        imp.close();
      }
      if (show) {
        out.resetDisplayRange();
        out.show();
      }
    }
    return out;
  }

/*======================================================================*/
/*!
 *   Extracts annotations stored as overlay from the given ImagePlus and
 *   generates corresponding labels and weights. Annotation names indicate
 *   class labels if at least two names are identical. If ROI names contain
 *   ignore, they will be treated as ignore regions. Point ROIs will be
 *   treated as detection ROIs and instead of single points small disks are
 *   rendered as labels. Finetuning only works for 2D models at the moment.
 *
 *   \param imp The ImagePlus to generate labels and weights for
 *   \param job Task progress will be reported via this Job.
 *
 *   \exception InterruptedException The user can quit this operation
 *     resulting in this error
 *
 *   \return Three ImagePlus objects, the first containing the labels,
 *     the second the weights and the third the pdf to sample training
 *     examples from (Input to CreateDeformationLayer).
 */
/*======================================================================*/
  public static ImagePlus[] convertAnnotationsToLabelsAndWeights(
      ImagePlus imp, Vector<String> classes, Job job)
      throws InterruptedException, NotImplementedException {

    int nDims = job.model().nDims();
    if (nDims != 2) throw new NotImplementedException(
        "Sorry, " + nDims + "D finetuning is not implemented yet");

    float foregroundBackgroundRatio = job.model().foregroundBackgroundRatio;
    float sigma1_px = job.model().sigma1Um / job.model().elementSizeUm[1];
    float borderWeightFactor = job.model().borderWeightFactor;
    float sigma2_px = job.model().borderWeightSigmaUm /
        job.model().elementSizeUm[1];
    double diskRadiusXPx = 2.0 * job.model().elementSizeUm[nDims - 1] /
        imp.getCalibration().pixelWidth;
    double diskRadiusYPx = 2.0 * job.model().elementSizeUm[nDims - 2] /
        imp.getCalibration().pixelHeight;

    ImagePlus[] blobs = new ImagePlus[3];

    // Fix layout of input ImagePlus and get dimensions
    blobs[2] = fixStackLayout(imp, job);
    int T = blobs[2].getNFrames();
    int Z = blobs[2].getNSlices();
    int W = blobs[2].getWidth();
    int H = blobs[2].getHeight();

    // Create output blobs
    blobs[0] = IJ.createHyperStack(
        imp.getTitle() + " - labels", W, H, 1, Z, T, 32);
    blobs[0].setCalibration(imp.getCalibration().copy());
    blobs[1] = IJ.createHyperStack(
        imp.getTitle() + " - weights", W, H, 1, Z, T, 32);
    blobs[1].setCalibration(imp.getCalibration().copy());
    blobs[2] = IJ.createHyperStack(
        imp.getTitle() + " - sample pdf", W, H, 1, Z, T, 32);
    blobs[2].setCalibration(imp.getCalibration().copy());
    ImagePlus instanceLabels = IJ.createHyperStack(
        imp.getTitle() + " - instance labels", W, H, 1, Z, T, 32);
    instanceLabels.setCalibration(imp.getCalibration().copy());

    // Initialize output blobs
    // Initialize the weights blob with -1, which indicates that no weight
    // is assigned yet. When computing extra weights all regions with positive
    // weight will be skipped!
    for (int t = 1; t <= T; t++) {
      for (int z = 1; z <= Z; z++) {
        int stackIdx = blobs[0].getStackIndex(1, z, t);
        ImageProcessor ip = blobs[0].getStack().getProcessor(stackIdx);
        ip.setValue(0.0);
        ip.fill();
        ip = blobs[1].getStack().getProcessor(stackIdx);
        ip.setValue(-1.0);
        ip.fill();
        ip = blobs[2].getStack().getProcessor(stackIdx);
        ip.setValue(foregroundBackgroundRatio);
        ip.fill();
        ip = instanceLabels.getStack().getProcessor(stackIdx);
        ip.setValue(0.0);
        ip.fill();
      }
    }

    Roi[] rois = imp.getOverlay().toArray();
    int[] nObjects = new int[T];
    int nObjectsTotal = 0;
    Arrays.fill(nObjects, 0);

    // First pass: Set ignore disks for point ROIs
    for (Roi roi: rois) {
      if (job.interrupted())
          throw new InterruptedException("Aborted by user");

      if (!(roi instanceof PointRoi)) continue;
      int z = roi.getZPosition();
      int t = roi.getTPosition();
      int stackIndex = blobs[0].getStackIndex(1, z + 1, t + 1);
      ImageProcessor ipWeights = blobs[1].getStack().getProcessor(stackIndex);
      ImageProcessor ipPdf = blobs[2].getStack().getProcessor(stackIndex);
      for (Point point: roi.getContainedPoints()) {
        OvalRoi disk = new OvalRoi(
            point.x - 2 * diskRadiusXPx, point.y - 2 * diskRadiusYPx,
            4 * diskRadiusXPx + 1, 4 * diskRadiusYPx + 1);
        ipWeights.setValue(0.0);
        ipWeights.fill(disk);
        ipPdf.setValue(0.0);
        ipPdf.fill(disk);
      }
    }

    // Second pass: Create labels, instancelabels, weights and pdf
    for (Roi roi: rois) {

      if (job.interrupted())
          throw new InterruptedException("Aborted by user");

      int z = roi.getZPosition();
      int t = roi.getTPosition();
      int stackIndex = blobs[0].getStackIndex(1, z + 1, t + 1);
      ImageProcessor ipLabels = blobs[0].getStack().getProcessor(stackIndex);
      ImageProcessor ipInstanceLabels =
          instanceLabels.getStack().getProcessor(stackIndex);
      ImageProcessor ipWeights = blobs[1].getStack().getProcessor(stackIndex);
      ImageProcessor ipPdf = blobs[2].getStack().getProcessor(stackIndex);
      if (roi.getName() != null &&
          roi.getName().toLowerCase(Locale.ROOT).contains("ignore")) {
        ipWeights.setValue(0.0);
        ipWeights.fill(roi);
        ipPdf.setValue(0.0);
        ipPdf.fill(roi);
      }
      else {
        String label = (roi.getName() != null) ? roi.getName() : "foreground";
        int labelIdx = 1;
        if (classes != null) {
          for (; labelIdx < classes.size() &&
                   !classes.get(labelIdx).equals(label); labelIdx++);
        }
        ipLabels.setValue(labelIdx);

        if (roi instanceof PointRoi) {
          for (Point point: roi.getContainedPoints()) {
            OvalRoi disk = new OvalRoi(
                point.x - diskRadiusXPx, point.y - diskRadiusYPx,
                2 * diskRadiusXPx + 1, 2 * diskRadiusYPx + 1);
            nObjects[t]++;
            nObjectsTotal++;
            ipInstanceLabels.setValue(nObjects[t]);
            ipInstanceLabels.fill(disk);
            ipLabels.fill(disk);
            ipWeights.setValue(1.0);
            ipWeights.fill(disk);
            ipPdf.setValue(1.0);
            ipPdf.fill(disk);
          }
        }
        else {
          nObjects[t]++;
          nObjectsTotal++;
          ipInstanceLabels.setValue(nObjects[t]);
          ipInstanceLabels.fill(roi);
          ipLabels.fill(roi);
          ipWeights.setValue(1.0);
          ipWeights.fill(roi);
          ipPdf.setValue(1.0);
          ipPdf.fill(roi);
        }
      }
    }

    blobs[0] = rescaleZ(
        rescaleXY(blobs[0], ImageProcessor.NEAREST_NEIGHBOR, job),
        ImageProcessor.NEAREST_NEIGHBOR, job);
    blobs[1] = rescaleZ(
        rescaleXY(blobs[1], ImageProcessor.NEAREST_NEIGHBOR, job),
        ImageProcessor.NEAREST_NEIGHBOR, job);
    blobs[2] = rescaleZ(
        rescaleXY(blobs[2], ImageProcessor.NEAREST_NEIGHBOR, job),
        ImageProcessor.NEAREST_NEIGHBOR, job);
    instanceLabels = rescaleZ(
        rescaleXY(instanceLabels, ImageProcessor.NEAREST_NEIGHBOR, job),
        ImageProcessor.NEAREST_NEIGHBOR, job);
    Z = blobs[0].getNSlices();
    W = blobs[0].getWidth();
    H = blobs[0].getHeight();

    // Here labels and instance labels are not separated by background,
    // weights are -1 for background pixels, sample pdf is ready except for
    // gaps between cells

    int objIdx = 1;

    job.progressMonitor().init(0, "", "", nObjectsTotal);

    if (nDims == 2) {

      // Piece of cake, just process slicewise :-D

      for (int t = 1; t <= T; ++t) {

        ImageProcessor ip, wp, w2p, lp;
        if (blobs[0].getStackSize() == 1) {
          lp = blobs[0].getProcessor();
          wp = blobs[1].getProcessor();
          w2p = blobs[2].getProcessor();
          ip = instanceLabels.getProcessor();
        }
        else {
          lp = blobs[0].getStack().getProcessor(t);
          wp = blobs[1].getStack().getProcessor(t);
          w2p = blobs[2].getStack().getProcessor(t);
          ip = instanceLabels.getStack().getProcessor(t);
        }

        // Create background ridges between touching instances
        for (int y = 0; y < H; y++) {
          for (int x = 0; x < W; x++) {
            float instanceLabel = ip.getf(x, y);
            if (instanceLabel == 0 || wp.getf(x, y) == 0.0f) continue;
            float classLabel = lp.getf(x, y);
            for (int dy = -1; dy <= 0 && instanceLabel != 0; dy++) {
              if (y + dy < 0 || y + dy >= H) continue;
              for (int dx = -1; dx <= 1 && instanceLabel != 0; dx++) {
                if ((dy == 0 && dx >= 0) || x + dx < 0 || x + dx >= W)
                    continue;
                float nbInstanceLabel = ip.getf(x + dx, y + dy);
                float nbClassLabel = lp.getf(x + dx, y + dy);
                if (nbInstanceLabel == 0 ||
                    nbInstanceLabel == instanceLabel ||
                    classLabel != nbClassLabel) continue;
                instanceLabel = 0;
                ip.setf(x, y, instanceLabel);
                lp.setf(x, y, 0);
                // Mark as "pixel needs extraweight computation"
                wp.setf(x, y, -1.0f);
                w2p.setf(x, y, foregroundBackgroundRatio);
              }
            }
          }
        }

        // Compute extra weights
        ImageProcessor impMin1Dist = ip.duplicate();
        impMin1Dist.setValue(DistanceTransform.BG_VALUE);
        impMin1Dist.fill();
        float[] min1Dist = (float[])impMin1Dist.getPixels();
        ImageProcessor impMin2Dist = ip.duplicate();
        impMin2Dist.setValue(DistanceTransform.BG_VALUE);
        impMin2Dist.fill();
        float[] min2Dist = (float[])impMin2Dist.getPixels();
        for (int i = 1; i <= nObjects[t - 1]; i++, objIdx++) {
          if (job.interrupted())
              throw new InterruptedException("Aborted by user");
          job.progressMonitor().count(
              "Processing slice " + t + " / " + T + ": object " + i +
              " / " + nObjects[t], 1);
          float[] d = (float[])DistanceTransform.getDistanceToForegroundPixels(
              (FloatProcessor)ip.duplicate(), i).getPixels();
          for (int j = 0; j < H * W; j++) {
            float min1dist = min1Dist[j];
            float min2dist = Math.min(min2Dist[j], d[j]);
            min1Dist[j] = Math.min(min1dist, min2dist);
            min2Dist[j] = Math.max(min1dist, min2dist);
          }
        }

        float va = 1 - foregroundBackgroundRatio;
        float[] w = (float[])wp.getPixels();
        for (int i = 0; i < W * H; ++i) {
          if (w[i] >= 0.0f) continue;
          float d1 = min1Dist[i];
          float d2 = min2Dist[i];
          float wa = (float)Math.exp(
              -(d1 * d1) / (2 * sigma1_px * sigma1_px));
          float we = (float)Math.exp(
              -(d1 + d2) * (d1 + d2) / (2 * sigma2_px * sigma2_px));
          w[i] = borderWeightFactor * we + va * wa +
              foregroundBackgroundRatio;
        }
      }
    }
    else {

      // Uh, real 3D data... what a mess :-(

      throw new NotImplementedException(
          "Sorry, 3D finetuning is not implemented yet");
    }

    return blobs;
  }

  private static void save2DBlob(
      ImagePlus imp, IHDF5Writer writer, String dsName, Job job)
        throws InterruptedException {
    int T = imp.getNFrames();
    int Z = imp.getNSlices();
    int N = T * Z;
    int C = imp.getNChannels();
    int W = imp.getWidth();
    int H = imp.getHeight();
    long[] dims = { N, C, H, W };
    int[] blockDims = { 1, 1, H, W };
    long[] blockIdx = { 0, 0, 0, 0 };

    job.progressMonitor().init(0, "", "", imp.getImageStackSize());

    writer.float32().createMDArray(
        dsName, dims, blockDims, HDF5FloatStorageFeatures.createDeflation(3));

    for (int t = 0; t < T; ++t) {
      for (int z = 0; z < Z; ++z) {
        blockIdx[0] = t * Z + z;
        for (int c = 0; c < C; ++c) {
          if (job.interrupted())
              throw new InterruptedException("Aborted by user");
          job.progressMonitor().count(
              "Saving " + dsName + " t=" + t + ", z=" + z + ", c=" + c, 1);

           blockIdx[1] = c;

          // Create HDF5 Multi-dimensional Array (memory space)
          MDFloatArray data = new MDFloatArray(blockDims);
          float[] dataFlat = data.getAsFlatArray();

          // Get IJ index to processed slice
          ImageStack stack = imp.getStack();
          int stackIndex = imp.getStackIndex(c + 1, z + 1, t + 1);

          System.arraycopy(stack.getPixels(stackIndex), 0, dataFlat, 0, H * W);

         // save it
          writer.float32().writeMDArrayBlock(dsName, data, blockIdx);
        }
      }
    }
  }

  private static void save3DBlob(
      ImagePlus imp, IHDF5Writer writer, String dsName, Job job)
        throws InterruptedException {
    int T = imp.getNFrames();
    int Z = imp.getNSlices();
    int C = imp.getNChannels();
    int W = imp.getWidth();
    int H = imp.getHeight();
    long[] dims = { T, C, Z, H, W };
    int[] blockDims = { 1, 1, 1, H, W };
    long[] blockIdx = { 0, 0, 0, 0, 0 };

    job.progressMonitor().init(0, "", "", imp.getImageStackSize());

    writer.float32().createMDArray(
        dsName, dims, blockDims, HDF5FloatStorageFeatures.createDeflation(3));

    for (int t = 0; t < T; ++t) {
      blockIdx[0] = t;
      for (int z = 0; z < Z; ++z) {
        blockIdx[2] = z;
        for (int c = 0; c < C; ++c) {
          blockIdx[1] = c;
          if (job.interrupted())
              throw new InterruptedException("Aborted by user");

          // Create HDF5 Multi-dimensional Array (memory space)
          MDFloatArray data = new MDFloatArray(blockDims);
          float[] dataFlat = data.getAsFlatArray();

          // Get IJ index to processed slice
          ImageStack stack = imp.getStack();
          int stackIndex = imp.getStackIndex(c + 1, z + 1, t + 1);

          System.arraycopy(stack.getPixels(stackIndex), 0, dataFlat, 0, H * W);

          job.progressMonitor().count(
              "Saving t=" + t + ", z=" + z + ", c=" + c, 1);

          // save it
          writer.float32().writeMDArrayBlock(dsName, data, blockIdx);
        }
      }
    }
  }

  public static ImagePlus saveHDF5Blob(
      ImagePlus imp, File outFile, Job job, boolean keepOriginal,
      boolean show)
      throws IOException, InterruptedException, NotImplementedException {
    return saveHDF5Blob(imp, null, outFile, job, keepOriginal, show);
  }

  public static ImagePlus saveHDF5Blob(
      ImagePlus imp, Vector<String> classes, File outFile, Job job,
      boolean keepOriginal, boolean show)
      throws IOException, InterruptedException, NotImplementedException {
    if (job == null) {
      IJ.error("Cannot save HDF5 blob without associated unet model");
      throw new InterruptedException("No active unet job");
    }

    String dsName = job.model().inputDatasetName;

    IJ.log("  Processing '" + imp.getTitle() + "': " +
           "#frames = " + imp.getNFrames() +
           ", #slices = " + imp.getNSlices() +
           ", #channels = " + imp.getNChannels() +
           ", height = " + imp.getHeight() +
           ", width = " + imp.getWidth() +
           " with element size = [" +
           imp.getCalibration().pixelDepth + ", " +
           imp.getCalibration().pixelHeight + ", " +
           imp.getCalibration().pixelWidth + "]");

    ImagePlus impScaled = convertToUnetFormat(imp, job, keepOriginal, show);

    // Recursively create parent folders
    Vector<File> createdFolders = new Vector<File>();
    File folder = outFile.getParentFile();
    while (folder != null && !folder.isDirectory()) {
      createdFolders.add(folder);
      folder = folder.getParentFile();
    }
    for (int i = createdFolders.size() - 1; i >= 0; --i) {
      if (job.interrupted())
          throw new InterruptedException("Aborted by user");
      job.progressMonitor().count(
          "Creating folder '" + createdFolders.get(i) + "'", 0);
      IJ.log("$ mkdir \"" + createdFolders.get(i) + "\"");
      if (!createdFolders.get(i).mkdir())
          throw new IOException(
              "Could not create folder '" +
              createdFolders.get(i).getAbsolutePath() + "'");
      createdFolders.get(i).deleteOnExit();
    }

    // syncMode: Always wait on close and flush till all data is written!
    // useSimpleDataSpace: Save attributes as plain vectors
    IHDF5Writer writer =
        HDF5Factory.configure(outFile.getAbsolutePath()).syncMode(
            IHDF5WriterConfigurator.SyncMode.SYNC_BLOCK)
        .useSimpleDataSpaceForAttributes().overwrite().writer();
    outFile.deleteOnExit();

    if (job.model().elementSizeUm.length == 2)
        save2DBlob(impScaled, writer, dsName, job);
    else save3DBlob(impScaled, writer, dsName, job);

    if (job instanceof FinetuneJob && imp.getOverlay() != null) {
      ImagePlus[] blobs =
          convertAnnotationsToLabelsAndWeights(imp, classes, job);

      if (job.model().elementSizeUm.length == 2) {
        save2DBlob(blobs[0], writer, "labels", job);
        save2DBlob(blobs[1], writer, "weights", job);
        save2DBlob(blobs[2], writer, "weights2", job);
      }
      else {
        save3DBlob(blobs[0], writer, "labels", job);
        save3DBlob(blobs[1], writer, "weights", job);
        save3DBlob(blobs[2], writer, "weights2", job);
      }
    }

    writer.file().close();
    IJ.log("ImagePlus converted to caffe blob and saved to '" +
           outFile.getAbsolutePath() + "'");

    return impScaled;
  }

  public static File[] saveHDF5TiledBlob(
      ImagePlus imp, Vector<String> classes, String fileNameStub, Job job)
      throws IOException, InterruptedException, NotImplementedException {
    if (job == null) {
      IJ.error("Cannot save HDF5 blob without associated unet model");
      throw new InterruptedException("No active unet job");
    }

    String dsName = job.model().inputBlobName;

    IJ.log("  Processing '" + imp.getTitle() + "': " +
           "#frames = " + imp.getNFrames() + ", #slices = " + imp.getNSlices() +
           ", #channels = " + imp.getNChannels() +
           ", height = " + imp.getHeight() +
           ", width = " + imp.getWidth() + " with element size = [" +
           imp.getCalibration().pixelDepth + ", " +
           imp.getCalibration().pixelHeight + ", " +
           imp.getCalibration().pixelWidth + "]");

    ImagePlus data = convertToUnetFormat(imp, job, true, false);
    ImagePlus[] annotations = null;
    if (job instanceof FinetuneJob && imp.getOverlay() != null)
        annotations = convertAnnotationsToLabelsAndWeights(imp, classes, job);

    int T = data.getNFrames();
    int Z = data.getNSlices();
    int C = data.getNChannels();
    int W = data.getWidth();
    int H = data.getHeight();

    int[] inShape = job.model().getTileShape();
    int[] outShape = job.model().getOutputTileShape(inShape);
    int[] tileOffset = new int[inShape.length];
    for (int d = 0; d < tileOffset.length; d++)
        tileOffset[d] = (inShape[d] - outShape[d]) / 2;
    int[] tiling = new int[outShape.length];
    int nTiles = 1;
    if (outShape.length == 2) {
      tiling[0] = (int)(
          Math.ceil((double)data.getHeight() / (double)outShape[0]));
      tiling[1] = (int)(
          Math.ceil((double)data.getWidth() / (double)outShape[1]));
      IJ.log("  tiling = " + tiling[0] + "x" + tiling[1]);
    }
    else {
      tiling[0] = (int)(
          Math.ceil((double)data.getNSlices() / (double)outShape[0]));
      tiling[1] = (int)(
          Math.ceil((double)data.getHeight() / (double)outShape[1]));
      tiling[2] = (int)(
          Math.ceil((double)data.getWidth() / (double)outShape[2]));
      IJ.log("  tiling = " + tiling[0] + "x" + tiling[1] + "x" + tiling[2]);
    }
    for (int d = 0; d < outShape.length; d++) nTiles *= tiling[d];
    IJ.log("  nTiles = " + nTiles);

    File[] outfiles = new File[nTiles];

    // Recursively create parent folders
    if (fileNameStub != null) {
      File outFile = new File(fileNameStub);
      Vector<File> createdFolders = new Vector<File>();
      File folder = outFile.getParentFile();
      while (folder != null && !folder.isDirectory()) {
        createdFolders.add(folder);
        folder = folder.getParentFile();
      }
      for (int i = createdFolders.size() - 1; i >= 0; --i) {
        if (job.interrupted())
            throw new InterruptedException("Aborted by user");
        job.progressMonitor().count(
            "Creating folder '" + createdFolders.get(i) + "'", 0);
        IJ.log("$ mkdir \"" + createdFolders.get(i) + "\"");
        if (!createdFolders.get(i).mkdir())
            throw new IOException(
                "Could not create folder '" +
                createdFolders.get(i).getAbsolutePath() + "'");
        createdFolders.get(i).deleteOnExit();
      }
    }

    // Create output ImagePlus
    ImagePlus dataTile = IJ.createHyperStack(
        imp.getTitle() + " - dataTile", inShape[1], inShape[0], C, Z, T, 32);
    ImagePlus labelsTile = IJ.createHyperStack(
        imp.getTitle() + " - labelsTile",
        outShape[1], outShape[0], 1, Z, T, 32);
    ImagePlus weightsTile = IJ.createHyperStack(
        imp.getTitle() + " - weightsTile",
        outShape[1], outShape[0], 1, Z, T, 32);

    job.progressMonitor().init(0, "", "", nTiles);

    int tileIdx = 0;
    if (outShape.length == 2) {
      for (int yIdx = 0; yIdx < tiling[0]; yIdx++) {
        for (int xIdx = 0; xIdx < tiling[1]; xIdx++, tileIdx++) {

          job.progressMonitor().count(
              "Processing tile (" + yIdx + "," + xIdx + ")", 1);

          for (int t = 0; t < T; t++) {

            // Copy data tile for sample t
            for (int c = 0; c < C; c++) {
              int stackIdx = data.getStackIndex(c, 1, t);
              ImageProcessor ipDataIn =
                  data.getStack().getProcessor(stackIdx);
              ImageProcessor ipDataOut =
                  dataTile.getStack().getProcessor(stackIdx);
              for (int y = 0; y < inShape[0]; y++) {
                int yR = yIdx * outShape[0] - tileOffset[0] + y;
                if (yR < 0) yR = -yR;
                int n = yR / (H - 1);
                yR = (n % 2 == 0) ? (yR - n * (H - 1)) :
                    ((n + 1) * (H - 1) - yR);
                for (int x = 0; x < inShape[1]; x++) {
                  int xR = xIdx * outShape[1] - tileOffset[1] + x;
                  if (xR < 0) xR = -xR;
                  n = xR / (W - 1);
                  xR = (n % 2 == 0) ? (xR - n * (W - 1)) :
                      ((n + 1) * (W - 1) - xR);
                  ipDataOut.setf(x, y, ipDataIn.getf(xR, yR));
                }
              }
            }

            // Copy labels and weights tiles for sample t
            int stackIdx = annotations[0].getStackIndex(1, 1, t);
            ImageProcessor ipLabelsIn =
                annotations[0].getStack().getProcessor(stackIdx);
            ImageProcessor ipLabelsOut =
                labelsTile.getStack().getProcessor(stackIdx);
            ImageProcessor ipWeightsIn =
                annotations[1].getStack().getProcessor(stackIdx);
            ImageProcessor ipWeightsOut =
                weightsTile.getStack().getProcessor(stackIdx);
            for (int y = 0; y < outShape[0]; y++) {
              int yR = yIdx * outShape[0] + y;
              int n = yR / (H - 1);
              yR = (n % 2 == 0) ? (yR - n * (H - 1)) :
                  ((n + 1) * (H - 1) - yR);
              for (int x = 0; x < outShape[1]; x++) {
                int xR = xIdx * outShape[1] + x;
                n = xR / (W - 1);
                xR = (n % 2 == 0) ? (xR - n * (W - 1)) :
                    ((n + 1) * (W - 1) - xR);
                ipLabelsOut.setf(x, y, ipLabelsIn.getf(xR, yR));
                ipWeightsOut.setf(
                    x, y, (xR == xIdx * outShape[1] + x &&
                           yR == yIdx * outShape[0] + y) ?
                    ipWeightsIn.getf(xR, yR) : 0.0f);
              }
            }
          }
          if (job.interrupted()) throw new InterruptedException();

          // Save tile
          File outFile = null;
          if (fileNameStub == null) {
            outFile = File.createTempFile(job.id(), ".h5");
            outFile.delete();
          }
          else {
            outFile = new File(fileNameStub + "_" + tileIdx + ".h5");
          }
          IHDF5Writer writer =
              HDF5Factory.configure(outFile.getAbsolutePath()).syncMode(
                  IHDF5WriterConfigurator.SyncMode.SYNC_BLOCK)
              .useSimpleDataSpaceForAttributes().overwrite().writer();
          outFile.deleteOnExit();

          save2DBlob(dataTile, writer, dsName, job);
          save2DBlob(labelsTile, writer, "/labels", job);
          save2DBlob(weightsTile, writer, "/weights", job);
          writer.file().close();

          outfiles[tileIdx] = outFile;

          if (job.interrupted()) throw new InterruptedException();
        }
      }
    }
    else {
      throw new NotImplementedException("3D not yet implemented");
    }

    return outfiles;
  }

  public static ProcessResult execute(Vector<String> command, Job job)
      throws IOException, InterruptedException {
    if (command == null || command.size() == 0)
        throw new IOException("Tools.execute() Received empty command");
    String cmdString = command.get(0);
    for (int i = 1; i < command.size(); ++i)
        cmdString += " " + command.get(i);
    job.progressMonitor().count("Executing '" + cmdString + "'", 0);
    IJ.log("$ " + cmdString);

    Process p = new ProcessBuilder(command).start();

    BufferedReader stdOutput =
        new BufferedReader(new InputStreamReader(p.getInputStream()));
    BufferedReader stdError =
        new BufferedReader(new InputStreamReader(p.getErrorStream()));

    ProcessResult res = new ProcessResult();

    while (true) {
      try {
        // Read output on the fly to avoid BufferedReader overflow
        while (stdOutput.ready())
            res.cout += stdOutput.readLine() + "\n";
        while (stdError.ready()) res.cerr += stdError.readLine() + "\n";

        res.exitStatus = p.exitValue();

        // Read residual output after process finished
        while (stdOutput.ready())
            res.cout += stdOutput.readLine() + "\n";
        while (stdError.ready()) res.cerr += stdError.readLine() + "\n";
        return res;
      }
      catch (IllegalThreadStateException e) {}
      Thread.sleep(100);
    }
  }

  public static ProcessResult execute(
      String command, Session session, Job job)
      throws JSchException, InterruptedException, IOException {
    job.progressMonitor().count(
        "Executing remote command '" + command + "'", 0);
    IJ.log(session.getUserName() + "@" + session.getHost() +
           "$ " + command);

    Channel channel = session.openChannel("exec");
    ((ChannelExec)channel).setCommand(command);

    InputStream stdOutput = channel.getInputStream();
    InputStream stdError = ((ChannelExec)channel).getErrStream();

    byte[] buf = new byte[1024];

    ProcessResult res = new ProcessResult();

    channel.connect();
    while (true) {
      while(stdOutput.available() > 0) {
        int i = stdOutput.read(buf, 0, 1024);
        if (i < 0) break;
        res.cout += new String(buf, 0, i);
      }
      while(stdError.available() > 0) {
        int i = stdError.read(buf, 0, 1024);
        if (i < 0) break;
        res.cerr += new String(buf, 0, i);
      }
      if (channel.isClosed()) {
        if (stdOutput.available() > 0 || stdError.available() > 0) continue;
        res.exitStatus = channel.getExitStatus();
        return res;
      }
      Thread.sleep(100);
    }
  }

  public static void loadSegmentationToImagePlus(
      File file, SegmentationJob job, boolean outputScores,
      boolean outputSoftmaxScores)
      throws HDF5Exception, IOException {
    loadSegmentationToImagePlus(
        file, job, outputScores, outputSoftmaxScores, false);
  }

  public static void loadSegmentationToImagePlus(
      File file, SegmentationJob job, boolean outputScores,
      boolean outputSoftmaxScores, boolean generateMarkers)
      throws HDF5Exception, IOException {

    job.progressMonitor().reset();
    job.progressMonitor().count("Creating visualization", 0);

    boolean computeSoftmaxScores = outputSoftmaxScores || generateMarkers;

    IHDF5Reader reader =
        HDF5Factory.configureForReading(file.getAbsolutePath()).reader();
    List<String> outputs = reader.getGroupMembers("/");

    int dsIdx = 1;
    for (String dsName : outputs) {

      String title = job.imageName() + " - " + dsName;
      HDF5DataSetInformation dsInfo =
          reader.object().getDataSetInformation(dsName);
      int nDims    = dsInfo.getDimensions().length - 2;
      int nFrames  = (int)dsInfo.getDimensions()[0];
      int nClasses = (int)dsInfo.getDimensions()[1];
      int nLevs    = (nDims == 2) ? 1 : (int)dsInfo.getDimensions()[2];
      int nRows    = (int)dsInfo.getDimensions()[2 + ((nDims == 2) ? 0 : 1)];
      int nCols    = (int)dsInfo.getDimensions()[3 + ((nDims == 2) ? 0 : 1)];

      ImagePlus impScores = null;
      if (outputScores) {
        impScores = IJ.createHyperStack(
            title, nCols, nRows, nClasses, nLevs, nFrames, 32);
        impScores.setDisplayMode(IJ.GRAYSCALE);
        impScores.setCalibration(job.imageCalibration().copy());
      }

      ImagePlus impSoftmaxScores = null;
      if (computeSoftmaxScores) {
        impSoftmaxScores = IJ.createHyperStack(
            title + " (softmax)", nCols, nRows, nClasses, nLevs, nFrames, 32);
        impSoftmaxScores.setDisplayMode(IJ.GRAYSCALE);
        impSoftmaxScores.setCalibration(job.imageCalibration().copy());
      }

      ImagePlus impClassification = IJ.createHyperStack(
          title + " (segmentation)", nCols, nRows, 1, nLevs, nFrames, 16);
      impClassification.setDisplayMode(IJ.GRAYSCALE);
      impClassification.setCalibration(job.imageCalibration().copy());

      int[] blockDims = (nDims == 2) ?
          (new int[] { 1, 1, nRows, nCols }) :
          (new int[] { 1, 1, 1, nRows, nCols });
      long[] blockIdx = (nDims == 2) ?
          (new long[] { 0, 0, 0, 0 }) : (new long[] { 0, 0, 0, 0, 0 });

      int nOperations = nFrames * nLevs * nClasses;
      if (generateMarkers) nOperations += 4 * nFrames * nLevs * (nClasses - 1);
      job.progressMonitor().initNewTask(
          "Creating visualization for " + dsName,
          (float)dsIdx / (float)outputs.size(), nOperations);
      dsIdx++;

      for (int t = 0; t < nFrames; ++t) {
        blockIdx[0] = t;
        for (int z = 0; z < nLevs; ++z) {
          if (nDims == 3) blockIdx[2] = z;
          float[] maxScore = new float[nRows * nCols];
          float[] expScoreSum =
              computeSoftmaxScores ? new float[nRows * nCols] : null;
          ImageProcessor ipC = impClassification.getStack().getProcessor(
              impClassification.getStackIndex(1, z + 1, t + 1));
          short[] maxIndex = (short[])ipC.getPixels();
          int c = 0;
          blockIdx[1] = c;
          job.progressMonitor().count(
              "Classification t=" + (t + 1) + "/" + nFrames +
              ", z=" + (z + 1) + "/" + nLevs + ", class=" + c + "/" +
              (nClasses - 1), 1);
          float[] score = reader.float32().readMDArrayBlock(
              dsName, blockDims, blockIdx).getAsFlatArray();
          if (outputScores)
              System.arraycopy(
                  score, 0,
                  impScores.getStack().getProcessor(
                      impScores.getStackIndex(c + 1, z + 1, t + 1)).getPixels(),
                  0, nRows * nCols);
          if (computeSoftmaxScores) {
            float[] smscores =
                (float[])impSoftmaxScores.getStack().getProcessor(
                    impSoftmaxScores.getStackIndex(
                        c + 1, z + 1, t + 1)).getPixels();
            for (int i = 0; i < nRows * nCols; ++i) {
              smscores[i] = (float)Math.exp((double)score[i]);
              expScoreSum[i] = smscores[i];
            }
          }
          System.arraycopy(score, 0, maxScore, 0, nRows * nCols);
          Arrays.fill(maxIndex, (short) c);
          ++c;
          for (; c < nClasses; ++c) {
            blockIdx[1] = c;
            job.progressMonitor().count(
                "Classification t=" + (t + 1) + "/" + nFrames +
                ", z=" + (z + 1) + "/" + nLevs + ", class=" + c + "/" +
                (nClasses - 1), 1);
            score = reader.float32().readMDArrayBlock(
                dsName, blockDims, blockIdx).getAsFlatArray();
            if (outputScores)
                System.arraycopy(
                    score, 0, impScores.getStack().getProcessor(
                        impScores.getStackIndex(
                            c + 1, z + 1, t + 1)).getPixels(),
                    0, nRows * nCols);
            if (computeSoftmaxScores) {
              float[] smscores =
                  (float[])impSoftmaxScores.getStack().getProcessor(
                  impSoftmaxScores.getStackIndex(
                      c + 1, z + 1, t + 1)).getPixels();
              for (int i = 0; i < nRows * nCols; ++i) {
                smscores[i] = (float)Math.exp((double)score[i]);
                expScoreSum[i] += smscores[i];
              }
            }
            for (int i = 0; i < nRows * nCols; ++i) {
              if (score[i] > maxScore[i]) {
                maxScore[i] = score[i];
                maxIndex[i] = (short) c;
              }
            }
          }
          if (computeSoftmaxScores) {
            for (c = 0; c < nClasses; ++c) {
              float[] smscores =
                  (float[])impSoftmaxScores.getStack().getProcessor(
                      impSoftmaxScores.getStackIndex(
                          c + 1, z + 1, t + 1)).getPixels();
              for (int i = 0; i < nRows * nCols; ++i)
                  smscores[i] /= expScoreSum[i];
            }
          }
        }
      }

      if (outputScores) {
        for (int i = 0; i < impScores.getStackSize(); ++i) {
          impScores.setSlice(i + 1);
          impScores.resetDisplayRange();
        }
        impScores.setSlice(1);
        impScores.show();
      }
      if (outputSoftmaxScores) {
        for (int i = 0; i < impSoftmaxScores.getStackSize(); ++i) {
          impSoftmaxScores.setSlice(i + 1);
          impSoftmaxScores.setDisplayRange(0.0, 1.0);
        }
        impSoftmaxScores.setSlice(1);
        impSoftmaxScores.show();
      }
      if (impClassification.getStackSize() > 1) {
        for (int i = 0; i < impClassification.getStackSize(); ++i) {
          impClassification.setSlice(i + 1);
          impClassification.resetDisplayRange();
        }
        impClassification.setSlice(1);
      }
      else impClassification.resetDisplayRange();
      impClassification.show();

      if (generateMarkers) {

        // Create multi-channel image of per class binary masks
        ImagePlus impMCClassification = IJ.createHyperStack(
            title + " (classes)", nCols, nRows, nClasses - 1,
            nLevs, nFrames, 8);
        impMCClassification.setCalibration(job.imageCalibration().copy());

        for (int t = 0; t < nFrames; ++t) {
          for (int z = 0; z < nLevs; ++z) {
            short[] cl = (short[])impClassification.getStack().getProcessor(
                impClassification.getStackIndex(1, z + 1, t + 1)).getPixels();
            byte[][] mccl = new byte[nClasses - 1][];
            for (int c = 0; c < nClasses - 1; ++c)
            {
              job.progressMonitor().count(
                  "Generating masks t=" + (t + 1) + "/" + nFrames +
                  ", z=" + (z + 1) + "/" + nLevs + ", class=" + (c + 1) + "/" +
                  (nClasses - 1), 1);
              ImageProcessor impMccl =
                  impMCClassification.getStack().getProcessor(
                      impMCClassification.getStackIndex(c + 1, z + 1, t + 1));
              impMccl.setValue(0);
              impMccl.fill();
              mccl[c] = (byte[])impMccl.getPixels();
            }
            for (int i = 0; i < nRows * nCols; ++i)
                if (cl[i] != 0) mccl[cl[i] - 1][i] = (byte)255;
          }
        }

        // Connected component labeling
        job.progressMonitor().count("Connected component labeling", 0);
        Pair< Integer[], Blob<Integer> > connComps =
            ConnectedComponentLabeling.label(
                impMCClassification,
                ConnectedComponentLabeling.SIMPLE_NEIGHBORHOOD,
                job.progressMonitor());

        // Compute centers of mass of connected components
        float[][][] centerPosUm = new float[nFrames * (nClasses - 1)][][];
        float[][] weightSum = new float[nFrames * (nClasses - 1)][];
        int[][] nPixels = new int[nFrames * (nClasses - 1)][];
        for (int i = 0; i < nFrames * (nClasses - 1); ++i) {
          centerPosUm[i] = new float[connComps.first[i]][nDims];
          weightSum[i] = new float[connComps.first[i]];
          nPixels[i] = new int[connComps.first[i]];
          for (int j = 0; j < connComps.first[i]; ++j) {
            for (int d = 0; d < nDims; ++d) centerPosUm[i][j][d] = 0.0f;
            weightSum[i][j] = 0.0f;
            nPixels[i][j] = 0;
          }
        }
        Integer[] labels = connComps.second.data();
        double[] elSize = connComps.second.elementSizeUm();
        int lblIdx = 0;
        for (int t = 0; t < nFrames; ++t) {
          for (int c = 0; c < nClasses - 1; ++c) {
            float[][] cPosUm = centerPosUm[t * (nClasses - 1) + c];
            float[] ws = weightSum[t * (nClasses - 1) + c];
            int[] np = nPixels[t * (nClasses - 1) + c];
            for (int z = 0; z < nLevs; ++z) {
              job.progressMonitor().count(
                  "Computing positions t=" + (t + 1) + "/" + nFrames +
                  ", z=" + (z + 1) + "/" + nLevs + ", class=" + (c + 1) + "/" +
                  (nClasses - 1), 1);
              float[] smscore = (float[])
                  impSoftmaxScores.getStack().getProcessor(
                      impSoftmaxScores.getStackIndex(
                          c + 2, z + 1, t + 1)).getPixels();
              int smIdx = 0;
              for (int y = 0; y < nRows; ++y) {
                for (int x = 0; x < nCols; ++x, ++lblIdx, ++smIdx) {
                  if (labels[lblIdx] == 0) continue;
                  if (nDims == 2) {
                    cPosUm[labels[lblIdx] - 1][0] +=
                        smscore[smIdx] * y * elSize[0];
                    cPosUm[labels[lblIdx] - 1][1] +=
                        smscore[smIdx] * x * elSize[1];
                  }
                  else {
                    cPosUm[labels[lblIdx] - 1][0] +=
                        smscore[smIdx] * z * elSize[0];
                    cPosUm[labels[lblIdx] - 1][1] +=
                        smscore[smIdx] * y * elSize[1];
                    cPosUm[labels[lblIdx] - 1][2] +=
                        smscore[smIdx] * x * elSize[2];
                  }
                  ws[labels[lblIdx] - 1] += smscore[smIdx];
                  np[labels[lblIdx] - 1]++;
                }
              }
            }
          }
        }

        Overlay overlay = new Overlay();
        ResultsTable table = new ResultsTable();
        int volIdx = 0;
        for (int t = 0; t < nFrames; ++t) {
          for (int c = 0; c < nClasses - 1; ++c, ++volIdx) {
            PointRoi[] detections = new PointRoi[nLevs];
            for (int j = 0; j < connComps.first[volIdx]; ++j) {
              table.incrementCounter();
              table.addValue("frame", t + 1);
              for (int d = 0; d < nDims; ++d)
                  centerPosUm[volIdx][j][d] /= weightSum[volIdx][j];
              if (nDims == 2) {
                table.addValue("x [µm]", centerPosUm[volIdx][j][1]);
                table.addValue("y [µm]", centerPosUm[volIdx][j][0]);
                if (detections[0] == null) {
                  detections[0] = new PointRoi(
                      centerPosUm[volIdx][j][1] / elSize[1],
                      centerPosUm[volIdx][j][0] / elSize[0]);
                  detections[0].setPosition(t + 1);
                }
                else detections[0].addPoint(
                    centerPosUm[volIdx][j][1] / elSize[1],
                    centerPosUm[volIdx][j][0] / elSize[0]);
              }
              else {
                table.addValue("x [µm]", centerPosUm[volIdx][j][2]);
                table.addValue("y [µm]", centerPosUm[volIdx][j][1]);
                table.addValue("z [µm]", centerPosUm[volIdx][j][0]);
                int z = (int)Math.round(centerPosUm[volIdx][j][0] / elSize[0]);
                if (z < 0) z = 0;
                if (z >= nLevs) z = nLevs - 1;
                if (detections[z] == null) {
                  detections[z] = new PointRoi(
                      centerPosUm[volIdx][j][2] / elSize[2],
                      centerPosUm[volIdx][j][1] / elSize[1]);
                  if (nFrames > 1)
                      detections[z].setPosition(1, z + 1, t + 1);
                  else
                      detections[z].setPosition(z + 1);
                }
                else detections[z].addPoint(
                    centerPosUm[volIdx][j][2] / elSize[2],
                    centerPosUm[volIdx][j][1] / elSize[1]);
              }
              table.addValue("class", c + 1);
              table.addValue(
                  "confidence", weightSum[volIdx][j] / nPixels[volIdx][j]);
            }
            for (int z = 0; z < nLevs; ++z) {
              if (detections[z] != null) overlay.add(
                  detections[z], "Class " + (c + 1));
            }
          }
        }

        table.show(title + " (detections)");
        impClassification.setOverlay(overlay);

        job.progressMonitor().end();
      }
    }
    reader.close();
  }

  static int checkAck(InputStream in) throws IOException {
    int b = in.read();
    // b may be 0 for success,
    //          1 for error,
    //          2 for fatal error,
    //          -1
    if (b == 0) return b;
    if (b == -1) return b;

    if (b == 1 || b == 2) {
      StringBuffer sb = new StringBuffer();
      int c;
      do {
        c = in.read();
        sb.append((char)c);
      }
      while (c != '\n');
      if (b == 1) IJ.log(sb.toString());
      if (b == 2) IJ.log(sb.toString());
    }
    return b;
  }

}
