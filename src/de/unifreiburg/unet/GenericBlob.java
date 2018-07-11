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
import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * Generic n-D data container with continuous memory layout
 *
 * @author Thorsten Falk
 * @version 1.0
 */
public class GenericBlob<T> extends Blob {

  private final T[] _data;

/**
 * Construct a new uninitialized n-D blob with given shape.
 *
 * @param shape The shape of the new n-D Blob
 * @param elementSizeUm For any spatial dimension this array must contain
 *   the actual element size in micrometers, for 1-D (e_x), for 2-D (e_y, e_x),
 *   for 3-D (e_z, e_y, e_x). The number of spatial dimensions of the blob
 *   will be deduced from the length of this vector!
 * @param cl The Array type of the given base type.
 *   E.g. Blob<Float> = new Blob<Float>(shape, elSize, Float[].class);
 */
  public GenericBlob(int[] shape, double[] elementSizeUm, Class<T[]> cl) {
    super(shape, elementSizeUm);
    this._data = cl.cast(
        Array.newInstance(cl.getComponentType(), _stride[0] * _shape[0]));
  }

/**
 * Get the raw data array for direct access.
 *
 * @return A reference to the raw data array
 */
  public Object data() {
    return _data;
  }

/**
 * Get the value at the specified position in the blob. Extra dimensions
 * are ignored, i.e. in a 2-D blob only the given x- and y-indices are handled.
 * For immutable types T the function returns the value, otherwise a reference
 * to the contained object.
 *
 * @param t The index in time (5th) dimension (slowest moving)
 * @param c The index in channel (4th) dimension
 * @param z The index in depth (3rd) dimension
 * @param y The index in row (2nd) dimension
 * @param x The index in column (1st) dimension (fastest moving)
 *
 * @return The value or reference to the object at the given array position.
 *
 * @except ArrayIndexOutOfBoundsException is thrown if any of the given indices
 *         exceeds the corresponding Blob extent or the blob has more than 5
 *         dimensions.
 */
  public T get(int t, int c, int z, int y, int x)
      throws ArrayIndexOutOfBoundsException {
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
    throw new ArrayIndexOutOfBoundsException(
        _shape.length + "-D blob cannot be read using get(t, c, z, y, x)");
  }

/**
 * Get the value at the specified position in the blob. The given array's
 * length must match the dimensionality of the Blob. For immutable types T
 * the function returns the value, otherwise a reference to the contained
 * object.
 *
 * @param pos The position in the Blob
 *
 * @return The value or reference to the object at the given array position.
 *
 * @except ArrayIndexOutOfBoundsException is thrown if any of the given indices
 *         exceeds the corresponding Blob extent or the length of the pos
 *         vector does not match the number of Blob dimensions.
 */
  public T get(int[] pos)
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
 *
 * @param t The index in time (5th) dimension (slowest moving)
 * @param c The index in channel (4th) dimension
 * @param z The index in depth (3rd) dimension
 * @param y The index in row (2nd) dimension
 * @param x The index in column (1st) dimension (fastest moving)
 * @param value The value to write. The value is not copied, changes of the
 *              original reference will also affect the reference in the array
 *              for mutable types!
 *
 * @except ArrayIndexOutOfBoundsException is thrown if any of the given indices
 *         exceeds the corresponding Blob extent or the blob has more than 5
 *         dimensions.
 */
  public void set(int t, int c, int z, int y, int x, T value)
      throws ArrayIndexOutOfBoundsException {
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
    throw new ArrayIndexOutOfBoundsException(
        _shape.length + "-D blob cannot be read using get(t, c, z, y, x)");
  }

/**
 * Set the value at the specified position in the blob. The given array's
 * length must match the dimensionality of the Blob.
 *
 * @param pos The position in the Blob
 * @param value The value to write. The value is not copied, changes of the
 *              original reference will also affect the reference in the array
 *              for mutable types!
 *
 * @except ArrayIndexOutOfBoundsException is thrown if any of the given indices
 *         exceeds the corresponding Blob extent or the length of the pos
 *         vector does not match the number of Blob dimensions.
 */
  public void set(int[] pos, T value)
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
 * Create an ImagePlus from this blob for Visualization in ImageJ. Supported
 * types are:
 *   - Boolean - Converted to 8-Bit ImagePlus with values 0, 255
 *   - Byte    - as is
 *   - Short   - as is
 *   - Integer - Converted to 16-Bit ImagePlus without overflow check!
 *   - Float   - as is
 *   - Double  - Converted to 32-Bit ImagePlus
 *
 * If the Blob has less than 5 dimensions leading axes will be omitted in
 * order (t, c, z, y, x), e.g. if the Blob has 3 dimensions they will be
 * treated as (z, y, x).
 *
 * @return The ImagePlus
 *
 * @except BlobException is thrown if the Blob has more than 5 dimensions or
 *         the datatype of the blob is not supported.
 */
  public ImagePlus convertToImagePlus()
        throws BlobException {
    if (_shape.length > 5)
        throw new BlobException(
            _shape.length + "-D blob cannot be converted to ImagePlus");
    int width = _shape[_shape.length - 1];
    int height = (_elementSizeUm.length > 1) ? _shape[_shape.length - 2] : 1;
    int depth = (_elementSizeUm.length > 2) ? _shape[_shape.length - 3] : 1;
    int nChannels = (_shape.length > _elementSizeUm.length) ?
        _shape[_shape.length - _elementSizeUm.length - 1] : 1;
    int nFrames = (_shape.length > _elementSizeUm.length + 1) ?
        _shape[_shape.length - _elementSizeUm.length - 2] : 1;

    int bitdepth = 32;
    if (_data instanceof Boolean[] || _data instanceof Byte[]) bitdepth = 8;
    else if (_data instanceof Short[] || _data instanceof Integer[])
        bitdepth = 16;
    else if (_data instanceof Float[] || _data instanceof Double[])
        bitdepth = 32;
    else throw new BlobException(
        "Datatype " + _data.getClass().getComponentType().getName() +
        " not supported");

    ImagePlus impOut = IJ.createHyperStack(
        "", width, height, nChannels, depth, nFrames, bitdepth);

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
    for (int i = 0; i < nFrames * nChannels * depth; ++i) {
      switch (bitdepth) {
      case 8: {
        byte[] out = (byte[])(
            (impOut.getStackSize() == 1) ? impOut.getProcessor().getPixels() :
            impOut.getStack().getProcessor(i + 1).getPixels());
        for (int j = 0; j < out.length; ++j, ++lblIdx)
            out[j] = (_data instanceof Boolean[]) ?
                (((Boolean)_data[lblIdx]) ? (byte)255 : (byte)0) :
                ((Byte)_data[lblIdx]).byteValue();
        break;
      }
      case 16: {
        short[] out = (short[])(
            (impOut.getStackSize() == 1) ? impOut.getProcessor().getPixels() :
            impOut.getStack().getProcessor(i + 1).getPixels());
        for (int j = 0; j < out.length; ++j, ++lblIdx)
            out[j] = (_data instanceof Short[]) ?
                ((Short)_data[lblIdx]).shortValue() :
                ((Integer)_data[lblIdx]).shortValue();
        break;
      }
      case 32: {
        float[] out = (float[])(
            (impOut.getStackSize() == 1) ? impOut.getProcessor().getPixels() :
            impOut.getStack().getProcessor(i + 1).getPixels());
        for (int j = 0; j < out.length; ++j, ++lblIdx)
            out[j] = (_data instanceof Float[]) ?
                ((Float)_data[lblIdx]).floatValue() :
                ((Double)_data[lblIdx]).floatValue();
        break;
      }
      }
      impOut.setPosition(i + 1);
      impOut.resetDisplayRange();
    }
    return impOut;
  }

}
