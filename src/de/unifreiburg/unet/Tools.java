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

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;

import java.util.Vector;

import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;

import java.lang.reflect.Field;

import ch.systemsx.cisd.hdf5.IHDF5Writer;
import ch.systemsx.cisd.hdf5.HDF5FloatStorageFeatures;
import ch.systemsx.cisd.base.mdarray.MDFloatArray;

import com.jcraft.jsch.Session;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;

public class Tools {

  public static void saveBlob(
      ImagePlus imp, IHDF5Writer writer, String dsName, ProgressMonitor pr)
      throws InterruptedException {
    if (imp.getNSlices() == 1) save2DBlob(imp, writer, dsName, pr);
    else save3DBlob(imp, writer, dsName, pr);
  }

  public static void save2DBlob(
      ImagePlus imp, IHDF5Writer writer, String dsName, ProgressMonitor pr)
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

    double[] elSize = getElementSizeUm(imp);

    if (pr != null) pr.init(imp.getImageStackSize());

    writer.float32().createMDArray(
        dsName, dims, blockDims, HDF5FloatStorageFeatures.createDeflation(3));

    // Create HDF5 Multi-dimensional Array (memory space)
    MDFloatArray data = new MDFloatArray(blockDims);
    float[] dataFlat = data.getAsFlatArray();

    ImageStack stack = imp.getStack();

    for (int t = 0; t < T; ++t) {
      for (int z = 0; z < Z; ++z, ++blockIdx[0]) {
        for (int c = 0; c < C; ++c) {
          if (pr != null && !pr.count(
                  "Saving " + dsName + " t=" + t + ", z=" + z + ", c=" + c, 1))
              throw new InterruptedException();
          blockIdx[1] = c;
          int stackIndex = imp.getStackIndex(c + 1, z + 1, t + 1);
          System.arraycopy(stack.getPixels(stackIndex), 0, dataFlat, 0, H * W);
          writer.float32().writeMDArrayBlock(dsName, data, blockIdx);
        }
      }
    }
    writer.float64().setArrayAttr(dsName, "element_size_um", elSize);
  }

  public static void save3DBlob(
      ImagePlus imp, IHDF5Writer writer, String dsName, ProgressMonitor pr)
        throws InterruptedException {
    int T = imp.getNFrames();
    int Z = imp.getNSlices();
    int C = imp.getNChannels();
    int W = imp.getWidth();
    int H = imp.getHeight();
    long[] dims = { T, C, Z, H, W };
    int[] blockDims = { 1, 1, 1, H, W };
    long[] blockIdx = { 0, 0, 0, 0, 0 };

    double[] elSize = getElementSizeUm(imp);

    if (pr != null) pr.init(imp.getImageStackSize());

    writer.float32().createMDArray(
        dsName, dims, blockDims, HDF5FloatStorageFeatures.createDeflation(3));

    // Create HDF5 Multi-dimensional Array (memory space)
    MDFloatArray data = new MDFloatArray(blockDims);
    float[] dataFlat = data.getAsFlatArray();

    ImageStack stack = imp.getStack();

    for (int t = 0; t < T; ++t) {
      blockIdx[0] = t;
      for (int z = 0; z < Z; ++z) {
        blockIdx[2] = z;
        for (int c = 0; c < C; ++c) {
          blockIdx[1] = c;
          if (pr != null && !pr.count(
                  "Saving " + dsName + " t=" + t + ", z=" + z + ", c=" + c, 1))
              throw new InterruptedException();
          int stackIndex = imp.getStackIndex(c + 1, z + 1, t + 1);
          System.arraycopy(stack.getPixels(stackIndex), 0, dataFlat, 0, H * W);
          writer.float32().writeMDArrayBlock(dsName, data, blockIdx);
        }
      }
    }
    writer.float64().setArrayAttr(dsName, "element_size_um", elSize);
  }

  public static int getPID(Process p) {
    if (p.getClass().getName().equals("java.lang.UNIXProcess")) {
      try {
        Field f = p.getClass().getDeclaredField("pid");
        f.setAccessible(true);
        return f.getInt(p);
      }
      catch (NoSuchFieldException e) {
        System.out.println("Could not get Process ID: " + e);
        return -1;
      }
      catch (IllegalAccessException e) {
        System.out.println("Could not get Process ID: " + e);
      }
    }
    return -1;
  }

  /***********************************************************************
   * Parse the given caffe error stream output for specific error messages.
   * If the type of error can be determined the corresponding error code
   * otherwise -1 (unknown) is returned.
   *
   * \return The error code
   *         -1 - unknown
   *          1 - out of memory
   *          2 - invalid device ordinal
   *          3 - Insufficient compute capability
   *
   ***********************************************************************/
  public static int getCaffeError(String error) {
    if (error.contains("out of memory")) return 1;
    if (error.contains("invalid device ordinal")) return 2;
    if (error.contains("CUDNN_STATUS_ARCH_MISMATCH")) return 3;
    return -1;
  }

  public static String getCaffeErrorString(String error) {
    switch (getCaffeError(error)) {
    case 1:
      return "Not enough GPU memory available.";
    case 2:
      return "Requested GPU does not exist.";
    case 3:
      return "The compute capability of your GPU is too low.\n" +
          "Please choose another GPU or rebuild caffe_unet for your GPU.";
    default:
      String[] errors = error.split("\n");
      for (int i = 0; i < errors.length; ++i)
          if (errors[i].contains("Check failed: error == cudaSuccess"))
              return errors[i];
    }
    return "Unknown caffe error.";
  }

  public static ProcessResult execute(
      Vector<String> command, ProgressMonitor pr)
      throws IOException, InterruptedException {
    if (command == null || command.size() == 0)
        throw new IOException("Tools.execute() Received empty command");
    String cmdString = command.get(0);
    for (int i = 1; i < command.size(); ++i) cmdString += " " + command.get(i);
    if (pr != null) pr.count("Executing '" + cmdString + "'", 0);
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
      String command, Session session, ProgressMonitor pr)
      throws JSchException, InterruptedException, IOException {
    if (pr != null) pr.count("Executing remote command '" + command + "'", 0);
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

  public static Vector<File> createFolder(File path) throws IOException {
    Vector<File> createdFolders = new Vector<File>();
    while (path != null && !path.isDirectory()) {
      createdFolders.add(path);
      path = path.getParentFile();
    }
    for (int i = createdFolders.size() - 1; i >= 0; --i) {
      IJ.log("$ mkdir \"" + createdFolders.get(i) + "\"");
      if (!createdFolders.get(i).mkdir())
          throw new IOException(
              "Could not create folder '" +
              createdFolders.get(i).getAbsolutePath() + "'");
    }
    return createdFolders;
  }

  public static double[] getElementSizeUm(ImagePlus imp) {
    Calibration cal = imp.getCalibration();
    double factor = 1;
    switch (cal.getUnit())
    {
    case "m":
    case "meter":
      factor = 1000000.0;
    break;
    case "cm":
    case "centimeter":
      factor = 10000.0;
    break;
    case "mm":
    case "millimeter":
      factor = 1000.0;
    break;
    case "nm":
    case "nanometer":
      factor = 0.001;
    break;
    case "pm":
    case "pikometer":
      factor = 0.000001;
    break;
    }
    if (imp.getNSlices() == 1)
        return new double[] {
            cal.pixelHeight * factor, cal.pixelWidth * factor };
    else
        return new double[] {
            cal.pixelDepth * factor, cal.pixelHeight * factor,
            cal.pixelWidth * factor };
  }

  public static void setElementSizeUm(ImagePlus imp, double[] elementSizeUm) {
    Calibration cal = imp.getCalibration();
    cal.setUnit("um");
    if (imp.getNSlices() == 1) {
      cal.pixelDepth = 1;
      cal.pixelHeight = elementSizeUm[0];
      cal.pixelWidth = elementSizeUm[1];
    }
    else {
      cal.pixelDepth = elementSizeUm[0];
      cal.pixelHeight = elementSizeUm[1];
      cal.pixelWidth = elementSizeUm[2];
    }
  }

}
