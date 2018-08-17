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
import ij.measure.Calibration;

import java.lang.ArrayIndexOutOfBoundsException;
import java.util.Arrays;

/**
 * n-D data container with continuous memory layout for storing short values
 *
 * @author Thorsten Falk
 * @version 1.0
 * @since 1.0
 */
public class ShortBlob extends Blob {

  private short[] _data;

/**
 * Creates a new uninitialized n-D blob storing short values with given shape.
 *
 * @param shape The shape of the n-D blob
 * @param elementSizeUm For any spatial dimension this array must contain
 *   the actual element size in micrometers, for 1-D (e_x), for 2-D (e_y, e_x),
 *   for 3-D (e_z, e_y, e_x). The number of spatial dimensions of the blob
 *   will be deduced from the length of this vector!
 */
  public ShortBlob(int[] shape, double[] elementSizeUm) {
    super(shape, elementSizeUm);
    this._data = new short[_stride[0] * _shape[0]];
  }

/**
 * Get the raw short array for direct access.
 *
 * @return a reference to the raw data array
 */
  public Object data() {
    return _data;
  }

/**
 * Get the value at the specified position in the blob. Extra dimensions
 * are ignored, i.e. in a 2-D blob only the given x- and y-indices are handled.
 * <p>
 * Warning: This function does not respect the number of spatial dimensions and
 * will simply apply the dimension map t=4,c=3,z=2,y=1,x=0.
 *
 * @param t the index in time (5th) dimension (slowest moving)
 * @param c the index in channel (4th) dimension
 * @param z the index in depth (3rd) dimension
 * @param y the index in row (2nd) dimension
 * @param x the index in column (1st) dimension (fastest moving)
 *
 * @return the value at the given array position.
 *
 * @exception ArrayIndexOutOfBoundsException if any of the given
 *   indices exceeds the corresponding blob extent
 * @exception BlobException if the blob has more than five dimensions
 */
  public short get(int t, int c, int z, int y, int x)
      throws ArrayIndexOutOfBoundsException, BlobException {
    switch (_shape.length)
    {
    case 1:
      if (x < 0 || x >= _shape[0])
          throw new ArrayIndexOutOfBoundsException(
              "Array index " + x + " out of bounds for blob with shape " +
              shapeString());
      return _data[x];
    case 2:
      if (x < 0 || x >= _shape[1] || y < 0 || y >= _shape[0])
          throw new ArrayIndexOutOfBoundsException(
              "Array index (" + y + "," + x + ") out of bounds for blob with " +
              "shape " + shapeString());
      return _data[y * _shape[1] + x];
    case 3:
      if (x < 0 || x >= _shape[2] || y < 0 || y >= _shape[1] ||
          z < 0 || z >= _shape[0])
          throw new ArrayIndexOutOfBoundsException(
              "Array index (" + z + "," + y + "," + x + ") out of bounds for " +
              "blob with shape " + shapeString());
      return _data[(z * _shape[1] + y) * _shape[2] + x];
    case 4:
      if (x < 0 || x >= _shape[3] || y < 0 || y >= _shape[2] ||
          z < 0 || z >= _shape[1] || c < 0 || c >= _shape[0])
          throw new ArrayIndexOutOfBoundsException(
              "Array index (" + c + "," + z + "," + y + "," + x + ") out of " +
              "bounds for blob with shape " + shapeString());
      return _data[((c * _shape[1] + z) * _shape[2] + y) * _shape[3] + x];
    case 5:
      if (x < 0 || x >= _shape[4] || y < 0 || y >= _shape[3] ||
          z < 0 || z >= _shape[2] || c < 0 || c >= _shape[1] ||
          t < 0 || t >= _shape[0])
          throw new ArrayIndexOutOfBoundsException(
              "Array index (" + t + "," + c + "," + z + "," + y + "," + x +
              ") out of bounds for blob with shape " + shapeString());
      return _data[(((t * _shape[1] + c) * _shape[2] + z) * _shape[3] + y) *
                   _shape[4] + x];
    }
    throw new BlobException(
        _shape.length + "-D blob cannot be read using get(t, c, z, y, x)");
  }

/**
 * Get the value at the specified position in the blob. The given array's
 * length must match the dimensionality of this <code>Blob</code>.
 *
 * @param pos the position to read
 * @return the value at the given position in the blob.
 *
 * @exception ArrayIndexOutOfBoundsException if any of the given
 *   indices exceeds the corresponding blob extent or the length of the pos
 *   vector does not match the number of blob dimensions.
 */
  public short get(int[] pos)
      throws ArrayIndexOutOfBoundsException {
    if (pos.length != _shape.length)
        throw new ArrayIndexOutOfBoundsException(
            _shape.length + "-D blob cannot be accessed via " + pos.length +
            "-D index array");
    int idx = 0;
    for (int d = 0; d < _shape.length; ++d) idx += pos[d] * _stride[d];
    return _data[idx];
  }

/**
 * Set the value at the specified position in the blob. Extra dimensions
 * are ignored, i.e. in a 2-D blob only the given x- and y-indices are handled.
 * <p>
 * Warning: This function does not respect the number of spatial dimensions and
 * will simply apply the dimension map t=4,c=3,z=2,y=1,x=0.
 *
 * @param t the index in time (5th) dimension (slowest moving)
 * @param c the index in channel (4th) dimension
 * @param z the index in depth (3rd) dimension
 * @param y the index in row (2nd) dimension
 * @param x the index in column (1st) dimension (fastest moving)
 * @param value the value to write
 *
 * @exception ArrayIndexOutOfBoundsException if any of the given
 *   indices exceeds the corresponding blob extent
 * @exception BlobException if the blob has more than five dimensions
 */
  public void set(int t, int c, int z, int y, int x, short value)
      throws ArrayIndexOutOfBoundsException, BlobException {
    switch (_shape.length)
    {
    case 1:
      if (x < 0 || x >= _shape[0])
          throw new ArrayIndexOutOfBoundsException(
              "Array index " + x + " out of bounds for blob with shape " +
              shapeString());
      _data[x] = value;
      return;
    case 2:
      if (x < 0 || x >= _shape[1] || y < 0 || y >= _shape[0])
          throw new ArrayIndexOutOfBoundsException(
              "Array index (" + y + "," + x + ") out of bounds for blob with " +
              "shape " + shapeString());
      _data[y * _shape[1] + x] = value;
      return;
    case 3:
      if (x < 0 || x >= _shape[2] || y < 0 || y >= _shape[1] ||
          z < 0 || z >= _shape[0])
          throw new ArrayIndexOutOfBoundsException(
              "Array index (" + z + "," + y + "," + x + ") out of bounds for " +
              "blob with shape " + shapeString());
      _data[(z * _shape[1] + y) * _shape[2] + x] = value;
      return;
    case 4:
      if (x < 0 || x >= _shape[3] || y < 0 || y >= _shape[2] ||
          z < 0 || z >= _shape[1] || c < 0 || c >= _shape[0])
          throw new ArrayIndexOutOfBoundsException(
              "Array index (" + c + "," + z + "," + y + "," + x + ") out of " +
              "bounds for blob with shape " + shapeString());
      _data[((c * _shape[1] + z) * _shape[2] + y) * _shape[3] + x] = value;
      return;
    case 5:
      if (x < 0 || x >= _shape[4] || y < 0 || y >= _shape[3] ||
          z < 0 || z >= _shape[2] || c < 0 || c >= _shape[1] ||
          t < 0 || t >= _shape[0])
          throw new ArrayIndexOutOfBoundsException(
              "Array index (" + t + "," + c + "," + z + "," + y + "," + x +
              ") out of bounds for blob with shape " + shapeString());
      _data[(((t * _shape[1] + c) * _shape[2] + z) * _shape[3] + y) *
            _shape[4] + x] = value;
      return;
    }
    throw new BlobException(
        _shape.length + "-D blob cannot be set using set(t, c, z, y, x, v)");
  }

/**
 * Set the value at the specified position in the blob. The given array's
 * length must match the dimensionality of this <code>Blob</code>.
 *
 * @param pos the position to write
 * @param value the value to write
 *
 * @exception ArrayIndexOutOfBoundsException if any of the given
 *   indices exceeds the corresponding blob extent or the length of the pos
 *   vector does not match the number of blob dimensions.
 */
  public void set(int[] pos, short value)
      throws ArrayIndexOutOfBoundsException {
    if (pos.length != _shape.length)
        throw new ArrayIndexOutOfBoundsException(
            _shape.length + "-D blob cannot be accessed via " + pos.length +
            "-D index array");
    int idx = 0;
    for (int d = 0; d < _shape.length; ++d) idx += pos[d] * _stride[d];
    _data[idx] = value;
  }

/**
 * Rescale this blob so that it has the given element size in micrometers
 * afterwards.
 *
 * @param targetElementSizeUm the new element size in micrometers
 * @param interp how to interpolate during scaling
 * @param pr if not <code>null</code>progress is reported to the given
 *   <code>ProgressMonitor</code>
 * @return A reference to <code>this</code> for chaining
 *
 * @exception InterruptedException if the user aborts the computation via the
 *   <code>ProgressMonitor</code>
 */
  public ShortBlob rescale(
      double[] targetElementSizeUm, InterpolationType interp,
      ProgressMonitor pr) throws InterruptedException {

    double[] scales = new double[_elementSizeUm.length];
    boolean needsRescaling = false;
    for (int d = 0; d < scales.length; ++d) {
      scales[d] = _elementSizeUm[d] / targetElementSizeUm[d];
      if (scales[d] != 1.0) needsRescaling = true;
    }
    if (!needsRescaling) return this;

    int[] targetShape = new int[_shape.length];
    for (int d = 0; d < _shape.length - _elementSizeUm.length; ++d)
        targetShape[d] = _shape[d];
    for (int d = 0; d < _elementSizeUm.length; ++d)
        targetShape[_shape.length - _elementSizeUm.length + d] =
            (int)Math.round(
                _shape[_shape.length - _elementSizeUm.length + d] * scales[d]);

    int[] targetStride = new int[_shape.length];
    targetStride[targetShape.length - 1] = 1;
    for (int d = _shape.length - 2; d >= 0; --d)
        targetStride[d] = targetStride[d + 1] * targetShape[d + 1];

    short[] targetData = new short[targetShape[0] * targetStride[0]];

    int N = 1;
    for (int d = 0; d < _shape.length - _elementSizeUm.length; ++d)
        N *= _shape[d];
    int D = 1, targetD = 1;
    if (_elementSizeUm.length == 3) {
      D = _shape[_shape.length - 3];
      targetD = targetShape[_shape.length - 3];
    }
    int H = _shape[_shape.length - 2];
    int targetH = targetShape[_shape.length - 2];
    int W = _shape[_shape.length - 1];
    int targetW = targetShape[_shape.length - 1];

    String msg = "Rescaling ShortBlob " + shapeString() +
        " with element size (" + _elementSizeUm[0];
    for (int d = 1; d < _elementSizeUm.length; ++d)
        msg += "," + _elementSizeUm[d];
    msg += ") to element size (" + targetElementSizeUm[0];
    for (int d = 1; d < targetElementSizeUm.length; ++d)
        msg += "," + targetElementSizeUm[d];
    msg += "). New shape: (" + targetShape[0];
    for (int d = 1; d < targetShape.length; ++d)
        msg += "," + targetShape[d];
    msg += ")";
    IJ.log(msg);

    if (pr != null) pr.init(0, "", "", N * targetD);
    if (pr != null && pr.canceled()) throw new InterruptedException();

    if (_elementSizeUm.length == 3) {

      if (interp == InterpolationType.NEAREST) {

        int idx = 0;
        for (int n = 0; n < N; ++n) {
          for (int z = 0; z < targetD; ++z) {
            if (pr != null) {
              pr.count(1);
              if (pr.canceled()) throw new InterruptedException();
            }
            int zRd = (int)Math.round(z / scales[0]);
            if (zRd >= D) zRd = 2 * (D - 1) - zRd;
            int rdIdxZ = (n * D + zRd) * H * W;
            for (int y = 0; y < targetH; ++y) {
              int yRd = (int)Math.round(y / scales[1]);
              if (yRd >= H) yRd = 2 * (H - 1) - yRd;
              int rdIdxY = rdIdxZ + yRd * W;
              for (int x = 0; x < targetW; ++x, ++idx) {
                int xRd = (int)Math.round(x / scales[2]);
                if (xRd >= W) xRd = 2 * (W - 1) - xRd;
                int rdIdx = rdIdxY + xRd;
                targetData[idx] = _data[rdIdx];
              }
            }
          }
        }

      }
      else {

        int idx = 0;
        for (int n = 0; n < N; ++n) {
          for (int z = 0; z < targetD; ++z) {
            if (pr != null) {
              pr.count(1);
              if (pr.canceled()) throw new InterruptedException();
            }
            double zRd = z / scales[0];
            int zL = (int)Math.floor(zRd);
            int zU = (zL + 1 < D) ? zL + 1 : (2 * (D - 1) - (zL + 1));
            double dz = zRd - zL;
            for (int y = 0; y < targetH; ++y) {
              double yRd = y / scales[1];
              int yL = (int)Math.floor(yRd);
              int yU = (yL + 1 < H) ? yL + 1 : (2 * (H - 1) - (yL + 1));
              double dy = yRd - yL;
              for (int x = 0; x < targetW; ++x, ++idx) {
                double xRd = x / scales[2];
                int xL = (int)Math.floor(xRd);
                int xU = (xL + 1 < W) ? xL + 1 : (2 * (W - 1) - (xL + 1));
                double dx = xRd - xL;
                targetData[idx] = (short)(
                    (1 - dx) * (1 - dy) * (1 - dz) *
                    (double)_data[((n * D + zL) * H + yL) * W + xL] +
                    (1 - dx) * (1 - dy) * dz *
                    (double)_data[((n * D + zU) * H + yL) * W + xL] +
                    (1 - dx) * dy * (1 - dz) *
                    (double)_data[((n * D + zL) * H + yU) * W + xL] +
                    (1 - dx) * dy * dz *
                    (double)_data[((n * D + zU) * H + yU) * W + xL] +
                    dx * (1 - dy) * (1 - dz) *
                    (double)_data[((n * D + zL) * H + yL) * W + xU] +
                    dx * (1 - dy) * dz *
                    (double)_data[((n * D + zU) * H + yL) * W + xU] +
                    dx * dy * (1 - dz) *
                    (double)_data[((n * D + zL) * H + yU) * W + xU] +
                    dx * dy * dz *
                    (double)_data[((n * D + zU) * H + yU) * W + xU]);
              }
            }
          }
        }

      }

    }
    else { // 2D

      if (interp == InterpolationType.NEAREST) {

        int idx = 0;
        for (int n = 0; n < N; ++n) {
          if (pr != null) {
            pr.count(1);
            if (pr.canceled()) throw new InterruptedException();
          }
          for (int y = 0; y < targetH; ++y) {
            int yRd = (int)Math.round(y / scales[0]);
            if (yRd >= H) yRd = 2 * (H - 1) - yRd;
            int rdIdxY = (n * H + yRd) * W;
            for (int x = 0; x < targetW; ++x, ++idx) {
              int xRd = (int)Math.round(x / scales[1]);
              if (xRd >= W) xRd = 2 * (W - 1) - xRd;
              int rdIdx = rdIdxY + xRd;
              targetData[idx] = _data[rdIdx];
            }
          }
        }

      }
      else {

        int idx = 0;
        for (int n = 0; n < N; ++n) {
          if (pr != null) {
            pr.count(1);
            if (pr.canceled()) throw new InterruptedException();
          }
          for (int y = 0; y < targetH; ++y) {
            double yRd = y / scales[0];
            int yL = (int)Math.floor(yRd);
            int yU = (yL + 1 < H) ? yL + 1 : (2 * (H - 1) - (yL + 1));
            double dy = yRd - yL;
            for (int x = 0; x < targetW; ++x, ++idx) {
              double xRd = x / scales[1];
              int xL = (int)Math.floor(xRd);
              int xU = (xL + 1 < W) ? xL + 1 : (2 * (W - 1) - (xL + 1));
              double dx = xRd - xL;
              targetData[idx] = (short)(
                  (1 - dx) * (1 - dy) * (double)_data[(n * H + yL) * W + xL] +
                  (1 - dx) * dy * (double)_data[(n * H + yU) * W + xL] +
                  dx * (1 - dy) * (double)_data[(n * H + yL) * W + xU] +
                  dx * dy * (double)_data[(n * H + yU) * W + xU]);
            }
          }
        }

      }

    }

    _data = targetData;
    _shape = targetShape;
    _stride = targetStride;
    _elementSizeUm = targetElementSizeUm;

    if (pr != null) pr.end();

    return this;
  }

/**
 * {@inheritDoc}
 *
 * This implementation creates a short ImagePlus.
 *
 * @return {@inheritDoc}
 *
 * @exception BlobException {@inheritDoc}
 */
  public ImagePlus convertToImagePlus() throws BlobException {

    if (_shape.length > 5)
        throw new BlobException(
            _shape.length + "-D blob cannot be converted to ImagePlus");

    int W = _shape[_shape.length - 1];
    int H = (_elementSizeUm.length > 1) ? _shape[_shape.length - 2] : 1;
    int D = (_elementSizeUm.length > 2) ? _shape[_shape.length - 3] : 1;
    int C = (_shape.length > _elementSizeUm.length) ?
        _shape[_shape.length - _elementSizeUm.length - 1] : 1;
    int T = (_shape.length > _elementSizeUm.length + 1) ?
        _shape[_shape.length - _elementSizeUm.length - 2] : 1;

    ImagePlus impOut = IJ.createHyperStack("", W, H, C, D, T, 16);

    Calibration cal = new Calibration();
    if (_elementSizeUm.length == 2) {
      cal.pixelDepth = 1.0;
      cal.pixelHeight = _elementSizeUm[0];
      cal.pixelWidth = _elementSizeUm[1];
    }
    else {
      cal.pixelDepth = _elementSizeUm[0];
      cal.pixelHeight = _elementSizeUm[1];
      cal.pixelWidth = _elementSizeUm[2];
    }
    cal.setUnit("um");
    impOut.setCalibration(cal);

    int lblIdx = 0;
    for (int i = 0; i < T * C * D; ++i) {
      short[] out = (short[])impOut.getStack().getProcessor(i + 1).getPixels();
      for (int j = 0; j < out.length; ++j, ++lblIdx) out[j] = _data[lblIdx];
    }
    impOut.setPosition(1);
    impOut.resetDisplayRange();
    return impOut;
  }

}
