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
import ij.plugin.CompositeConverter;
import ij.plugin.filter.ParticleAnalyzer;

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
import java.util.HashMap;
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
 *   If the given ImagePlus is a color image (stack), a new ImagePlus will be
 *   created with color components split to individual channels.
 *   For grayscale images calling this method is a noop and a reference to
 *   the input ImagePlus is returned
 *
 *   \param imp The ImagePlus to convert to a composite multi channel image
 *   \param pr Task progress will be reported to this ProgressMonitor.
 *
 *   \return The newly created ImagePlus or if no conversion was required
 *     a reference to imp
 */
/*======================================================================*/
  public static ImagePlus makeComposite(ImagePlus imp, ProgressMonitor pr) {
    if (imp.getType() != ImagePlus.COLOR_256 &&
        imp.getType() != ImagePlus.COLOR_RGB) return imp;

    if (pr != null) pr.count("Splitting color channels", 0);
    ImagePlus out = CompositeConverter.makeComposite(imp);
    out.setTitle(imp.getTitle() + " - composite");
    out.setCalibration(imp.getCalibration().copy());
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
 *   \param pr Task progress will be reported to this ProgressMonitor.
 *
 *   \return The newly created ImagePlus or if no conversion was required
 *     a reference to imp
 */
/*======================================================================*/
  public static ImagePlus convertToFloat(ImagePlus imp, ProgressMonitor pr)
      throws InterruptedException {
    if (imp.getBitDepth() == 32) return imp;

    if (pr != null) {
      pr.init(0, "", "", imp.getImageStackSize());
      pr.count("Converting hyperstack to float", 0);
    }
    ImagePlus out = IJ.createHyperStack(
        imp.getTitle() + " - 32-Bit", imp.getWidth(), imp.getHeight(),
        imp.getNChannels(), imp.getNSlices(), imp.getNFrames(), 32);
    out.setCalibration(imp.getCalibration().copy());

    for (int i = 1; i <= imp.getImageStackSize(); i++) {
      if (pr != null) {
        pr.count(1);
        if (pr.canceled()) throw new InterruptedException();
      }
      out.getStack().setProcessor(
          imp.getStack().getProcessor(i).duplicate().convertToFloat(), i);
    }
    return out;
  }

/*======================================================================*/
/*!
 *   If the model definition requires 2-D data, both time and z will be
 *   interpreted as time. The stack layout is changed accordingly. For 3-D
 *   models or if the image only contains one slice per time point, this
 *   method is a noop and a reference to the input ImagePlus is returned
 *
 *   \param imp The ImagePlus to rearrange
 *   \param model The ModelDefinition to use for conversion
 *   \param pr Task progress will be reported to this ProgressMonitor.
 *
 *   \return The newly created ImagePlus or if no conversion was required
 *     a reference to imp
 */
/*======================================================================*/
  public static ImagePlus fixStackLayout(
      ImagePlus imp, ModelDefinition model, ProgressMonitor pr)
      throws InterruptedException {
    if (model.nDims() == 3 || imp.getNSlices() == 1) return imp;
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
    if (pr != null) {
      pr.init(0, "", "", imp.getImageStackSize());
      pr.count("Fixing stack layout", 0);
    }
    for (int i = 1; i <= imp.getImageStackSize(); ++i) {
      if (pr != null) {
        pr.count(1);
        if (pr.canceled()) throw new InterruptedException();
      }
      out.getStack().setProcessor(
          imp.getStack().getProcessor(i).duplicate(), i);
    }
    return out;
  }

  public static ImagePlus rescaleXY(
      ImagePlus imp, int interpolationMethod, ModelDefinition model,
      ProgressMonitor pr) throws InterruptedException {
    Calibration cal = imp.getCalibration().copy();
    int offs = (model.nDims() == 2) ? 0 : 1;
    double[] elSizeModel = model.elementSizeUm();
    double[] elSizeData = getElementSizeUmFromCalibration(cal, model.nDims());
    cal.setUnit("um");
    cal.pixelDepth = (model.nDims() == 3) ? elSizeData[0] : 1;
    cal.pixelHeight = elSizeModel[offs];
    cal.pixelWidth = elSizeModel[offs + 1];

    double[] scales = new double[2];
    scales[0] = elSizeData[offs] / elSizeModel[offs];
    scales[1] = elSizeData[offs + 1] / elSizeModel[offs + 1];
    if (scales[0] == 1 && scales[1] == 1) return imp;

    if (pr != null) {
      pr.init(0, "", "", imp.getImageStackSize());
      pr.count("Rescaling hyperstack (xy)", 0);
    }
    IJ.log("Rescaling Hyperstack (xy) from (" +
           ((model.nDims() == 3) ? (elSizeData[0] + ", ") : "") +
           elSizeData[offs] + ", " + elSizeData[offs + 1] + ") to (" +
           ((model.nDims() == 3) ? (elSizeModel[0] + ", ") : "") +
           elSizeModel[offs] + ", " + elSizeModel[offs + 1] + ")");

    ImagePlus out = IJ.createHyperStack(
        imp.getTitle() + " - rescaled (xy)",
        (int)Math.round(imp.getWidth() * scales[1]),
        (int)Math.round(imp.getHeight() * scales[0]), imp.getNChannels(),
        imp.getNSlices(), imp.getNFrames(), 32);
    out.setCalibration(cal);

    // ImageJ interpolation method NEAREST_NEIGHBOR seems to be broken...
    // To ensure proper interpolation we do the interpolation ourselves
    int W = imp.getWidth();
    int H = imp.getHeight();
    int Wout = out.getWidth();
    int Hout = out.getHeight();
    for (int i = 0; i < imp.getImageStackSize(); ++i) {
      if (pr != null) {
        pr.count(1);
        if (pr.canceled()) throw new InterruptedException();
      }
      ImageProcessor ipIn = imp.getStack().getProcessor(i + 1);
      ImageProcessor ipOut = out.getStack().getProcessor(i + 1);
      for (int y = 0; y < Hout; ++y) {
        double yRd = y / scales[0];
        int yL = (int)Math.floor(yRd);
        int yU = (yL + 1 < H) ? yL + 1 : (2 * (H - 1) - (yL + 1));
        double dy = yRd - yL;
        for (int x = 0; x < Wout; ++x) {
          double xRd = x / scales[1];
          int xL = (int)Math.floor(xRd);
          int xU = (xL + 1 < W) ? xL + 1 : (2 * (W - 1) - (xL + 1));
          double dx = xRd - xL;
          if (interpolationMethod == ImageProcessor.NEAREST_NEIGHBOR)
              ipOut.setf(
                  x, y, ipIn.getf((int)Math.round(xRd), (int)Math.round(yRd)));
          else {
            ipOut.setf(
                x, y,
                (float)(
                    (1 - dx) * (1 - dy) * ipIn.getf(xL, yL) +
                    (1 - dx) * dy * ipIn.getf(xL, yU) +
                    dx * (1 - dy) * ipIn.getf(xU, yL) +
                    dx * dy * ipIn.getf(xU, yU)));
          }
        }
      }
    }
    return out;
  }

  public static ImagePlus rescaleZ(
      ImagePlus imp, int interpolationMethod, ModelDefinition model,
      ProgressMonitor pr)
      throws InterruptedException {
    if (model.nDims() == 2 || imp.getNSlices() == 1) return imp;

    double elSizeZ = model.elementSizeUm()[0];
    Calibration cal = imp.getCalibration().copy();
    double[] elSizeData = getElementSizeUmFromCalibration(cal, 3);
    double scale = elSizeData[0] / elSizeZ;
    if (scale == 1) return imp;

    cal.setUnit("um");
    cal.pixelDepth = elSizeZ;
    cal.pixelHeight = elSizeData[1];
    cal.pixelWidth = elSizeData[2];
    ImagePlus out = IJ.createHyperStack(
        imp.getTitle() + " - rescaled (z)",
        imp.getWidth(), imp.getHeight(), imp.getNChannels(),
        (int)Math.round(imp.getNSlices() * scale), imp.getNFrames(), 32);
    out.setCalibration(cal);
    if (pr != null) {
      pr.init(0, "", "", imp.getImageStackSize());
      pr.count("Rescaling hyperstack (z)", 0);
    }
    IJ.log("Rescaling Hyperstack (z) from (" +
           elSizeData[0] + ", " + elSizeData[1] + ", " + elSizeData[2] +
           ") to (" + cal.pixelDepth + ", " + cal.pixelHeight + ", " +
           cal.pixelWidth + ")");
    IJ.log("  Input shape = [" + imp.getNFrames() + ", " +
           imp.getNChannels() + ", " + imp.getNSlices() + ", " +
           imp.getHeight() + ", " + imp.getWidth() + "]");
    IJ.log("  Output shape = [" + out.getNFrames() + ", " +
           out.getNChannels() + ", " + out.getNSlices() + ", " +
           out.getHeight() + ", " + out.getWidth() + "]");

    for (int t = 1; t <= out.getNFrames(); ++t) {
      for (int z = 1; z <= out.getNSlices(); ++z) {
        double zTmp = (z - 1) / scale + 1;
        if (interpolationMethod == ImageProcessor.BILINEAR) {
          int zIn = (int)Math.floor(zTmp);
          double lambda = zTmp - zIn;
          int zIn2 = zIn + 1;
          if (zIn >= imp.getNSlices())
              zIn = 2 * (imp.getNSlices() - 1) - zIn;
          if (zIn2 >= imp.getNSlices())
              zIn2 = 2 * (imp.getNSlices() - 1) - zIn2;
          for (int c = 1; c <= out.getNChannels(); ++c) {
            if (pr != null) {
              if (pr.canceled()) throw new InterruptedException();
              pr.count(1);
            }
            ImageProcessor ip = imp.getStack().getProcessor(
                imp.getStackIndex(c, zIn, t)).duplicate();
            if (lambda != 0) {
              ip.multiply(1 - lambda);
              ImageProcessor ip2 = imp.getStack().getProcessor(
                  imp.getStackIndex(c, zIn2, t)).duplicate();
              float[] ipData = (float[]) ip.getPixels();
              float[] ip2Data = (float[]) ip2.getPixels();
              for (int i = 0; i < imp.getHeight() * imp.getWidth(); ++i)
                  ipData[i] += lambda * ip2Data[i];
            }
            out.getStack().setProcessor(ip, out.getStackIndex(c, z, t));
          }
        }
        else {
          int zIn = (int)Math.round(zTmp);
          if (zIn >= imp.getNSlices())
              zIn = 2 * (imp.getNSlices() - 1) - zIn;
          for (int c = 1; c <= out.getNChannels(); ++c) {
            if (pr != null) {
              if (pr.canceled()) throw new InterruptedException();
              pr.count(1);
            }
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

  public static ImagePlus normalizeValues(
      ImagePlus imp, ModelDefinition model, ProgressMonitor pr)
      throws InterruptedException {
    if (model.normalizationType == 0) return imp;
    float[] scales = new float[imp.getNFrames()];
    float[] offsets = new float[imp.getNFrames()];
    boolean needsNormalization = false;

    long nSteps = 2 * imp.getStackSize();
    if (model.normalizationType == 2) nSteps += imp.getStackSize();
    if (pr != null) pr.init(0, "", "", nSteps);

    for (int t = 1; t <= imp.getNFrames(); ++t) {
      switch (model.normalizationType) {
      case 1: { // MIN/MAX
        float minValue = Float.POSITIVE_INFINITY;
        float maxValue = Float.NEGATIVE_INFINITY;
        for (int z = 1; z <= imp.getNSlices(); ++z) {
          for (int c = 1; c <= imp.getNChannels(); ++c) {
            if (pr != null) {
              if (pr.canceled()) throw new InterruptedException();
              pr.count("Computing data min/max (t=" + t + ", z=" + z +
                       ", c=" + c + ")", 1);
            }
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
            if (pr != null) {
              if (pr.canceled()) throw new InterruptedException();
              pr.count("Computing data mean (t=" + t + ", z=" + z +
                       ", c=" + c + ")", 1);
            }
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
            if (pr != null) {
              if (pr.canceled()) throw new InterruptedException();
              pr.count("Computing data standard deviation (t=" + t + ", z=" +
                       z + ", c=" + c + ")", 1);
            }
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
            if (pr != null) {
              if (pr.canceled()) throw new InterruptedException();
              pr.count("Computing data norm (t=" + t + ", z=" +
                       z + ", c=" + c + ")", 1);
            }
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
      needsNormalization |= offsets[t - 1] != 0 || scales[t - 1] != 1;
    }

    if (!needsNormalization) return imp;

    ImagePlus out = IJ.createHyperStack(
        imp.getTitle() + " - normalized", imp.getWidth(),
        imp.getHeight(), imp.getNChannels(), imp.getNSlices(),
        imp.getNFrames(), 32);
    out.setCalibration(imp.getCalibration().copy());
    for (int t = 1; t <= imp.getNFrames(); ++t) {
      for (int z = 1; z <= imp.getNSlices(); ++z) {
        for (int c = 1; c <= imp.getNChannels(); ++c) {
          if (pr != null) {
            if (pr.canceled()) throw new InterruptedException();
            pr.count(
                "Normalizing (t=" + t + ", z=" + z + ", c=" + c + ")", 1);
          }
          ImageProcessor ip =
              imp.getStack().getProcessor(
                  imp.getStackIndex(c, z, t)).duplicate();
          ip.add(offsets[t - 1]);
          ip.multiply(scales[t - 1]);
          ip.setMinAndMax(0, 1);
          out.getStack().setProcessor(ip, out.getStackIndex(c, z, t));
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
 *   \param imp   The ImagePlus to convert to unet model compliant format
 *   \param model The reference model for conversion
 *   \param pr    Task progress will be reported to this ProgressMonitor.
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
      ImagePlus imp, ModelDefinition model, ProgressMonitor pr,
      boolean keepOriginal, boolean show) throws InterruptedException {
    ImagePlus out = normalizeValues(
        rescaleZ(
            rescaleXY(
                fixStackLayout(
                    convertToFloat(makeComposite(imp, pr), pr), model, pr),
                ImageProcessor.BILINEAR, model, pr),
            ImageProcessor.BILINEAR, model, pr), model, pr);

    if (imp != out) {
      if (!keepOriginal) {
        imp.changes = false;
        imp.close();
      }
      if (show) {
        out.setDisplayRange(0, 1);
        out.updateAndDraw();
      }
    }
    return out;
  }

  public static void addLabelsAndWeightsToBlobs(
      int t, ConnectedComponentLabeling.ConnectedComponents instancelabels,
      IntBlob classlabels, ImagePlus labels, ImagePlus weights,
      ImagePlus samplePdf, ModelDefinition model, ProgressMonitor pr)
        throws InterruptedException {

    int T = labels.getNFrames();
    int C = instancelabels.nComponents.length;
    int D = labels.getNSlices();
    int H = labels.getHeight();
    int W = labels.getWidth();
    int[] blobShape = (D == 1) ? new int[] { H, W } : new int[] { D, H, W };

    int[] dx = null, dy = null, dz = null;
    if (D == 1)
    {
      dx = new int[] { -1,  0,  1, -1 };
      dy = new int[] { -1, -1, -1,  0 };
      dz = new int[] {  0,  0,  0,  0 };
    }
    else
    {
      dx = new int[] { -1,  0,  1, -1,  0,  1, -1,  0,  1, -1,  0,  1, -1 };
      dy = new int[] { -1, -1, -1,  0,  0,  0,  1,  1,  1, -1, -1, -1,  0 };
      dz = new int[] { -1, -1, -1, -1, -1, -1, -1, -1, -1,  0,  0,  0,  0 };
    }

    double foregroundBackgroundRatio = model.foregroundBackgroundRatio;
    double sigma1Um = model.sigma1Um;
    double borderWeightFactor = model.borderWeightFactor;
    double borderWeightSigmaUm = model.borderWeightSigmaUm;
    double[] elementSizeUm = model.elementSizeUm();

    int[] inst = (int[])instancelabels.labels.data();
    int[] classlabelsData = (int[])classlabels.data();

    // Generate (multiclass) labels with gaps, set foreground weights and
    // finalize sample pdf
    for (int z = 0; z < D; ++z) {

      int stackIdx = labels.getStackIndex(1, z + 1, t + 1);
      ImageProcessor ipLabels = labels.getStack().getProcessor(stackIdx);
      ipLabels.setValue(0.0f);
      ipLabels.fill();
      float[] labelsData = (float[])ipLabels.getPixels();
      ImageProcessor ipWeights = weights.getStack().getProcessor(stackIdx);
      ipWeights.setValue(-1.0f);
      ipWeights.fill();
      float[] weightsData = (float[])ipWeights.getPixels();
      ImageProcessor ipSamplePdf =
          samplePdf.getStack().getProcessor(stackIdx);
      ipSamplePdf.setValue(foregroundBackgroundRatio);
      ipSamplePdf.fill();
      float[] samplePdfData = (float[])ipSamplePdf.getPixels();

      int idx = 0;
      for (int y = 0; y < H; ++y) {
        for (int x = 0; x < W; ++x, ++idx) {
          int classLabel = classlabelsData[z * H * W + idx];
          if (classLabel == 0) { // Ignore label
            weightsData[idx] = 0.0f;
            samplePdfData[idx] = 0.0f;
            continue;
          }
          if (classLabel == 1) continue;
          int instanceLabel = inst[
              ((classLabel - 2) * D + z) * H * W + idx];
          // Check top left 8-neighorhood for different instance with same
          // class label, if there is one, treat this pixel as background,
          // otherwise mark as foreground
          int nbIdx = 0;
          for (; nbIdx < dx.length; ++nbIdx) {
            if (z + dz[nbIdx] < 0 ||
                y + dy[nbIdx] < 0 || y + dy[nbIdx] >= H ||
                x + dx[nbIdx] < 0 || x + dx[nbIdx] >= W)
                continue;
            int nbInst = inst[(((classLabel - 2) * D + z + dz[nbIdx]) * H +
                               y + dy[nbIdx]) * W + x + dx[nbIdx]];
            if (nbInst > 0 && nbInst != instanceLabel) break;
          }
          if (nbIdx == dx.length) { // Fine, can be labeled
            labelsData[idx] = classLabel - 1;
            weightsData[idx] = 1.0f;
            samplePdfData[idx] = 1.0f;
          }
        }
      }
    }

    // Compute extra weights (per class (outer), per instance (inner))
    FloatBlob extraWeightsBlob = new FloatBlob(blobShape, elementSizeUm);
    float[] extraWeights = (float[])extraWeightsBlob.data();
    Arrays.fill(extraWeights, 0.0f);
    FloatBlob min1DistBlob = new FloatBlob(blobShape, elementSizeUm);
    float[] min1Dist = (float[])min1DistBlob.data();
    FloatBlob min2DistBlob = new FloatBlob(blobShape, elementSizeUm);
    float[] min2Dist = (float[])min2DistBlob.data();
    double va = 1.0 - foregroundBackgroundRatio;
    for (int c = 0; c < C; ++c) {
      Arrays.fill(min1Dist, DistanceTransform.BG_VALUE);
      Arrays.fill(min2Dist, DistanceTransform.BG_VALUE);
      IntBlob instances = null;
      if (C == 1) instances = instancelabels.labels;
      else {
        instances = new IntBlob(blobShape, elementSizeUm);
        for (int i = 0; i < D * H * W; ++i)
            ((int[])instances.data())[i] =
                ((int[])instancelabels.labels.data())[c * D * W * H + i];
      }
      for (int i = 1; i <= instancelabels.nComponents[c]; i++) {
        if (pr != null) {
          if (pr.canceled()) throw new InterruptedException();
          pr.count("Processing slice " + (t + 1) + " / " + T + ", Class " +
                   (c + 1) + " / " + C + ": object " + i + " / " +
                   instancelabels.nComponents[c], 1);
        }
        FloatBlob d = DistanceTransform.getDistance(
            instances, i, DistanceTransform.Mode.DISTANCE_TO_FOREGROUND,
            true, null);
        for (int j = 0; j < D * H * W; j++) {
          float min1dist = min1Dist[j];
          float min2dist = Math.min(min2Dist[j], ((float[])d.data())[j]);
          min1Dist[j] = Math.min(min1dist, min2dist);
          min2Dist[j] = Math.max(min1dist, min2dist);
        }
      }

      for (int z = 0; z < D; ++z)
      {
        float[] w = (float[])weights.getStack().getProcessor(
            weights.getStackIndex(1, z + 1, t + 1)).getPixels();
        for (int i = 0; i < W * H; ++i) {
          if (w[i] >= 0.0f) continue;
          float d1 = min1Dist[z * W * H + i];
          float d2 = min2Dist[z * W * H + i];
          double wa = Math.exp(-(d1 * d1) / (2 * sigma1Um * sigma1Um));
          double we = Math.exp(
              -(d1 + d2) * (d1 + d2) /
              (2 * borderWeightSigmaUm * borderWeightSigmaUm));
          extraWeights[z * H * W + i] += borderWeightFactor * we + va * wa;
        }
      }
    }

    for (int z = 0; z < D; ++z) {
      float[] w = (float[])weights.getStack().getProcessor(
          weights.getStackIndex(1, z + 1, t + 1)).getPixels();
      for (int i = 0; i < W * H; ++i) {
        if (w[i] >= 0.0f) continue;
        w[i] = (float)foregroundBackgroundRatio + extraWeights[z * H * W + i];
      }
    }
  }

/*======================================================================*/
/*!
 *   Extracts annotations stored as overlay from the given ImagePlus and
 *   generates corresponding labels and weights. If ROI names contain
 *   ignore, they will be treated as ignore regions. Point ROIs will be
 *   treated as detection ROIs and instead of single points small disks are
 *   rendered as labels. Finetuning only works for 2D models at the moment.
 *
 *   \param imp The ImagePlus to generate labels and weights for
 *   \param model The model definition used for conversion
 *   \param pr Task progress will be reported to this ProgressMonitor
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
      ImagePlus imp, ModelDefinition model, ProgressMonitor pr)
      throws InterruptedException, NotImplementedException, BlobException {

    double[] elSizeModel = model.elementSizeUm();
    double[] elSizeData = getElementSizeUmFromCalibration(
        imp.getCalibration(), model.nDims());
    double scaleZ = (model.nDims() == 3) ? elSizeData[0] / elSizeModel[0] : 1.0;

    double foregroundBackgroundRatio = model.foregroundBackgroundRatio;
    double sigma1Um = model.sigma1Um;
    double bwFactor = model.borderWeightFactor;
    double bwSigmaUm = model.borderWeightSigmaUm;
    int diskRadiusPx = 2;

    // Generate small and large disks/spheres for PointRoi rendering
    Vector<Integer> dxVec = new Vector<Integer>();
    Vector<Integer> dyVec = new Vector<Integer>();
    Vector<Integer> dzVec = new Vector<Integer>();
    Vector<Integer> dxRVec = new Vector<Integer>();
    Vector<Integer> dyRVec = new Vector<Integer>();
    Vector<Integer> dzRVec = new Vector<Integer>();
    for (int z = ((model.nDims() == 3) ? -2 * diskRadiusPx : 0);
         z <= ((model.nDims() == 3) ? 2 * diskRadiusPx : 0); ++z) {
      for (int y = -2 * diskRadiusPx; y <= 2 * diskRadiusPx; ++y) {
        for (int x = -2 * diskRadiusPx; x <= 2 * diskRadiusPx; ++x) {
          double rSqr = z*z + y*y + x*x;
          if (rSqr <= diskRadiusPx * diskRadiusPx) {
            dxVec.add(x);
            dyVec.add(y);
            dzVec.add(z);
          }
          else if (rSqr <= 4 * diskRadiusPx * diskRadiusPx) {
            dxRVec.add(x);
            dyRVec.add(y);
            dzRVec.add(z);
          }
        }
      }
    }
    int[] dxSmall = new int[dxVec.size()];
    int[] dySmall = new int[dyVec.size()];
    int[] dzSmall = new int[dzVec.size()];
    for (int i = 0; i < dxVec.size(); ++i) {
      dxSmall[i] = dxVec.get(i);
      dySmall[i] = dyVec.get(i);
      dzSmall[i] = dzVec.get(i);
    }
    int[] dxLarge = new int[dxRVec.size()];
    int[] dyLarge = new int[dyRVec.size()];
    int[] dzLarge = new int[dzRVec.size()];
    for (int i = 0; i < dxRVec.size(); ++i) {
      dxLarge[i] = dxRVec.get(i);
      dyLarge[i] = dyRVec.get(i);
      dzLarge[i] = dzRVec.get(i);
    }

    // Fix layout of input ImagePlus and get dimensions
    ImagePlus[] blobs = new ImagePlus[] {
        fixStackLayout(imp, model, pr), null, null };
    int T = blobs[0].getNFrames();

    int Ds = (int)Math.round(blobs[0].getNSlices() * scaleZ);
    double[] elementSizeUm = Arrays.copyOf(elSizeData, model.nDims());
    if (model.nDims() == 3) elementSizeUm[0] = elSizeModel[0];

    int C = model.classNames.length - 1;
    int W = blobs[0].getWidth();
    int H = blobs[0].getHeight();

    ImagePlus impInstPlane = IJ.createHyperStack("", W, H, C, 1, 1, 16);
    ImageProcessor[] ipInst = new ImageProcessor[C];
    for (int c = 0; c < C; ++c)
        ipInst[c] = impInstPlane.getStack().getProcessor(c + 1);

    ImagePlus impClassPlane = IJ.createHyperStack("", W, H, 1, 1, 1, 16);
    ImageProcessor ipClass = impClassPlane.getProcessor();
    short[] classPlaneData = (short[])ipClass.getPixels();

    Roi[] rois = (imp.getOverlay() == null) ?
        (new Roi[] { imp.getRoi() }) : imp.getOverlay().toArray();

    for (int t = 0; t < T; ++t) {

      int[] blobShape = (Ds == 1) ? new int[] { H, W } : new int[] { Ds, H, W };
      int[] instShape = (C == 1) ? blobShape :
          ((Ds == 1) ? new int[] { C, H, W } : new int[] { C, Ds, H, W });

      ConnectedComponentLabeling.ConnectedComponents instancelabels =
          new ConnectedComponentLabeling.ConnectedComponents();
      instancelabels.labels = new IntBlob(instShape, elementSizeUm);
      int[] inst = (int[])instancelabels.labels.data();
      Arrays.fill(inst, 0);

      instancelabels.nComponents = new int[C];
      Arrays.fill(instancelabels.nComponents, 0);
      Vector< HashMap<Integer,Integer> > instMap =
          new Vector< HashMap<Integer,Integer> >();
      for (int c = 0; c < C; ++c) instMap.add(new HashMap<Integer,Integer>());

      IntBlob classlabels = new IntBlob(blobShape, elementSizeUm);
      int[] classlabelsData = (int[])classlabels.data();
      Arrays.fill(classlabelsData, 1);

      for (int z = 0; z < Ds; ++z) {
        for (int c = 0; c < C; ++c) {
          ipInst[c].setValue(0);
          ipInst[c].fill();
        }
        ipClass.setValue(1);
        ipClass.fill();
        for (Roi roi : rois) {
          int zRoi = Math.max(
              1, (int)Math.round(roi.getZPosition() * scaleZ)) - 1;
          int tRoi = Math.max(1, roi.getTPosition()) - 1;

          // Only process area rois in the current slice
          if (roi instanceof PointRoi || zRoi != z || tRoi != t) continue;

          RoiLabel rl = parseROIName(roi.getName());
          if (rl.className.toLowerCase().equals("ignore")) ipClass.setValue(0);
          else {
            int label = 1;
            if (C > 1) {
              while (label < model.classNames.length &&
                     !rl.className.equals(model.classNames[label])) ++label;
              if (label == model.classNames.length)
                  throw new BlobException("No such class: " + rl.className);
            }
            ipClass.setValue(label + 1);
            ipClass.fill(roi);

            if (rl.instance > 0)
            {
              if (!instMap.get(label - 1).containsKey(rl.instance))
              {
                instancelabels.nComponents[label - 1]++;
                instMap.get(label - 1).put(
                    rl.instance, instancelabels.nComponents[label - 1]);
              }
              ipInst[label - 1].setValue(
                  instMap.get(label - 1).get(rl.instance));
            }
            else {
              instancelabels.nComponents[label - 1]++;
              instMap.get(label - 1).put(
                  instancelabels.nComponents[label - 1],
                  instancelabels.nComponents[label - 1]);

              ipInst[label - 1].setValue(
                  instMap.get(label - 1).get(
                      instancelabels.nComponents[label - 1]));
            }
            ipInst[label - 1].fill(roi);
          }
        }
        for (int i = 0; i < H * W; ++i)
            classlabelsData[z * H * W + i] = classPlaneData[i];
        for (int c = 0; c < C; ++c) {
          short[] in = (short[])ipInst[c].getPixels();
          for (int i = 0; i < H * W; ++i)
              inst[(c * Ds + z) * H * W + i] = in[i];
        }
      }

      // Rescale instance labels and class labels
      instancelabels.labels.rescale(
          model.elementSizeUm(), Blob.InterpolationType.NEAREST, pr);
      classlabels.rescale(
          model.elementSizeUm(), Blob.InterpolationType.NEAREST, pr);
      inst = (int[])instancelabels.labels.data();
      classlabelsData = (int[])classlabels.data();
      blobShape = classlabels.shape();
      instShape = instancelabels.labels.shape();
      int Hs = blobShape[blobShape.length - 2];
      int Ws = blobShape[blobShape.length - 1];

      // Add PointRois
      for (Roi roi : rois) {
        int tRoi = Math.max(1, roi.getTPosition()) - 1;
        if (!(roi instanceof PointRoi) || tRoi != t) continue;
        int zRoi = Math.max(
            1, (int)Math.round(roi.getZPosition() * scaleZ)) - 1;
        int label = 1;
        RoiLabel rl = parseROIName(roi.getName());
        if (C > 1) {
          while (label < model.classNames.length &&
                 !rl.className.equals(model.classNames[label])) ++label;
          if (label == model.classNames.length)
              throw new BlobException("No such class: " + rl.className);
        }
        for (Point p : roi.getContainedPoints()) {
          instancelabels.nComponents[label - 1]++;
          // Draw ignore disk/sphere
          int yRoi = (int)(p.getY() * (double)Hs / (double)H);
          int xRoi = (int)(p.getX() * (double)Ws / (double)W);
          for (int i = 0; i < dxLarge.length; ++i) {
            int z = zRoi + dzLarge[i];
            int y = yRoi + dyLarge[i];
            int x = xRoi + dxLarge[i];
            if (z < 0 || z >= Ds || y < 0 || y >= Hs || x < 0 || x >= Ws ||
                classlabelsData[(z * Hs + y) * Ws + x] != 1) continue;
            classlabelsData[(z * Hs + y) * Ws + x] = 0;
          }
          // Draw label and instance label
          for (int i = 0; i < dxSmall.length; ++i) {
            int z = zRoi + dzSmall[i];
            int y = yRoi + dySmall[i];
            int x = xRoi + dxSmall[i];
            if (z < 0 || z >= Ds || y < 0 || y >= Hs || x < 0 || x >= Ws)
                continue;
            classlabelsData[(z * Hs + y) * Ws + x] = label + 1;
            inst[(((label - 1) * Ds + z) * Hs + y) * Ws + x] =
                instancelabels.nComponents[label - 1];
          }
        }
      }

      if (blobs[0] == null || blobs[1] == null || blobs[2] == null) {
        // Create output blobs
        Calibration cal = new Calibration();
        cal.setUnit("um");
        cal.pixelDepth = (model.nDims() == 2) ? 1 : model.elementSizeUm()[0];
        cal.pixelHeight =
            model.elementSizeUm()[model.elementSizeUm().length - 2];
        cal.pixelWidth =
            model.elementSizeUm()[model.elementSizeUm().length - 1];
        blobs[0] = IJ.createHyperStack(
            imp.getTitle() + " - labels", Ws, Hs, 1, Ds, T, 32);
        blobs[0].setCalibration(cal);
        blobs[1] = IJ.createHyperStack(
            imp.getTitle() + " - weights", Ws, Hs, 1, Ds, T, 32);
        blobs[1].setCalibration(cal);
        blobs[2] = IJ.createHyperStack(
            imp.getTitle() + " - sample pdf", Ws, Hs, 1, Ds, T, 32);
        blobs[2].setCalibration(cal);
      }

      addLabelsAndWeightsToBlobs(
          t, instancelabels, classlabels, blobs[0], blobs[1], blobs[2], model,
          pr);
    }

    return blobs;
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

    double elSize[] = getElementSizeUmFromCalibration(imp.getCalibration(), 2);

    if (pr != null) pr.init(0, "", "", imp.getImageStackSize());

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

    double[] elSize = getElementSizeUmFromCalibration(imp.getCalibration(), 3);

    if (pr != null) pr.init(0, "", "", imp.getImageStackSize());

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

  public static ImagePlus saveHDF5Blob(
      ImagePlus imp, File outFile, ModelDefinition model, ProgressMonitor pr,
      boolean generateLabelBlobs, boolean keepOriginal, boolean show)
      throws IOException, InterruptedException, NotImplementedException,
      BlobException {
    return saveHDF5Blob(
        imp, null, outFile, model, pr, generateLabelBlobs, keepOriginal, show);
  }

  public static ImagePlus saveHDF5Blob(
      ImagePlus imp, String[] classes, File outFile, ModelDefinition model,
      ProgressMonitor pr, boolean generateLabelBlobs, boolean keepOriginal,
      boolean show)
      throws IOException, InterruptedException, NotImplementedException,
      BlobException {
    if (model == null) {
      IJ.error("Cannot save HDF5 blob without associated U-Net model");
      throw new InterruptedException("No U-Net model");
    }

    String dsName = model.inputDatasetName;

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

    ImagePlus impScaled =
        convertToUnetFormat(imp, model, pr, keepOriginal, show);

    // Recursively create parent folders
    createFolder(outFile.getParentFile(), true);

    // syncMode: Always wait on close and flush till all data is written!
    // useSimpleDataSpace: Save attributes as plain vectors
    IHDF5Writer writer =
        HDF5Factory.configure(outFile.getAbsolutePath()).syncMode(
            IHDF5WriterConfigurator.SyncMode.SYNC_BLOCK)
        .useSimpleDataSpaceForAttributes().overwrite().writer();
    outFile.deleteOnExit();

    if (model.nDims() == 2) save2DBlob(impScaled, writer, dsName, pr);
    else save3DBlob(impScaled, writer, dsName, pr);

    if (generateLabelBlobs && imp.getOverlay() != null) {
      ImagePlus[] blobs = convertAnnotationsToLabelsAndWeights(imp, model, pr);

      if (model.nDims() == 2) {
        save2DBlob(blobs[0], writer, "labels", pr);
        save2DBlob(blobs[1], writer, "weights", pr);
        save2DBlob(blobs[2], writer, "weights2", pr);
      }
      else {
        save3DBlob(blobs[0], writer, "labels", pr);
        save3DBlob(blobs[1], writer, "weights", pr);
        save3DBlob(blobs[2], writer, "weights2", pr);
      }
    }

    writer.file().close();
    IJ.log("ImagePlus converted to caffe blob and saved to '" +
           outFile.getAbsolutePath() + "'");

    return impScaled;
  }

  public static File[] saveHDF5TiledBlob(
      ImagePlus imp, String fileNameStub, ModelDefinition model,
      ProgressMonitor pr)
      throws IOException, InterruptedException, NotImplementedException,
      BlobException {
    if (model == null) {
      IJ.error("Cannot save HDF5 blob without associated U-Net model");
      throw new InterruptedException("No U-Net model");
    }

    String dsName = model.inputBlobName;

    IJ.log("  Processing '" + imp.getTitle() + "': " +
           "#frames = " + imp.getNFrames() + ", #slices = " + imp.getNSlices() +
           ", #channels = " + imp.getNChannels() +
           ", height = " + imp.getHeight() +
           ", width = " + imp.getWidth() + " with element size = [" +
           imp.getCalibration().pixelDepth + ", " +
           imp.getCalibration().pixelHeight + ", " +
           imp.getCalibration().pixelWidth + "]");

    ImagePlus data = convertToUnetFormat(imp, model, pr, true, false);
    ImagePlus[] annotations = null;
    if (imp.getOverlay() != null)
        annotations = convertAnnotationsToLabelsAndWeights(imp, model, pr);

    int T = data.getNFrames();
    int Z = data.getNSlices();
    int C = data.getNChannels();
    int W = data.getWidth();
    int H = data.getHeight();

    int[] inShape = model.getTileShape();
    int[] outShape = model.getOutputTileShape(inShape);
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
    if (fileNameStub != null)
        createFolder(new File(fileNameStub).getParentFile(), true);

    // Create output ImagePlus
    ImagePlus dataTile = IJ.createHyperStack(
        imp.getTitle() + " - dataTile", inShape[1], inShape[0], C, Z, T, 32);
    ImagePlus labelsTile = IJ.createHyperStack(
        imp.getTitle() + " - labelsTile",
        outShape[1], outShape[0], 1, Z, T, 32);
    ImagePlus weightsTile = IJ.createHyperStack(
        imp.getTitle() + " - weightsTile",
        outShape[1], outShape[0], 1, Z, T, 32);

    if (pr != null) pr.init(0, "", "", nTiles);

    int tileIdx = 0;
    if (outShape.length == 2) {
      for (int yIdx = 0; yIdx < tiling[0]; yIdx++) {
        for (int xIdx = 0; xIdx < tiling[1]; xIdx++, tileIdx++) {

          if (pr != null &&
              !pr.count("Processing tile (" + yIdx + "," + xIdx + ")", 1))
              throw new InterruptedException();

          for (int t = 0; t < T; t++) {

            // Copy data tile for sample t
            for (int c = 0; c < C; c++) {
              int stackIdx = data.getStackIndex(c + 1, 1, t + 1);
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
            int stackIdx = annotations[0].getStackIndex(1, 1, t + 1);
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
          if (pr != null && pr.canceled()) throw new InterruptedException();

          // Save tile
          File outFile = null;
          if (fileNameStub == null) {
            outFile = File.createTempFile("unet-", ".h5");
            outFile.delete();
          }
          else outFile = new File(fileNameStub + "_" + tileIdx + ".h5");
          IHDF5Writer writer =
              HDF5Factory.configure(outFile.getAbsolutePath()).syncMode(
                  IHDF5WriterConfigurator.SyncMode.SYNC_BLOCK)
              .useSimpleDataSpaceForAttributes().overwrite().writer();
          outFile.deleteOnExit();

          save2DBlob(dataTile, writer, dsName, pr);
          save2DBlob(labelsTile, writer, "/labels", pr);
          save2DBlob(weightsTile, writer, "/weights", pr);
          writer.file().close();

          outfiles[tileIdx] = outFile;

          if (pr != null && pr.canceled()) throw new InterruptedException();
        }
      }
    }
    else {
      throw new NotImplementedException("3D from ROI not implemented");
    }

    return outfiles;
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

  public static Vector<File> createFolder(File path, boolean temporary)
      throws IOException {
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
      if (temporary) createdFolders.get(i).deleteOnExit();
    }
    return createdFolders;
  }

  public static double[] getElementSizeUmFromCalibration(
      Calibration cal, int nDims) {
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
      factor = 0.0001;
    break;
    case "pm":
    case "pikometer":
      factor = 0.0000001;
    break;
    }
    if (nDims == 2)
        return new double[] {
            cal.pixelHeight * factor, cal.pixelWidth * factor };
    else
        return new double[] {
            cal.pixelDepth * factor, cal.pixelHeight * factor,
            cal.pixelWidth * factor };
  }

  private static class RoiLabel {
    String className = "Foreground";
    int instance = -1;
  }

  private static RoiLabel parseROIName(String roiName) {
    if (roiName == null) return new RoiLabel();
    // Remove trailing clutter
    String tmp = roiName.replaceFirst("(-[0-9]+)*$", "");
    String[] comps = roiName.split("#");
    RoiLabel res = new RoiLabel();
    res.className = comps[0];
    if (comps.length > 1) {
      try {
        res.instance = Integer.parseInt(comps[1]);
      }
      catch (NumberFormatException e) {
        IJ.log("Could not parse instance label for ROI " + roiName);
      }
    }
    return res;
  }

}
