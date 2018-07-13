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
import ij.WindowManager;
import ij.process.ImageProcessor;
import ij.measure.Calibration;

import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.GroupLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.BorderFactory;

import java.io.File;
import java.io.IOException;

import java.util.Vector;
import java.util.HashMap;
import java.util.Arrays;

// HDF5 stuff
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Writer;
import ch.systemsx.cisd.hdf5.IHDF5WriterConfigurator;

public class TrainImagePair
{

  // Input images
  private ImagePlus rawdata = null;
  private ImagePlus rawlabels = null;

  // Output blobs
  private ImagePlus data = null;
  private ImagePlus labels = null;
  private ImagePlus weights = null;
  private ImagePlus samplePdf = null;

  private double[] elementSizeUm = null;

  private ModelDefinition conversionModel = null;

  public TrainImagePair(ImagePlus rawdata, ImagePlus rawlabels)
      throws TrainImagePairException {
    if (rawdata == null)
        throw new TrainImagePairException(
            "You must provide an ImagePlus containing raw intensities.");
    if (rawlabels == null)
        throw new TrainImagePairException(
            "You must provide an ImagePlus containing instance labels.");
    if (rawlabels.getBitDepth() > 16)
        throw new TrainImagePairException(
            "The label image must be either 8Bit or 16Bit integer");
    if (rawdata.getNFrames() != rawlabels.getNFrames() ||
        rawdata.getNFrames() != rawlabels.getNFrames() ||
        rawdata.getNFrames() != rawlabels.getNFrames() ||
        rawdata.getNFrames() != rawlabels.getNFrames() ||
        rawdata.getNFrames() != rawlabels.getNFrames())
        throw new TrainImagePairException(
            "The provided label hyperstack " + rawlabels.getTitle() +
            " is incompatible to the raw data hyperstack " +
            rawdata.getTitle());

    this.rawdata = rawdata;
    this.rawlabels = rawlabels;

    this.elementSizeUm = Tools.getElementSizeUmFromCalibration(
        rawdata.getCalibration(), (rawdata.getNSlices() == 1) ? 2 : 3);
  }

  public ImagePlus rawdata() {
    return rawdata;
  }

  public ImagePlus rawlabels() {
    return rawlabels;
  }

  public ImagePlus data() {
    return data;
  }

  public ImagePlus labels() {
    return labels;
  }

  public ImagePlus weights() {
    return weights;
  }

  public ImagePlus samplePdf() {
    return samplePdf;
  }

  public void createCaffeBlobs(
      String[] classes, ModelDefinition model, ProgressMonitor pr)
      throws InterruptedException, IOException, BlobException,
      NotImplementedException {

    boolean alreadyConverted =
        conversionModel != null &&
        model.elementSizeUm().length ==
        conversionModel.elementSizeUm().length &&
        model.foregroundBackgroundRatio ==
        conversionModel.foregroundBackgroundRatio &&
        model.sigma1Um == conversionModel.sigma1Um &&
        model.borderWeightSigmaUm == conversionModel.borderWeightSigmaUm &&
        model.borderWeightFactor == conversionModel.borderWeightFactor &&
        model.normalizationType == conversionModel.normalizationType;
    for (int d = 0; alreadyConverted &&
             d < conversionModel.elementSizeUm().length; ++d)
        alreadyConverted = alreadyConverted &&
            conversionModel.elementSizeUm()[d] == model.elementSizeUm()[d];
    if (alreadyConverted) return;

    if (model == null) {
      IJ.error("Cannot convert blob without associated U-Net model");
      throw new InterruptedException("No Model");
    }
    if (model.elementSizeUm().length > elementSizeUm.length)
        throw new NotImplementedException(
            "Cannot process " + elementSizeUm.length + "-D images using a " +
            model.elementSizeUm().length + "-D model.");

    if (model.elementSizeUm().length < elementSizeUm.length) {
      IJ.log("Warning: Model is " + model.elementSizeUm().length +
             "-D, data is " + elementSizeUm.length +
             "-D. Data will be treated as " + model.elementSizeUm().length +
             "-D");
      double[] elSize = new double[model.elementSizeUm().length];
      for (int d = 0; d < model.elementSizeUm().length; ++d)
          elSize[d] = elementSizeUm[d];
      elementSizeUm = elSize;
    }

    data = Tools.normalizeValues(
        Tools.rescaleZ(
            Tools.rescaleXY(
                Tools.fixStackLayout(
                    Tools.convertToFloat(
                        Tools.makeComposite(rawdata, pr), pr), model, pr),
                ImageProcessor.BILINEAR, model, pr),
            ImageProcessor.BILINEAR, model, pr), model, pr);

    labels = Tools.fixStackLayout(rawlabels, model, pr);

    int T = labels.getNFrames();
    int D = labels.getNSlices();
    int W = labels.getWidth();
    int H = labels.getHeight();

    for (int t = 0; t < T; ++t) {

      int[] blobShape = (D == 1) ? new int[] { H, W } : new int[] { D, H, W };

      ConnectedComponentLabeling.ConnectedComponents instancelabels = null;
      IntBlob classlabels = new IntBlob(blobShape, elementSizeUm);
      int[] classlabelsData = (int[])classlabels.data();

      if (classes == null) { // Treat labels as instance labels

        instancelabels = new ConnectedComponentLabeling.ConnectedComponents();
        instancelabels.labels = new IntBlob(blobShape, elementSizeUm);
        int[] instData = (int[])instancelabels.labels.data();
        Arrays.fill(instData, 0);

        HashMap<Integer,Integer> labelMap = new HashMap<Integer,Integer>();
        int nextLabel = 1;
        int idx = 0;
        for (int z = 0; z < D; ++z) {
          ImageProcessor ipIn = labels.getStack().getProcessor(
              labels.getStackIndex(1, z + 1, t + 1));
          for (int y = 0; y < H; ++y) {
            for (int x = 0; x < W; ++x, ++idx) {
              int label = (int)ipIn.getf(x, y);
              classlabelsData[idx] = (label > 1) ? 2 : label;
              if (label < 2) continue;
              if (!labelMap.containsKey(label))
                  labelMap.put(label, nextLabel++);
              instData[idx] = labelMap.get(label);
            }
          }
        }
        instancelabels.nComponents = new int[] { nextLabel - 1 };
      }
      else {
        ImagePlus tmp = IJ.createHyperStack(
            "binarylabels", W, H, classes.length - 2, D, 1, 8);
        int idx = 0;
        for (int z = 0; z < D; ++z) {
          ImageProcessor ipSrc = labels.getStack().getProcessor(
              labels.getStackIndex(1, z + 1, t + 1));
          ImageProcessor[] ipDest = new ImageProcessor[classes.length - 2];
          for (int c = 0; c < classes.length - 2; ++c) {
            ipDest[c] = tmp.getStack().getProcessor(
                tmp.getStackIndex(c + 1, z + 1, t + 1));
            ipDest[c].setValue(0);
            ipDest[c].fill();
          }
          for (int y = 0; y < H; ++y) {
            for (int x = 0; x < W; ++x, ++idx) {
              int label = (int)ipSrc.getf(x, y);
              classlabelsData[idx] = label;
              if (label < 2) continue;
              ipDest[label - 2].setf(x, y, 255.0f);
            }
          }
        }
        instancelabels = ConnectedComponentLabeling.label(
            tmp, ConnectedComponentLabeling.SIMPLE_NEIGHBORHOOD, pr);
      }

      // classlabels are 0 = ignore, 1 = background, 2-n = classes,
      // instance labels contain n-2 channels, each containing the
      // corresponding class instances with unique instance labels 1-k

      // Rescale class labels and instance labels to processing resolution
      classlabels.rescale(
          model.elementSizeUm(), Blob.InterpolationType.NEAREST, pr);
      classlabelsData = (int[])classlabels.data();
      instancelabels.labels.rescale(
          model.elementSizeUm(), Blob.InterpolationType.NEAREST, pr);

      int C = instancelabels.nComponents.length;
      D = (D > 1) ? classlabels.shape()[0] : 1;
      H = classlabels.shape()[(D > 1) ? 1 : 0];
      W = classlabels.shape()[(D > 1) ? 2 : 1];

      if (labels == null || weights == null || samplePdf == null) {
        // Create output blobs
        labels = IJ.createHyperStack(
            rawlabels.getTitle() + " - labels", W, H, 1, D, T, 32);
        labels.setCalibration(data.getCalibration().copy());
        weights = IJ.createHyperStack(
            rawlabels.getTitle() + " - weights", W, H, 1, D, T, 32);
        weights.setCalibration(data.getCalibration().copy());
        samplePdf = IJ.createHyperStack(
            rawlabels.getTitle() + " - sample pdf", W, H, 1, D, T, 32);
        samplePdf.setCalibration(data.getCalibration().copy());
      }

      Tools.addLabelsAndWeightsToBlobs(
          t, instancelabels, classlabels, labels, weights, samplePdf, model,
          pr);
    }
    conversionModel = model;
  }

  public void saveHDF5Blob(
      String[] classes, File outFile, ModelDefinition model, ProgressMonitor pr)
      throws InterruptedException, IOException, BlobException,
      NotImplementedException {

    createCaffeBlobs(classes, model, pr);

    // Recursively create parent folders
    Tools.createFolder(outFile.getParentFile(), true);

    // syncMode: Always wait on close and flush till all data is written!
    // useSimpleDataSpace: Save attributes as plain vectors
    IHDF5Writer writer =
        HDF5Factory.configure(outFile.getAbsolutePath()).syncMode(
            IHDF5WriterConfigurator.SyncMode.SYNC_BLOCK)
        .useSimpleDataSpaceForAttributes().overwrite().writer();
    outFile.deleteOnExit();

    if (elementSizeUm.length == 3) {
      Tools.save3DBlob(data, writer, model.inputDatasetName, pr);
      Tools.save3DBlob(labels, writer, "labels", pr);
      Tools.save3DBlob(weights, writer, "weights", pr);
      Tools.save3DBlob(samplePdf, writer, "weights2", pr);
    }
    else {
      Tools.save2DBlob(data, writer, model.inputDatasetName, pr);
      Tools.save2DBlob(labels, writer, "labels", pr);
      Tools.save2DBlob(weights, writer, "weights", pr);
      Tools.save2DBlob(samplePdf, writer, "weights2", pr);
    }
    writer.object().createGroup("/conversionParameters");
    writer.float64().setAttr(
        "/conversionParameters", "foregroundBackgroundRatio",
        conversionModel.foregroundBackgroundRatio);
    writer.float64().setAttr(
        "/conversionParameters", "sigma1_um", conversionModel.sigma1Um);
    writer.float64().setAttr(
        "/conversionParameters", "borderWeightFactor",
        conversionModel.borderWeightFactor);
    writer.float64().setAttr(
        "/conversionParameters", "borderWeightSigmaUm",
        conversionModel.borderWeightSigmaUm);
    writer.int32().setAttr(
        "/conversionParameters", "normalizationType",
        conversionModel.normalizationType);
    if (classes != null)
        writer.string().setArrayAttr(
            "/conversionParameters", "classNames",
            conversionModel.classNames);

    writer.file().close();
    IJ.log("TrainImagePair converted to caffe blob and saved to '" +
           outFile.getAbsolutePath() + "'");
  }

  public File[] saveHDF5TiledBlob(
      String[] classes, String fileNameStub, ModelDefinition model,
      ProgressMonitor pr) throws InterruptedException, IOException,
      BlobException, NotImplementedException {

    createCaffeBlobs(classes, model, pr);

    int T = data.getNFrames();
    int C = data.getNChannels();
    int D = data.getNSlices();
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
        Tools.createFolder(new File(fileNameStub).getParentFile(), true);

    // Create output ImagePlus
    ImagePlus dataTile = (outShape.length == 2) ?
        IJ.createHyperStack(
            rawdata.getTitle() + " - dataTile",
            inShape[1], inShape[0], C, 1, T, 32) :
        IJ.createHyperStack(
            rawdata.getTitle() + " - dataTile",
            inShape[2], inShape[1], C, inShape[0], T, 32);
    dataTile.setCalibration(data.getCalibration().copy());
    ImagePlus labelsTile = (outShape.length == 2) ?
        IJ.createHyperStack(
            rawdata.getTitle() + " - labelsTile",
            outShape[1], outShape[0], 1, 1, T, 32) :
        IJ.createHyperStack(
            rawdata.getTitle() + " - labelsTile",
            outShape[2], outShape[1], 1, outShape[0], T, 32);
    labelsTile.setCalibration(labels.getCalibration().copy());
    ImagePlus weightsTile = (outShape.length == 2) ?
        IJ.createHyperStack(
            rawdata.getTitle() + " - weightsTile",
            outShape[1], outShape[0], 1, 1, T, 32) :
        IJ.createHyperStack(
            rawdata.getTitle() + " - weightsTile",
            outShape[2], outShape[1], 1, outShape[0], T, 32);
    weightsTile.setCalibration(weights.getCalibration().copy());

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
            int stackIdx = labels.getStackIndex(1, 1, t + 1);
            ImageProcessor ipLabelsIn =
                labels.getStack().getProcessor(stackIdx);
            ImageProcessor ipLabelsOut =
                labelsTile.getStack().getProcessor(stackIdx);
            ImageProcessor ipWeightsIn =
                weights.getStack().getProcessor(stackIdx);
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

          Tools.save2DBlob(dataTile, writer, model.inputBlobName, pr);
          Tools.save2DBlob(labelsTile, writer, "/labels", pr);
          Tools.save2DBlob(weightsTile, writer, "/weights", pr);
          writer.file().close();

          outfiles[tileIdx] = outFile;

          if (pr != null && pr.canceled()) throw new InterruptedException();
        }
      }
    }
    else {
      for (int zIdx = 0; zIdx < tiling[0]; zIdx++) {
        for (int yIdx = 0; yIdx < tiling[1]; yIdx++) {
          for (int xIdx = 0; xIdx < tiling[2]; xIdx++, tileIdx++) {

            if (pr != null &&
                !pr.count("Processing tile (" + zIdx + "," + yIdx + "," +
                          xIdx + ")", 1)) throw new InterruptedException();

          for (int t = 0; t < T; t++) {

            // Copy data tile for sample t
            for (int c = 0; c < C; c++) {
              for (int z = 0; z < inShape[0]; ++z) {
                int zR = zIdx * outShape[0] - tileOffset[0] + z;
                if (zR < 0) zR = -zR;
                int n = zR / (D - 1);
                zR = (n % 2 == 0) ? (zR - n * (D - 1)) :
                    ((n + 1) * (D - 1) - zR);
                ImageProcessor ipDataIn =
                    data.getStack().getProcessor(
                        data.getStackIndex(c + 1, zR + 1, t + 1));
                ImageProcessor ipDataOut =
                    dataTile.getStack().getProcessor(
                        dataTile.getStackIndex(c + 1, z + 1, t + 1));
                for (int y = 0; y < inShape[1]; y++) {
                  int yR = yIdx * outShape[1] - tileOffset[1] + y;
                  if (yR < 0) yR = -yR;
                  n = yR / (H - 1);
                  yR = (n % 2 == 0) ? (yR - n * (H - 1)) :
                      ((n + 1) * (H - 1) - yR);
                  for (int x = 0; x < inShape[2]; x++) {
                    int xR = xIdx * outShape[2] - tileOffset[2] + x;
                    if (xR < 0) xR = -xR;
                    n = xR / (W - 1);
                    xR = (n % 2 == 0) ? (xR - n * (W - 1)) :
                        ((n + 1) * (W - 1) - xR);
                    ipDataOut.setf(x, y, ipDataIn.getf(xR, yR));
                  }
                }
              }
            }

            // Copy labels and weights tiles for sample t
            for (int z = 0; z < outShape[0]; ++z) {
              int zR = zIdx * outShape[0] + z;
              int n = zR / (D - 1);
              zR = (n % 2 == 0) ? (zR - n * (D - 1)) :
                  ((n + 1) * (D - 1) - zR);
              int stackIdxIn = labels.getStackIndex(1, zR + 1, t + 1);
              int stackIdxOut = labelsTile.getStackIndex(1, z + 1, t + 1);
              ImageProcessor ipLabelsIn =
                  labels.getStack().getProcessor(stackIdxIn);
              ImageProcessor ipLabelsOut =
                  labelsTile.getStack().getProcessor(stackIdxOut);
              ImageProcessor ipWeightsIn =
                  weights.getStack().getProcessor(stackIdxIn);
              ImageProcessor ipWeightsOut =
                  weightsTile.getStack().getProcessor(stackIdxOut);
              for (int y = 0; y < outShape[1]; y++) {
                int yR = yIdx * outShape[1] + y;
                n = yR / (H - 1);
                yR = (n % 2 == 0) ? (yR - n * (H - 1)) :
                    ((n + 1) * (H - 1) - yR);
                for (int x = 0; x < outShape[2]; x++) {
                  int xR = xIdx * outShape[2] + x;
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

          Tools.save3DBlob(dataTile, writer, model.inputBlobName, pr);
          Tools.save3DBlob(labelsTile, writer, "/labels", pr);
          Tools.save3DBlob(weightsTile, writer, "/weights", pr);
          writer.file().close();

          outfiles[tileIdx] = outFile;

          if (pr != null && pr.canceled()) throw new InterruptedException();
          }
        }
      }
    }

    return outfiles;
  }

  public static TrainImagePair selectImagePair() {
    int[] ids = WindowManager.getIDList();
    Vector<ImagePlus> images = new Vector<ImagePlus>();
    Vector<String> names = new Vector<String>();
    for (int id : ids) {
      images.add(WindowManager.getImage(id));
      names.add(WindowManager.getImage(id).getTitle());
    }

    final JLabel rawLabel = new JLabel("Raw Image");
    final JComboBox<String> rawBox = new JComboBox<String>(names);
    final JLabel labelLabel = new JLabel("Labels");
    final JComboBox<String> labelBox = new JComboBox<String>(names);

    final JPanel dlgPanel = new JPanel();
    dlgPanel.setBorder(BorderFactory.createEtchedBorder());
    final GroupLayout dlgLayout = new GroupLayout(dlgPanel);
    dlgPanel.setLayout(dlgLayout);
    dlgLayout.setAutoCreateGaps(true);
    dlgLayout.setAutoCreateContainerGaps(true);
    dlgLayout.setHorizontalGroup(
        dlgLayout.createSequentialGroup()
        .addGroup(
            dlgLayout.createParallelGroup(GroupLayout.Alignment.TRAILING)
            .addComponent(rawLabel).addComponent(labelLabel))
        .addGroup(
            dlgLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(rawBox).addComponent(labelBox)));
    dlgLayout.setVerticalGroup(
        dlgLayout.createSequentialGroup()
        .addGroup(
            dlgLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(rawLabel).addComponent(rawBox))
        .addGroup(
            dlgLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(labelLabel).addComponent(labelBox)));

    final JPanel okCancelPanel = new JPanel();
    JButton okButton = new JButton("OK");
    JButton cancelButton = new JButton("Cancel");
    okCancelPanel.add(okButton);
    okCancelPanel.add(cancelButton);

    JDialog dlg = new JDialog(
        WindowManager.getActiveWindow(), "Image Pair selection",
        Dialog.ModalityType.APPLICATION_MODAL);
    dlg.add(dlgPanel, BorderLayout.CENTER);
    dlg.add(okCancelPanel, BorderLayout.SOUTH);
    dlg.getRootPane().setDefaultButton(okButton);
    dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dlg.pack();
    dlg.setMinimumSize(dlg.getPreferredSize());
    dlg.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                                     dlg.getPreferredSize().height));
    dlg.setLocationRelativeTo(WindowManager.getActiveWindow());

    okButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            dlg.setVisible(false);
          }});

    cancelButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            dlg.dispose();
          }});

    dlg.setVisible(true);

    if (!dlg.isDisplayable()) return null;

    try {
      return new TrainImagePair(
          images.get(rawBox.getSelectedIndex()),
          images.get(labelBox.getSelectedIndex()));
    }
    catch (TrainImagePairException e) {
      IJ.error(e.toString());
      return null;
    }
  }

  public String toString() {
    return rawdata.getShortTitle() + " <-> " + rawlabels.getShortTitle();
  }

}
