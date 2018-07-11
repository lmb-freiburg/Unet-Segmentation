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

import ij.plugin.PlugIn;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import ij.process.FloatProcessor;

public class DistanceTransform implements PlugIn {

  public static final float BG_VALUE = 1.0e20f;

  public enum Mode {
      DISTANCE_TO_FOREGROUND, DISTANCE_TO_BACKGROUND;
  }

  private static class DT {

    private float[] f = null;
    private float[] d = null;
    private float[] _z = null;
    private int[] _v = null;

    private DT(int capacity) {
      f = new float[capacity];
      d = new float[capacity];
      _z = new float[capacity + 1];
      _v = new int[capacity];
    }

    private void run(int n) {
      int k = 0;
      _v[0] = 0;
      _z[0] = -BG_VALUE;
      _z[1] = BG_VALUE;
      for (int q = 1; q <= n - 1; q++) {
        float s  = ((f[q] + q * q) - (f[_v[k]] + _v[k] * _v[k])) /
            (2 * q - 2 * _v[k]);
        while (s <= _z[k]) {
          k--;
          s = ((f[q] + q * q) - (f[_v[k]] + _v[k] * _v[k])) /
              (2 * q - 2 * _v[k]);
        }
        k++;
        _v[k] = q;
        _z[k] = s;
        _z[k+1] = BG_VALUE;
      }

      k = 0;
      for (int q = 0; q <= n - 1; q++) {
        while (_z[k+1] < q) k++;
        d[q] = (q - _v[k]) * (q - _v[k]) + f[_v[k]];
      }
    }
  }

  private static FloatProcessor getSquaredDistance(
      FloatProcessor ip, float fg, Mode mode) {

    int W = ip.getWidth();
    int H = ip.getHeight();

    for (int y = 0; y < H; y++) {
      for (int x = 0; x < W; x++) {
        if (ip.getf(x, y) == fg) {
          if (mode == Mode.DISTANCE_TO_FOREGROUND) ip.setf(x, y, 0.0f);
          else ip.setf(x, y, BG_VALUE);
        }
        else
        {
          if (mode == Mode.DISTANCE_TO_FOREGROUND) ip.setf(x, y, BG_VALUE);
          else ip.setf(x, y, 0.0f);
        }
      }
    }

    DT dt = new DT(Math.max(W, H));

    // transform along columns
    for (int x = 0; x < W; x++) {
      for (int y = 0; y < H; y++) dt.f[y] = ip.getf(x, y);
      dt.run(H);
      for (int y = 0; y < H; y++) ip.setf(x, y, dt.d[y]);
    }

    // transform along rows
    for (int y = 0; y < H; y++) {
      for (int x = 0; x < W; x++) dt.f[x] = ip.getf(x, y);
      dt.run(W);
      for (int x = 0; x < W; x++) ip.setf(x, y, dt.d[x]);
    }

    return ip;
  }

/*======================================================================*/
/*!
 *   Binary 2D Euclidean distance transform. The given fg value is treated
 *   as foreground, all other values as background. This method computes
 *   the euclidean distance to the nearest foreground pixel inline, i.e.
 *   the given FloatProcessor will contain the distance transform. For
 *   operator chaining it is also returned.
 *
 *   \param ip The FloatProcessor to compute the distance transform
 *   \param fg The value of foreground pixels
 *
 *   \return The FloatProcessor containing the distance transform
 */
/*======================================================================*/
  public static FloatProcessor getDistance(
      FloatProcessor ip, float fg, Mode mode) {
    ip = getSquaredDistance(ip, fg, mode);
    float[] pixels = (float[])ip.getPixels();
    for (int i = 0; i < ip.getHeight() * ip.getWidth(); i++)
        pixels[i] = (float)Math.sqrt(pixels[i]);
    return ip;
  }

/*======================================================================*/
/*!
 *   Binary 2D/3D Euclidean distance transform. The given fg value is treated
 *   as foreground, all other values as background. Timepoint and channels
 *   are processed individually.
 *
 *   \param imp  The ImagePlus to compute the distance transform for
 *   \param fg   The value of foreground pixels
 *   \param mode Distances can be computed to foreground pixels
 *     (DistanceTransform.DISTANCE_TO_FOREGROUND) or to background pixels
 *     (DistanceTransform.DISTANCE_TO_BACKGROUND)
 *   \param usePhysicalUnits If true, returned distances are in micrometers
 *     according to the Calibration of the ImagePlus, otherwise the distance in
 *     pixels is returned
 *   \param pr   A progress reporter to output progress to
 *
 *   \return The Blob containing the distance transform
 */
/*======================================================================*/
  public static FloatBlob getDistance(
      ImagePlus imp, float fg, Mode mode, boolean usePhysicalUnits,
      ProgressMonitor pr) {

    int T = imp.getNFrames();
    int C = imp.getNChannels();
    int D = imp.getNSlices();
    int H = imp.getHeight();
    int W = imp.getWidth();

    if (pr != null && pr.getMax() == 0)
        pr.init(0, "", "", 4 * T * C * D + ((D > 1) ? T * C * H : 0));

    Calibration cal = imp.getCalibration();
    double factor = 1.0;
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

    int[] shape = new int[] { T, C, D, H, W };
    double[] elSize = null;
    if (D == 1) {
      elSize = new double[] {
          factor * cal.pixelHeight, factor * cal.pixelWidth };
    }
    else {
      elSize = new double[] {
          factor * cal.pixelDepth, factor * cal.pixelHeight,
          factor * cal.pixelWidth };
    }
    FloatBlob dtBlob = new FloatBlob(shape, elSize);
    float[] dtData = (float[])dtBlob.data();

    // Initialize Blob according to mode
    int idx = 0;
    for (int t = 0; t < T; ++t) {
      for (int c = 0; c < C; ++c) {
        for (int z = 0; z < D; ++z) {
          if (pr != null) pr.count(1);
          ImageProcessor ip = imp.getStack().getProcessor(
              imp.getStackIndex(c + 1, z + 1, t + 1));
          for (int y = 0; y < H; ++y) {
            for (int x = 0; x < W; ++x, ++idx) {
              if (ip.getf(x, y) == fg)
                  dtData[idx] = (mode == Mode.DISTANCE_TO_FOREGROUND) ?
                      0 : BG_VALUE;
              else
                  dtData[idx] = (mode == Mode.DISTANCE_TO_FOREGROUND) ?
                      BG_VALUE : 0;
            }
          }
        }
      }
    }

    DT dt = new DT(Math.max(D, Math.max(W, H)));

    for (int t = 0; t < T; ++t) {
      int tPos = t * C * D * W * H;
      for (int c = 0; c < C; ++c) {
        int cPos = tPos + c * D * W * H;
        for (int z = 0; z < D; ++z) {
          int zPos = cPos + z * W * H;

          // Transform along columns
          if (pr != null) pr.count(1);
          for (int x = 0; x < W; ++x) {
            int xPos = zPos + x;
            int xStride = W;
            for (int y = 0; y < H; ++y, xPos += xStride) dt.f[y] = dtData[xPos];
            dt.run(H);
            xPos = zPos + x;
            for (int y = 0; y < H; ++y, xPos += xStride)
                dtData[xPos] = dt.d[y] *
                    (float)(usePhysicalUnits ? elSize[elSize.length - 2] : 1.0);
          }

          // Transform along rows
          if (pr != null) pr.count(1);
          for (int y = 0; y < H; ++y) {
            int xRd = zPos + y * W;
            for (int x = 0; x < W; ++x, ++xRd) dt.f[x] = dtData[xRd];
            dt.run(W);
            xRd = zPos + y * W;
            for (int x = 0; x < W; ++x, ++xRd)
                dtData[xRd] = dt.d[x] *
                    (float)(usePhysicalUnits ? elSize[elSize.length - 1] : 1.0);
          }

        }

        if (D > 1) {

          // Transform along levels
          for (int y = 0; y < H; ++y) {
            int yPos = cPos + y * W;

            if (pr != null) pr.count(1);
            for (int x = 0; x < W; ++x) {
              int pos = yPos + x;
              int stride = W * H;
              for (int z = 0; z < D; ++z, pos += stride) dt.f[z] = dtData[pos];
              dt.run(D);
              pos = yPos + x;
              for (int z = 0; z < D; ++z, pos += stride)
                  dtData[pos] = dt.d[z] *
                      (float)(usePhysicalUnits ? elSize[0] : 1.0);
            }
          }

        }

        // sqrt
        for (int z = 0; z < D; ++z) {
          if (pr != null) pr.count(1);
          int startIdx = ((t * C + c) * D + z) * W * H;
          for (int i = 0; i < W * H; i++)
              dtData[startIdx + i] = (float)Math.sqrt(dtData[startIdx + i]);
        }
      }
    }
    if (pr != null) pr.end();

    return dtBlob;
  }

/*======================================================================*/
/*!
 *   Binary 2D/3D Euclidean distance transform. The given fg value is treated
 *   as foreground, all other values as background. Timepoint and channels
 *   are processed individually.
 *
 *   \param data The IntBlob to compute the distance transform for
 *   \param fg   The value of foreground pixels
 *   \param mode Distances can be computed to foreground pixels
 *     (DistanceTransform.DISTANCE_TO_FOREGROUND) or to background pixels
 *     (DistanceTransform.DISTANCE_TO_BACKGROUND)
 *   \param usePhysicalUnits If true, the distance is output in micrometers,
 *     otherwise the distance in pixels is returned
 *   \param pr   A progress reporter to output progress to
 *
 *   \return The Blob containing the distance transform
 */
/*======================================================================*/
  public static FloatBlob getDistance(
      IntBlob dataBlob, int fg, Mode mode, boolean usePhysicalUnits,
      ProgressMonitor pr) {

    int N = 1;
    for (int i = 0; i < dataBlob.nDims() - dataBlob.nSpatialDims(); ++i)
        N *= dataBlob.shape()[i];
    int D = (dataBlob.nSpatialDims() >= 3) ?
        dataBlob.shape()[dataBlob.nDims() - 3] : 1;
    int H = (dataBlob.nSpatialDims() >= 2) ?
        dataBlob.shape()[dataBlob.nDims() - 2] : 1;
    int W = dataBlob.shape()[dataBlob.nDims() - 1];

    if (pr != null && pr.getMax() == 0)
        pr.init(0, "", "",
                3 * N * D + ((H > 1) ? N * D : 0) + ((D > 1) ? N * H : 0));

    FloatBlob dtBlob = new FloatBlob(
        dataBlob.shape(), dataBlob.elementSizeUm());
    float[] dtData = (float[])dtBlob.data();
    int[] inData = (int[])dataBlob.data();
    double[] elSize = dataBlob.elementSizeUm();

    // Initialize Blob according to mode
    int idx = 0;
    for (int n = 0; n < N; ++n) {
      for (int z = 0; z < D; ++z) {
        if (pr != null) pr.count(1);
        for (int i = 0; i < W * H; ++i, ++idx) {
          if (inData[idx] == fg)
              dtData[idx] = (mode == Mode.DISTANCE_TO_FOREGROUND) ?
                  0 : BG_VALUE;
          else
              dtData[idx] = (mode == Mode.DISTANCE_TO_FOREGROUND) ?
                  BG_VALUE : 0;
        }
      }
    }

    DT dt = new DT(Math.max(D, Math.max(W, H)));

    for (int n = 0; n < N; ++n) {
      int nPos = n * D * W * H;
      for (int z = 0; z < D; ++z) {
        int zPos = nPos + z * W * H;

        if (H > 1) {
          // Transform along columns
          if (pr != null) pr.count(1);
          for (int x = 0; x < W; ++x) {
            int yRd = zPos + x;
            int stride = W;
            for (int y = 0; y < H; ++y, yRd += stride) dt.f[y] = dtData[yRd];
            dt.run(H);
            yRd = zPos + x;
            for (int y = 0; y < H; ++y, yRd += stride)
                dtData[yRd] = dt.d[y] *
                    (float)(usePhysicalUnits ? elSize[elSize.length - 2] : 1.0);
          }
        }

        // Transform along rows
        if (pr != null) pr.count(1);
        for (int y = 0; y < H; ++y) {
          int xRd = zPos + y * W;
          for (int x = 0; x < W; ++x, ++xRd) dt.f[x] = dtData[xRd];
          dt.run(W);
          xRd = zPos + y * W;
          for (int x = 0; x < W; ++x, ++xRd)
              dtData[xRd] = dt.d[x] *
                  (float)(usePhysicalUnits ? elSize[elSize.length - 1] : 1.0);
        }

      }

      if (D > 1) {

        // Transform along levels
        for (int y = 0; y < H; ++y) {
          int yPos = nPos + y * W;
          if (pr != null) pr.count(1);
          for (int x = 0; x < W; ++x) {
            int pos = yPos + x;
            int stride = W * H;
            for (int z = 0; z < D; ++z, pos += stride) dt.f[z] = dtData[pos];
            dt.run(D);
            pos = yPos + x;
            for (int z = 0; z < D; ++z, pos += stride)
                dtData[pos] = dt.d[z] *
                    (float)(usePhysicalUnits ? elSize[0] : 1.0);
          }
        }

      }

      // sqrt
      for (int z = 0; z < D; ++z) {
        if (pr != null) pr.count(1);
        int startIdx = nPos + z * W * H;
        for (int i = 0; i < W * H; i++)
            dtData[startIdx + i] = (float)Math.sqrt(dtData[startIdx + i]);
      }
    }

    if (pr != null) pr.end();

    return dtBlob;
  }

  @Override
  public void run(String arg) {
    ImagePlus imp = IJ.getImage();
    if (imp == null) IJ.noImage();
    try
    {
      ProgressMonitor pr = new ProgressMonitor(null);
      pr.initNewTask("Distance transform", 1.0f, 0);
      ImagePlus res = getDistance(
          imp, 255, Mode.DISTANCE_TO_FOREGROUND,
          false, pr).convertToImagePlus();
      res.setTitle(imp.getTitle() + " - Euclidean Distance Transform");
      res.show();
    }
    catch(BlobException e)
    {
      IJ.error(e.toString());
    }
  }

}
