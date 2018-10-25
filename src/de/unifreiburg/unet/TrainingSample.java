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
import ij.process.ImageProcessor;
import ij.gui.Roi;
import ij.gui.PointRoi;
import ij.gui.ImageRoi;
import ij.measure.Calibration;
import ij.plugin.CompositeConverter;

import java.awt.Point;

import java.io.File;
import java.io.IOException;

import java.util.Vector;
import java.util.Arrays;
import java.util.HashMap;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Writer;
import ch.systemsx.cisd.hdf5.IHDF5WriterConfigurator;

public class TrainingSample
{

  // Input image
  private final ImagePlus _imp;

  // Output blobs
  private ModelDefinition _conversionModel = null;
  private ImagePlus _data = null;
  private ImagePlus _labels = null;
  private ImagePlus _weights = null;
  private ImagePlus _samplePdf = null;

  public TrainingSample(ImagePlus imp) {
    _imp = imp;
  }

  public ImagePlus getImage() {
    return _imp;
  }

  public int getRawNDims() {
    return (_imp.getNSlices() == 1) ? 2 : 3;
  }

  public double[] getRawElementSizeUm() {
    return Tools.getElementSizeUm(_imp);
  }

  public ImagePlus dataBlob() {
    return _data;
  }

  public ImagePlus labelBlob() {
    return _labels;
  }

  public ImagePlus weightBlob() {
    return _weights;
  }

  public ImagePlus samplePdfBlob() {
    return _samplePdf;
  }

  public void createDataBlob(ModelDefinition model, ProgressMonitor pr)
      throws TrainingSampleException, InterruptedException {
    if (model == null) throw new TrainingSampleException("No Model");
    if (_data != null && wasConvertedWithModel(model)) return;

    if (model.nDims() > getRawElementSizeUm().length)
        throw new TrainingSampleException(
            "Cannot process " + getRawNDims() + "-D images using a " +
            model.nDims() + "-D model.");

    if (_conversionModel != null && !wasConvertedWithModel(model)) {
      _data = null;
      _labels = null;
      _weights = null;
      _samplePdf = null;
      _conversionModel = null;
    }

    if (model.nDims() < getRawNDims())
        IJ.log("Warning: Model is " + model.nDims() + "-D, data is " +
               getRawNDims() + "-D. Data will be treated as " +
               model.nDims() + "-D");

    if (pr != null) pr.push("Convert to composite", 0.0f, 0.05f);
    _data = makeComposite(_imp, pr);
    if (pr != null) {
      pr.pop();
      pr.push("Convert to 32-Bit float", 0.05f, 0.1f);
    }
    _data = convertToFloat(_data, pr);
    if (pr != null) {
      pr.pop();
      pr.push("Fix stack layout", 0.1f, 0.15f);
    }
    _data = fixStackLayout(_data, model, pr);
    if (pr != null) {
      pr.pop();
      pr.push("Rescale (xy)", 0.1f, 0.6f);
    }
    _data = rescaleXY(_data, ImageProcessor.BILINEAR, model, pr);
    if (pr != null) {
      pr.pop();
      pr.push("Rescale (z)", 0.6f, 0.8f);
    }
    _data = rescaleZ(_data, ImageProcessor.BILINEAR, model, pr);
    if (pr != null) {
      pr.pop();
      pr.push("Normalize values", 0.8f, 1.0f);
    }
    _data = normalizeValues(_data, model, pr);
    if (pr != null) pr.pop();

    _conversionModel = model;
  }

  public void createLabelsAndWeightsBlobs(
      ModelDefinition model, boolean labelsAreClasses, ProgressMonitor pr)
      throws TrainingSampleException, InterruptedException, BlobException {
    if (model == null) throw new TrainingSampleException("No Model");
    if (_labels != null && _weights != null && _samplePdf != null &&
        wasConvertedWithModel(model)) return;

    if (_imp.getOverlay() == null)
        throw new TrainingSampleException(
            "Image does not contain Overlay with annotations");

    boolean containsMaskAnnotations = false;
    boolean containsRoiAnnotations = false;
    for (Roi roi : _imp.getOverlay().toArray()) {
      containsMaskAnnotations |= roi instanceof ImageRoi;
      containsRoiAnnotations |= !(roi instanceof ImageRoi);
    }
    if (!containsMaskAnnotations && !containsRoiAnnotations)
        throw new TrainingSampleException(
            "Image Overlay does not contain annotations");
    if (containsMaskAnnotations && containsRoiAnnotations)
        throw new TrainingSampleException(
            "Image Overlay contains ROI and mask annotations. " +
            "Only one annotation type per image is supported.");

    if (model.nDims() > getRawNDims())
        throw new TrainingSampleException(
            "Cannot process " + getRawNDims() + "-D images using a " +
            model.nDims() + "-D model.");

    if (_conversionModel != null && !wasConvertedWithModel(model)) {
      _data = null;
      _labels = null;
      _weights = null;
      _samplePdf = null;
      _conversionModel = null;
    }

    if (containsMaskAnnotations)
        createLabelsAndWeightBlobsFromMasks(model, labelsAreClasses, pr);
    else createLabelsAndWeightBlobsFromRois(model, labelsAreClasses, pr);

    _conversionModel = model;
  }

  public Vector<File> saveBlobs(
      File outFile, ModelDefinition model, ProgressMonitor pr)
      throws TrainingSampleException, InterruptedException, IOException {

    boolean createDataBlob = !dataBlobReady(model);
    if (createDataBlob)
    {
      if (pr != null) pr.push("Converting data to U-Net format", 0.0f, 0.6f);
      createDataBlob(model, pr);
      if (pr != null) pr.pop();
    }
    if (pr != null)
        pr.push("Saving blob(s)", createDataBlob ? 0.6f : 0.0f, 1.0f);

    // Recursively create parent folders
    Vector<File> createdFiles = Tools.createFolder(outFile.getParentFile());

    // syncMode: Always wait on close and flush till all data is written!
    // useSimpleDataSpace: Save attributes as plain vectors
    IHDF5Writer writer =
        HDF5Factory.configure(outFile.getAbsolutePath()).syncMode(
            IHDF5WriterConfigurator.SyncMode.SYNC_BLOCK)
        .useSimpleDataSpaceForAttributes().overwrite().writer();
    createdFiles.add(outFile);

    boolean saveLabels =
        _labels != null && _weights != null && _samplePdf != null;

    if (pr != null)
        pr.push("Saving " + outFile.getName() + ":/data",
                0.0f, saveLabels ? 0.25f : 1.0f);
    Tools.saveBlob(_data, writer, model.inputDatasetName, pr);

    if (saveLabels) {
      if (pr != null) {
        pr.pop();
        pr.push("Saving " + outFile.getName() + ":/labels", 0.25f, 0.5f);
      }
      Tools.saveBlob(_labels, writer, "labels", pr);
      if (pr != null) {
        pr.pop();
        pr.push("Saving " + outFile.getName() + ":/weights", 0.5f, 0.75f);
      }
      Tools.saveBlob(_weights, writer, "weights", pr);
      if (pr != null) {
        pr.pop();
        pr.push("Saving " + outFile.getName() + ":/weights2", 0.75f, 1.0f);
      }
      Tools.saveBlob(_samplePdf, writer, "weights2", pr);
      writer.object().createGroup("/conversionParameters");
      writer.float64().setAttr(
          "/conversionParameters", "foregroundBackgroundRatio",
          _conversionModel.foregroundBackgroundRatio);
      writer.float64().setAttr(
          "/conversionParameters", "sigma1_um",
          _conversionModel.sigma1Px);
      writer.float64().setAttr(
          "/conversionParameters", "borderWeightFactor",
          _conversionModel.borderWeightFactor);
      writer.float64().setAttr(
          "/conversionParameters", "borderWeightSigmaPx",
          _conversionModel.borderWeightSigmaPx);
      writer.int32().setAttr(
          "/conversionParameters", "normalizationType",
          _conversionModel.normalizationType);
      writer.string().setArrayAttr(
          "/conversionParameters", "classNames",
          _conversionModel.classNames);
    }

    writer.file().close();
    IJ.log("Caffe blobs saved to '" + outFile.getAbsolutePath() + "'");

    if (pr != null) {
      pr.pop();
      pr.pop();
    }

    return createdFiles;
  }

  public Vector<File> saveTiledBlobs(
      String fileNameStub, ModelDefinition model, boolean labelsAreClasses,
      ProgressMonitor pr)
      throws TrainingSampleException, InterruptedException, IOException,
      BlobException {

    boolean createDataBlob = !dataBlobReady(model);
    boolean createLabelBlobs = !labelBlobsReady(model);

    if (createDataBlob) {
      if (pr != null)
          pr.push("Converting data to U-Net format",
                  0.0f, createLabelBlobs ? 0.05f : 0.5f);
      createDataBlob(model, pr);
      if (pr != null) pr.pop();
    }
    if (createLabelBlobs) {
      if (pr != null)
          pr.push("Converting annotations",
                  createDataBlob ? 0.05f : 0.0f, 0.5f);
      createLabelsAndWeightsBlobs(model, labelsAreClasses, pr);
      if (pr != null) pr.pop();
    }
    if (pr != null)
        pr.push("Saving tiled blobs",
                (createDataBlob || createLabelBlobs) ? 0.5f : 0.0f, 1.0f);

    int T = _data.getNFrames();
    int C = _data.getNChannels();
    int D = _data.getNSlices();
    int W = _data.getWidth();
    int H = _data.getHeight();

    int[] inShape = model.getTileShape();
    int[] outShape = model.getOutputTileShape(inShape);
    int[] tileOffset = new int[inShape.length];
    for (int d = 0; d < tileOffset.length; d++)
        tileOffset[d] = (inShape[d] - outShape[d]) / 2;
    int[] tiling = new int[outShape.length];
    int nTiles = 1;
    if (outShape.length == 2) {
      tiling[0] = (int)(
          Math.ceil((double)_data.getHeight() / (double)outShape[0]));
      tiling[1] = (int)(
          Math.ceil((double)_data.getWidth() / (double)outShape[1]));
      IJ.log("  tiling = " + tiling[0] + "x" + tiling[1]);
    }
    else {
      tiling[0] = (int)(
          Math.ceil((double)_data.getNSlices() / (double)outShape[0]));
      tiling[1] = (int)(
          Math.ceil((double)_data.getHeight() / (double)outShape[1]));
      tiling[2] = (int)(
          Math.ceil((double)_data.getWidth() / (double)outShape[2]));
      IJ.log("  tiling = " + tiling[0] + "x" + tiling[1] + "x" + tiling[2]);
    }
    for (int d = 0; d < outShape.length; d++) nTiles *= tiling[d];
    IJ.log("  nTiles = " + nTiles);

    Vector<File> createdFiles = new Vector<File>();

    // Recursively create parent folders
    if (fileNameStub != null)
        createdFiles.addAll(
            Tools.createFolder(new File(fileNameStub).getParentFile()));

    // Create output ImagePlus
    ImagePlus dataTile = (outShape.length == 2) ?
        IJ.createHyperStack(
            _imp.getTitle() + " - dataTile",
            inShape[1], inShape[0], C, 1, T, 32) :
        IJ.createHyperStack(
            _imp.getTitle() + " - dataTile",
            inShape[2], inShape[1], C, inShape[0], T, 32);
    dataTile.setCalibration(_data.getCalibration().copy());
    ImagePlus labelsTile = (outShape.length == 2) ?
        IJ.createHyperStack(
            _imp.getTitle() + " - labelsTile",
            outShape[1], outShape[0], 1, 1, T, 32) :
        IJ.createHyperStack(
            _imp.getTitle() + " - labelsTile",
            outShape[2], outShape[1], 1, outShape[0], T, 32);
    labelsTile.setCalibration(_labels.getCalibration().copy());
    ImagePlus weightsTile = (outShape.length == 2) ?
        IJ.createHyperStack(
            _imp.getTitle() + " - weightsTile",
            outShape[1], outShape[0], 1, 1, T, 32) :
        IJ.createHyperStack(
            _imp.getTitle() + " - weightsTile",
            outShape[2], outShape[1], 1, outShape[0], T, 32);
    weightsTile.setCalibration(_weights.getCalibration().copy());

    int tileIdx = 0;
    if (outShape.length == 2) {
      for (int yIdx = 0; yIdx < tiling[0]; yIdx++) {
        for (int xIdx = 0; xIdx < tiling[1]; xIdx++, tileIdx++) {

          if (pr != null)
              pr.push("Processing tile (" + yIdx + "," + xIdx + ")",
                      (float)tileIdx / (float)nTiles,
                      (float)(tileIdx + 1) / (float)nTiles);

          for (int t = 0; t < T; t++) {

            // Copy data tile for sample t
            for (int c = 0; c < C; c++) {
              int stackIdx = _data.getStackIndex(c + 1, 1, t + 1);
              ImageProcessor ipDataIn =
                  _data.getStack().getProcessor(stackIdx);
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
            int stackIdx = _labels.getStackIndex(1, 1, t + 1);
            ImageProcessor ipLabelsIn =
                _labels.getStack().getProcessor(stackIdx);
            ImageProcessor ipLabelsOut =
                labelsTile.getStack().getProcessor(stackIdx);
            ImageProcessor ipWeightsIn =
                _weights.getStack().getProcessor(stackIdx);
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
          createdFiles.add(outFile);

          if (pr != null)
              pr.push("Saving " + outFile.getName() + ":/data", 0.0f, 0.33f);
          Tools.saveBlob(dataTile, writer, model.inputBlobName, pr);
          if (pr != null) {
            pr.pop();
            pr.push("Saving " + outFile.getName() + ":/labels", 0.33f, 0.67f);
          }
          Tools.saveBlob(labelsTile, writer, "/labels", pr);
          if (pr != null) {
            pr.pop();
            pr.push("Saving " + outFile.getName() + ":/weights", 0.67f, 1.0f);
          }
          Tools.saveBlob(weightsTile, writer, "/weights", pr);
          writer.file().close();
          if (pr != null) {
            pr.pop();
            pr.pop();
            if (pr.canceled()) throw new InterruptedException();
          }
        }
      }
    }
    else {
      for (int zIdx = 0; zIdx < tiling[0]; zIdx++) {
        for (int yIdx = 0; yIdx < tiling[1]; yIdx++) {
          for (int xIdx = 0; xIdx < tiling[2]; xIdx++, tileIdx++) {

            if (pr != null)
                pr.push("Processing tile (" + zIdx + "," + yIdx + "," +
                        xIdx + ")", (float)tileIdx / (float)nTiles,
                        (float)(tileIdx + 1) / (float)nTiles);

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
                      _data.getStack().getProcessor(
                          _data.getStackIndex(c + 1, zR + 1, t + 1));
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
                int stackIdxIn = _labels.getStackIndex(1, zR + 1, t + 1);
                int stackIdxOut = labelsTile.getStackIndex(1, z + 1, t + 1);
                ImageProcessor ipLabelsIn =
                    _labels.getStack().getProcessor(stackIdxIn);
                ImageProcessor ipLabelsOut =
                    labelsTile.getStack().getProcessor(stackIdxOut);
                ImageProcessor ipWeightsIn =
                    _weights.getStack().getProcessor(stackIdxIn);
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
            createdFiles.add(outFile);

            if (pr != null)
                pr.push("Saving " + outFile.getName() + ":/data", 0.0f, 0.33f);
            Tools.saveBlob(dataTile, writer, model.inputBlobName, pr);
            if (pr != null) {
              pr.pop();
              pr.push("Saving " + outFile.getName() + ":/labels", 0.33f, 0.67f);
            }
            Tools.saveBlob(labelsTile, writer, "/labels", pr);
            if (pr != null) {
              pr.pop();
              pr.push("Saving " + outFile.getName() + ":/weights", 0.67f, 1.0f);
            }
            Tools.saveBlob(weightsTile, writer, "/weights", pr);
            writer.file().close();
            if (pr != null) {
              pr.pop();
              pr.pop();
              if (pr.canceled()) throw new InterruptedException();
            }
          }
        }
      }
    }

    if (pr != null) pr.pop();

    return createdFiles;
  }

  private boolean wasConvertedWithModel(ModelDefinition model) {
    boolean res = _conversionModel != null &&
        model.elementSizeUm().length ==
        _conversionModel.elementSizeUm().length &&
        model.foregroundBackgroundRatio ==
        _conversionModel.foregroundBackgroundRatio &&
        model.sigma1Px == _conversionModel.sigma1Px &&
        model.borderWeightSigmaPx == _conversionModel.borderWeightSigmaPx &&
        model.borderWeightFactor == _conversionModel.borderWeightFactor &&
        model.normalizationType == _conversionModel.normalizationType;
    for (int d = 0; res && d < _conversionModel.elementSizeUm().length; ++d)
        res &= _conversionModel.elementSizeUm()[d] == model.elementSizeUm()[d];
    return res;
  }

  private boolean dataBlobReady(ModelDefinition model) {
    return (_data != null && wasConvertedWithModel(model));
  }

  private boolean labelBlobsReady(ModelDefinition model) {
    return (_labels != null && _weights != null && _samplePdf != null &&
            wasConvertedWithModel(model));
  }

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
  private static ImagePlus makeComposite(ImagePlus imp, ProgressMonitor pr)
        throws InterruptedException {
    if (imp.getType() != ImagePlus.COLOR_256 &&
        imp.getType() != ImagePlus.COLOR_RGB) return imp;

    if (pr != null && !pr.count("Splitting color channels", 0))
        throw new InterruptedException();
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
  private static ImagePlus convertToFloat(ImagePlus imp, ProgressMonitor pr)
      throws InterruptedException {
    if (imp.getBitDepth() == 32) return imp;

    if (pr != null) {
      pr.init(imp.getImageStackSize());
      if (!pr.count("Converting hyperstack to float", 0))
          throw new InterruptedException();
    }
    ImagePlus out = IJ.createHyperStack(
        imp.getTitle() + " - 32-Bit", imp.getWidth(), imp.getHeight(),
        imp.getNChannels(), imp.getNSlices(), imp.getNFrames(), 32);
    out.setCalibration(imp.getCalibration().copy());

    for (int i = 1; i <= imp.getImageStackSize(); i++) {
      if (pr != null && !pr.count(1)) throw new InterruptedException();
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
  private static ImagePlus fixStackLayout(
      ImagePlus imp, ModelDefinition model, ProgressMonitor pr)
      throws InterruptedException {
    if (model.nDims() == 3 || imp.getNSlices() == 1) return imp;
    ImagePlus out = IJ.createHyperStack(
        imp.getTitle() + " - reordered", imp.getWidth(), imp.getHeight(),
        imp.getNChannels(), 1, imp.getNSlices() * imp.getNFrames(),
        imp.getBitDepth());
    Calibration cal = imp.getCalibration().copy();
    cal.pixelDepth = 1;
    out.setCalibration(cal);
    if (pr != null) {
      pr.init(imp.getImageStackSize());
      if (!pr.count("Fixing stack layout", 0)) throw new InterruptedException();
    }
    for (int i = 1; i <= imp.getImageStackSize(); ++i) {
      if (pr != null && !pr.count(1)) throw new InterruptedException();
      out.getStack().setProcessor(
          imp.getStack().getProcessor(i).duplicate(), i);
    }
    return out;
  }

  private static ImagePlus rescaleXY(
      ImagePlus imp, int interpolationMethod, ModelDefinition model,
      ProgressMonitor pr) throws InterruptedException {
    Calibration cal = imp.getCalibration().copy();
    int offs = (model.nDims() == 2) ? 0 : 1;
    double[] elSizeModel = model.elementSizeUm();
    double[] elSizeData = Tools.getElementSizeUm(imp);
    cal.setUnit("um");
    cal.pixelDepth = (model.nDims() == 3) ? elSizeData[0] : 1;
    cal.pixelHeight = elSizeModel[offs];
    cal.pixelWidth = elSizeModel[offs + 1];

    double[] scales = new double[2];
    scales[0] = elSizeData[offs] / elSizeModel[offs];
    scales[1] = elSizeData[offs + 1] / elSizeModel[offs + 1];
    if (scales[0] == 1 && scales[1] == 1) return imp;

    if (pr != null) {
      pr.init(imp.getImageStackSize());
      if (!pr.count("Rescaling hyperstack (xy)", 0))
          throw new InterruptedException();
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
      if (pr != null && !pr.count(1)) throw new InterruptedException();
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

  private static ImagePlus rescaleZ(
      ImagePlus imp, int interpolationMethod, ModelDefinition model,
      ProgressMonitor pr)
      throws InterruptedException {
    if (model.nDims() == 2 || imp.getNSlices() == 1) return imp;

    double elSizeZ = model.elementSizeUm()[0];
    Calibration cal = imp.getCalibration().copy();
    double[] elSizeData = Tools.getElementSizeUm(imp);
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
      pr.init(out.getImageStackSize());
      if (!pr.count("Rescaling hyperstack (z)", 0))
          throw new InterruptedException();
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
            if (pr != null && !pr.count(1)) throw new InterruptedException();
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
            if (pr != null && !pr.count(1)) throw new InterruptedException();
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

  private static ImagePlus normalizeValues(
      ImagePlus imp, ModelDefinition model, ProgressMonitor pr)
      throws InterruptedException {
    if (model.normalizationType == 0) return imp;
    float[] scales = new float[imp.getNFrames()];
    float[] offsets = new float[imp.getNFrames()];
    boolean needsNormalization = false;

    long nSteps = 2 * imp.getStackSize();
    if (model.normalizationType == 2) nSteps += imp.getStackSize();
    if (pr != null) pr.init(nSteps);

    for (int t = 1; t <= imp.getNFrames(); ++t) {
      switch (model.normalizationType) {
      case 1: { // MIN/MAX
        float minValue = Float.POSITIVE_INFINITY;
        float maxValue = Float.NEGATIVE_INFINITY;
        for (int z = 1; z <= imp.getNSlices(); ++z) {
          for (int c = 1; c <= imp.getNChannels(); ++c) {
            if (pr != null &&
                !pr.count("Computing data min/max (t=" + t + ", z=" + z +
                          ", c=" + c + ")", 1))
                throw new InterruptedException();
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
            if (pr != null &&
                !pr.count("Computing data mean (t=" + t + ", z=" + z +
                          ", c=" + c + ")", 1))
                throw new InterruptedException();
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
            if (pr != null &&
                !pr.count("Computing data standard deviation (t=" + t + ", z=" +
                          z + ", c=" + c + ")", 1))
                throw new InterruptedException();
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
            if (pr != null &&
                !pr.count("Computing data norm (t=" + t + ", z=" +
                          z + ", c=" + c + ")", 1))
                throw new InterruptedException();
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
          if (pr != null &&
              !pr.count(
                  "Normalizing (t=" + t + ", z=" + z + ", c=" + c + ")", 1))
              throw new InterruptedException();
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

  private void createLabelsAndWeightBlobsFromMasks(
      ModelDefinition model, boolean labelsAreClasses, ProgressMonitor pr)
      throws InterruptedException {

    double[] elementSizeUm = new double[model.nDims()];
    for (int d = 0; d < model.nDims(); ++d)
        elementSizeUm[d] = getRawElementSizeUm()[
            d + (getRawNDims() - model.nDims())];

    if (pr != null) pr.push("Extracting masks", 0.0f, 0.01f);
    ImagePlus impLabels =
        fixStackLayout(MaskExtractor.extract(_imp), model, pr);
    if (pr != null) pr.pop();

    int T = impLabels.getNFrames();
    int D = impLabels.getNSlices();
    int W = impLabels.getWidth();
    int H = impLabels.getHeight();
    int[] blobShape = (D == 1) ? new int[] { H, W } : new int[] { D, H, W };

    if (pr != null) pr.push("Converting masks", 0.01f, 1.0f);

    for (int t = 0; t < T; ++t) {

      if (pr != null)
          pr.push("Processing " + _imp.getTitle() +
                  " frame " + (t + 1) + " / " + T,
                  (float)t / (float)T, (float)(t + 1) / (float)T);

      ConnectedComponentLabeling.ConnectedComponents instancelabels = null;
      IntBlob classlabels = new IntBlob(blobShape, elementSizeUm);
      int[] classlabelsData = (int[])classlabels.data();

      if (labelsAreClasses) {
        if (pr != null) {
          pr.push("Generating binary labels and instance labels", 0.0f, 0.1f);
          pr.init(D);
        }
        ImagePlus tmp = IJ.createHyperStack(
            "binarylabels", W, H, model.classNames.length - 2, D, 1, 8);
        int idx = 0;
        for (int z = 0; z < D; ++z) {
          ImageProcessor ipSrc = impLabels.getStack().getProcessor(
              impLabels.getStackIndex(1, z + 1, t + 1));
          ImageProcessor[] ipDest =
              new ImageProcessor[model.classNames.length - 2];
          for (int c = 0; c < model.classNames.length - 2; ++c) {
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
          if (pr != null && !pr.count(1)) throw new InterruptedException();
        }
        if (pr != null) {
          pr.pop();
          pr.push("Connected component labeling", 0.1f, 0.2f);
        }
        instancelabels = ConnectedComponentLabeling.label(
            tmp, ConnectedComponentLabeling.SIMPLE_NEIGHBORHOOD, pr);
      }
      else { // Treat labels as instance labels

        if (pr != null) {
          pr.push("Generating binary labels and instance labels",
                  0.0f, 0.2f);
          pr.init(D);
        }

        instancelabels = new ConnectedComponentLabeling.ConnectedComponents();
        instancelabels.labels = new IntBlob(blobShape, elementSizeUm);
        int[] instData = (int[])instancelabels.labels.data();
        Arrays.fill(instData, 0);

        HashMap<Integer,Integer> labelMap = new HashMap<Integer,Integer>();
        int nextLabel = 1;
        int idx = 0;
        for (int z = 0; z < D; ++z) {
          ImageProcessor ipIn = impLabels.getStack().getProcessor(
              impLabels.getStackIndex(1, z + 1, t + 1));
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
          if (pr != null && !pr.count(1)) throw new InterruptedException();
        }
        instancelabels.nComponents = new int[] { nextLabel - 1 };
      }

      // classlabels are 0 = ignore, 1 = background, 2-n = classes,
      // instance labels contain n-2 channels, each containing the
      // corresponding class instances with unique instance labels 1-k

      if (pr != null) {
        pr.pop();
        pr.push("Rescaling class labels", 0.2f, 0.3f);
      }

      classlabels.rescale(
          model.elementSizeUm(), Blob.InterpolationType.NEAREST, pr);
      classlabelsData = (int[])classlabels.data();

      if (pr != null) {
        pr.pop();
        pr.push("Rescaling instance labels", 0.3f, 0.4f);
      }

      instancelabels.labels.rescale(
          model.elementSizeUm(), Blob.InterpolationType.NEAREST, pr);

      int C = instancelabels.nComponents.length;
      int Ds = (D > 1) ? classlabels.shape()[0] : 1;
      int Hs = classlabels.shape()[(D > 1) ? 1 : 0];
      int Ws = classlabels.shape()[(D > 1) ? 2 : 1];

      if (_labels == null || _weights == null || _samplePdf == null) {
        // Create output blobs
        _labels = IJ.createHyperStack(
            _imp.getTitle() + " - labels", Ws, Hs, 1, Ds, T, 32);
        Tools.setElementSizeUm(_labels, classlabels.elementSizeUm());
        _weights = IJ.createHyperStack(
            _imp.getTitle() + " - weights", Ws, Hs, 1, Ds, T, 32);
        Tools.setElementSizeUm(_weights, classlabels.elementSizeUm());
        _samplePdf = IJ.createHyperStack(
            _imp.getTitle() + " - sample pdf", Ws, Hs, 1, Ds, T, 32);
        Tools.setElementSizeUm(_samplePdf, classlabels.elementSizeUm());
      }

      if (pr != null) {
        pr.pop();
        pr.push("Generating weights", 0.4f, 1.0f);
      }
      addLabelsAndWeightsToBlobs(t, instancelabels, classlabels, model, pr);

      if (pr != null) {
        pr.pop();
        pr.pop();
      }
    }
    if (pr != null) pr.pop();
  }

  private void createLabelsAndWeightBlobsFromRois(
      ModelDefinition model, boolean labelsAreClasses, ProgressMonitor pr)
      throws BlobException, InterruptedException {

    double scaleZ =
        (model.nDims() == 3) ?
        getRawElementSizeUm()[0] / model.elementSizeUm()[0] : 1.0;

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

    int T = (model.nDims() == 2 && _imp.getNSlices() > 1) ?
        _imp.getNSlices() * _imp.getNFrames() : _imp.getNFrames();
    int Ds = (model.nDims() == 3) ?
        (int)Math.round(_imp.getNSlices() * scaleZ) : 1;
    int C = model.classNames.length - 1;
    int W = _imp.getWidth();
    int H = _imp.getHeight();

    double[] elementSizeUm;
    if (getRawNDims() == model.nDims()) elementSizeUm = getRawElementSizeUm();
    else {
      elementSizeUm = new double[model.nDims()];
      for (int d = 0; d < model.nDims(); ++d)
          elementSizeUm[d] = getRawElementSizeUm()[
              d + getRawNDims() - model.nDims()];
    }
    if (model.nDims() == 3) elementSizeUm[0] = model.elementSizeUm()[0];

    ImagePlus impInstPlane = IJ.createHyperStack("", W, H, C, 1, 1, 16);
    ImageProcessor[] ipInst = new ImageProcessor[C];
    for (int c = 0; c < C; ++c)
        ipInst[c] = impInstPlane.getStack().getProcessor(c + 1);

    ImagePlus impClassPlane = IJ.createHyperStack("", W, H, 1, 1, 1, 16);
    ImageProcessor ipClass = impClassPlane.getProcessor();
    short[] classPlaneData = (short[])ipClass.getPixels();

    Roi[] rois = _imp.getOverlay().toArray();

    for (int t = 0; t < T; ++t) {

      if (pr != null) {
        pr.push("Processing " + _imp.getTitle() +
                " frame " + (t + 1) + " / " + T,
                (float)t / (float)T, (float)(t + 1) / (float)T);
        pr.push("Adding ROIs", 0.0f, 0.1f);
        int nRois = 0;
        for (Roi roi : rois) {
          if (roi instanceof PointRoi) continue;
          if (getROIPosition(roi, Ds, scaleZ).t == t + 1) nRois++;
        }
        pr.init(nRois);
      }

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

      // Get a mask of potentially annotated planes after rescaling
      // to set planes that cannot contain annotations to ignore
      // This is especially important for upscaling to avoid false negatives
      boolean[] planeAnnotated = new boolean[Ds];
      for (int z = 0; z < Ds; ++z) planeAnnotated[z] = false;
      for (int z = 0; z < _imp.getNSlices(); ++z)
          planeAnnotated[(int)Math.round(z * scaleZ)] = true;

      for (int z = 0; z < Ds; ++z) {
        for (int c = 0; c < C; ++c) {
          ipInst[c].setValue(0);
          ipInst[c].fill();
        }
        ipClass.setValue((planeAnnotated[z]) ? 1 : 0);
        ipClass.fill();
        for (Roi roi : rois) {
          if (roi instanceof PointRoi) continue;
          RoiPosition p = getROIPosition(roi, Ds, scaleZ);
          if (p.z != z + 1 || p.t != t + 1) continue;

          RoiLabel rl = parseRoiName(roi.getName());
          if (rl.isIgnore()) ipClass.setValue(0);
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
          if (pr != null && !pr.count(1)) throw new InterruptedException();
        }
        for (int i = 0; i < H * W; ++i)
            classlabelsData[z * H * W + i] = classPlaneData[i];
        for (int c = 0; c < C; ++c) {
          short[] in = (short[])ipInst[c].getPixels();
          for (int i = 0; i < H * W; ++i)
              inst[(c * Ds + z) * H * W + i] = in[i];
        }
      }

      if (pr != null) {
        pr.pop();
        pr.push("Rescaling instance labels", 0.1f, 0.2f);
      }
      instancelabels.labels.rescale(
          model.elementSizeUm(), Blob.InterpolationType.NEAREST, pr);
      if (pr != null) {
        pr.pop();
        pr.push("Rescaling class labels", 0.2f, 0.3f);
      }
      classlabels.rescale(
          model.elementSizeUm(), Blob.InterpolationType.NEAREST, pr);
      if (pr != null) {
        pr.pop();
        pr.push("Adding point ROIs", 0.3f, 0.4f);
        int nPoints = 0;
        for (Roi roi : rois) {
          if (!(roi instanceof PointRoi)) continue;
          if (getROIPosition(roi, Ds, scaleZ).t == t + 1)
              nPoints += roi.getContainedPoints().length;
        }
        pr.init(nPoints);
      }

      inst = (int[])instancelabels.labels.data();
      classlabelsData = (int[])classlabels.data();
      blobShape = classlabels.shape();
      instShape = instancelabels.labels.shape();
      int Hs = blobShape[blobShape.length - 2];
      int Ws = blobShape[blobShape.length - 1];

      // Add PointRois
      for (Roi roi : rois) {
        if (!(roi instanceof PointRoi)) continue;
        RoiPosition pos = getROIPosition(roi, Ds, scaleZ);
        if (pos.t != t + 1) continue;
        int label = 1;
        RoiLabel rl = parseRoiName(roi.getName());
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
            int z = pos.z + dzLarge[i];
            int y = yRoi + dyLarge[i];
            int x = xRoi + dxLarge[i];
            if (z < 0 || z >= Ds || y < 0 || y >= Hs || x < 0 || x >= Ws ||
                classlabelsData[(z * Hs + y) * Ws + x] != 1) continue;
            classlabelsData[(z * Hs + y) * Ws + x] = 0;
          }
          // Draw label and instance label
          for (int i = 0; i < dxSmall.length; ++i) {
            int z = pos.z + dzSmall[i];
            int y = yRoi + dySmall[i];
            int x = xRoi + dxSmall[i];
            if (z < 0 || z >= Ds || y < 0 || y >= Hs || x < 0 || x >= Ws)
                continue;
            classlabelsData[(z * Hs + y) * Ws + x] = label + 1;
            inst[(((label - 1) * Ds + z) * Hs + y) * Ws + x] =
                instancelabels.nComponents[label - 1];
          }
        }
        if (pr != null && !pr.count(1)) throw new InterruptedException();
      }

      if (_labels == null || _weights == null || _samplePdf == null) {
        _labels = IJ.createHyperStack(
            _imp.getTitle() + " - labels", Ws, Hs, 1, Ds, T, 32);
        Tools.setElementSizeUm(_labels, classlabels.elementSizeUm());
        _weights = IJ.createHyperStack(
            _imp.getTitle() + " - weights", Ws, Hs, 1, Ds, T, 32);
        Tools.setElementSizeUm(_weights, classlabels.elementSizeUm());
        _samplePdf = IJ.createHyperStack(
            _imp.getTitle() + " - sample pdf", Ws, Hs, 1, Ds, T, 32);
        Tools.setElementSizeUm(_samplePdf, classlabels.elementSizeUm());
      }

      if (pr != null) {
        pr.pop();
        pr.push("Generating weights", 0.4f, 1.0f);
      }
      addLabelsAndWeightsToBlobs(t, instancelabels, classlabels, model, pr);

      if (pr != null) {
        pr.pop();
        pr.pop();
      }
    }
  }

  private void addLabelsAndWeightsToBlobs(
      int t, ConnectedComponentLabeling.ConnectedComponents instancelabels,
      IntBlob classlabels, ModelDefinition model, ProgressMonitor pr)
        throws InterruptedException {

    int T = _labels.getNFrames();
    int C = instancelabels.nComponents.length;
    int D = _labels.getNSlices();
    int H = _labels.getHeight();
    int W = _labels.getWidth();
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
    double sigma1Px = model.sigma1Px;
    double borderWeightFactor = model.borderWeightFactor;
    double borderWeightSigmaPx = model.borderWeightSigmaPx;
    double[] elementSizeUm = model.elementSizeUm();

    int[] inst = (int[])instancelabels.labels.data();
    int[] classlabelsData = (int[])classlabels.data();

    if (pr != null) {
      pr.push("Adding gaps", 0.0f, 0.1f);
      pr.init(D);
    }

    // Generate (multiclass) labels with gaps, set foreground weights and
    // finalize sample pdf
    for (int z = 0; z < D; ++z) {

      int stackIdx = _labels.getStackIndex(1, z + 1, t + 1);
      ImageProcessor ipLabels = _labels.getStack().getProcessor(stackIdx);
      ipLabels.setValue(0.0f);
      ipLabels.fill();
      float[] labelsData = (float[])ipLabels.getPixels();
      ImageProcessor ipWeights = _weights.getStack().getProcessor(stackIdx);
      ipWeights.setValue(-1.0f);
      ipWeights.fill();
      float[] weightsData = (float[])ipWeights.getPixels();
      ImageProcessor ipSamplePdf =
          _samplePdf.getStack().getProcessor(stackIdx);
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
      if (pr != null && !pr.count(1)) throw new InterruptedException();
    }

    int nObjects = 0;
    for (int c = 0; c < C; ++c) nObjects += instancelabels.nComponents[c];

    if (pr != null) {
      pr.pop();
      pr.push("Weight computation", 0.1f, 0.99f);
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
    int processedObjects = 0;
    for (int c = 0; c < C; ++c) {

      if (pr != null) {
        pr.push("Computing weights for class " + c,
                (float)processedObjects / (float)nObjects,
                (float)(processedObjects + instancelabels.nComponents[c]) /
                (float)nObjects);
        pr.push("Distance transform", 0.0f, 0.9f);
        pr.init(instancelabels.nComponents[c]);
      }

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
        if (pr != null &&
            !pr.count("Processing slice " + (t + 1) + " / " + T + ", Class " +
                      (c + 1) + " / " + C + ": object " + i + " / " +
                      instancelabels.nComponents[c], 1))
            throw new InterruptedException();
        FloatBlob d = DistanceTransform.getDistance(
            instances, i, DistanceTransform.Mode.DISTANCE_TO_FOREGROUND,
            false, null);
        for (int j = 0; j < D * H * W; j++) {
          float min1dist = min1Dist[j];
          float min2dist = Math.min(min2Dist[j], ((float[])d.data())[j]);
          min1Dist[j] = Math.min(min1dist, min2dist);
          min2Dist[j] = Math.max(min1dist, min2dist);
        }
      }
      processedObjects += instancelabels.nComponents[c];

      if (pr != null) {
        pr.pop();
        pr.push("Computing edge weights", 0.9f, 1.0f);
        pr.init(D);
      }

      for (int z = 0; z < D; ++z)
      {
        float[] w = (float[])_weights.getStack().getProcessor(
            _weights.getStackIndex(1, z + 1, t + 1)).getPixels();
        for (int i = 0; i < W * H; ++i) {
          if (w[i] >= 0.0f) continue;
          float d1 = min1Dist[z * W * H + i];
          float d2 = min2Dist[z * W * H + i];
          double wa = Math.exp(-(d1 * d1) / (2 * sigma1Px * sigma1Px));
          double we = Math.exp(
              -(d1 + d2) * (d1 + d2) /
              (2 * borderWeightSigmaPx * borderWeightSigmaPx));
          extraWeights[z * H * W + i] += borderWeightFactor * we + va * wa;
        }
        if (pr != null && !pr.count(1)) throw new InterruptedException();
      }

      if (pr != null) {
        pr.pop();
        pr.pop();
      }
    }

    if (pr != null) {
      pr.pop();
      pr.push("Set final weights", 0.99f, 1.0f);
      pr.init(D);
    }

    for (int z = 0; z < D; ++z) {
      float[] w = (float[])_weights.getStack().getProcessor(
          _weights.getStackIndex(1, z + 1, t + 1)).getPixels();
      for (int i = 0; i < W * H; ++i) {
        if (w[i] >= 0.0f) continue;
        w[i] = (float)foregroundBackgroundRatio + extraWeights[z * H * W + i];
      }
      if (pr != null && !pr.count(1)) throw new InterruptedException();
    }

    if (pr != null) pr.pop();
  }

  private static class RoiPosition {
    public int t = 1;
    public int z = 1;
  }

  private RoiPosition getROIPosition(Roi roi, int Ds, double scaleZ) {
    RoiPosition p = new RoiPosition();
    if (roi.getPosition() != 0)
    {
      int[] pos = _imp.convertIndexToPosition(roi.getPosition());
      p.t = pos[2];
      p.z = pos[1];
    }
    else {
      if (roi.getTPosition() != 0) p.t = roi.getTPosition();
      if (roi.getZPosition() != 0) p.z = roi.getZPosition();
    }
    if (Ds == 1) {
      p.z = 1;
      p.t = (p.t - 1) * _imp.getNSlices() + p.z;
    }
    else p.z = (int)Math.round((p.z - 1) * scaleZ) + 1;
    return p;
  }

  public static class RoiLabel {
    public String className = "Foreground";
    public int instance = -1;
    public boolean isIgnore() {
      return className.toLowerCase().equals("ignore");
    }

  }

  public static RoiLabel parseRoiName(String roiName) {
    if (roiName == null) return new RoiLabel();
    // Remove leading and trailing clutter
    String tmp = roiName.replaceFirst("([0-9]+-)*(.*)(-[0-9]+)*$", "$2");
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
