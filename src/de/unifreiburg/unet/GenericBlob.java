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
 * n-D data container with continuous memory layout for storing references
 *
 * @author Thorsten Falk
 * @version 1.0
 * @since 1.0
 */
public class GenericBlob<T> extends Blob {

  private final T[] _data;

/**
 * Creates a new uninitialized n-D blob storing references of type
 * <code>T</code> with given shape.
 *
 * @param shape The shape of the n-D blob
 * @param elementSizeUm For any spatial dimension this array must contain
 *   the actual element size in micrometers, for 1-D (e_x), for 2-D (e_y, e_x),
 *   for 3-D (e_z, e_y, e_x). The number of spatial dimensions of the blob
 *   will be deduced from the length of this vector!
 * @param cl The Array type to use
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
 * Get a reference to the value at the specified position in the blob. Extra
 * dimensions are ignored, i.e. in a 2-D blob only the given x- and y-indices
 * are handled.
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
 * @return a reference to the object at the given array position.
 *
 * @exception ArrayIndexOutOfBoundsException if any of the given
 *   indices exceeds the corresponding blob extent
 * @exception BlobException if the blob has more than five dimensions
 */
  public T get(int t, int c, int z, int y, int x)
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
 * Get a reference to the value at the specified position in the blob. The
 * given array's length must match the dimensionality of this <code>Blob</code>.
 *
 * @param pos the position to read
 * @return the value at the given position in the blob.
 *
 * @exception ArrayIndexOutOfBoundsException if any of the given
 *   indices exceeds the corresponding blob extent or the length of the pos
 *   vector does not match the number of blob dimensions.
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
 * Set the reference at the specified position in the blob. Extra dimensions
 * are ignored, i.e. in a 2-D blob only the given x- and y-indices are handled.
 * This method is provided for convenience to match the interface of the Blobs
 * for storing primitive types. The corresponding <code>get</code>-Method
 * can be used for direct manipulation of the blob content.
 * <p>
 * Warning: This function does not respect the number of spatial dimensions and
 * will simply apply the dimension map t=4,c=3,z=2,y=1,x=0.
 *
 * @param t the index in time (5th) dimension (slowest moving)
 * @param c the index in channel (4th) dimension
 * @param z the index in depth (3rd) dimension
 * @param y the index in row (2nd) dimension
 * @param x the index in column (1st) dimension (fastest moving)
 * @param value the reference to write
 *
 * @exception ArrayIndexOutOfBoundsException if any of the given
 *   indices exceeds the corresponding blob extent
 * @exception BlobException if the blob has more than five dimensions
 */
  public void set(int t, int c, int z, int y, int x, T value)
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
        _shape.length + "-D blob cannot be read using get(t, c, z, y, x)");
  }

/**
 * Set the value at the specified position in the blob. The given array's
 * length must match the dimensionality of this <code>Blob</code>.
 * This method is provided for convenience to match the interface of the Blobs
 * for storing primitive types. The corresponding <code>get</code>-Method
 * can be used for direct manipulation of the blob content.
 *
 * @param pos the position to write
 * @param value the value to write
 *
 * @exception ArrayIndexOutOfBoundsException if any of the given
 *   indices exceeds the corresponding blob extent or the length of the pos
 *   vector does not match the number of blob dimensions.
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
 * {@inheritDoc}
 * <p>
 *   Supported types are:
 *   <ul>
 *   <li>Boolean - Converted to 8-Bit ImagePlus with values 0, 255
 *   <li>Byte    - as is
 *   <li>Short   - as is
 *   <li>Integer - Converted to 16-Bit ImagePlus without overflow check!
 *   <li>Float   - as is
 *   <li>Double  - Converted to 32-Bit ImagePlus
 *   </ul>
 *
 * @return {@inheritDoc}
 *
 * @exception BlobException {@inheritDoc}
 */
  public ImagePlus convertToImagePlus()
        throws BlobException {
    int nNonSpatialDimensions = _shape.length - _elementSizeUm.length;
    if (nNonSpatialDimensions > 2)
        throw new BlobException(
            _shape.length + "-D blob with " + _elementSizeUm.length +
            " spatial dimensions cannot be converted to ImagePlus");
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
        byte[] out =
            (byte[])(impOut.getStack().getProcessor(i + 1).getPixels());
        for (int j = 0; j < out.length; ++j, ++lblIdx)
            out[j] = (_data instanceof Boolean[]) ?
                (((Boolean)_data[lblIdx]) ? (byte)255 : (byte)0) :
                ((Byte)_data[lblIdx]).byteValue();
        break;
      }
      case 16: {
        short[] out =
            (short[])(impOut.getStack().getProcessor(i + 1).getPixels());
        for (int j = 0; j < out.length; ++j, ++lblIdx)
            out[j] = (_data instanceof Short[]) ?
                ((Short)_data[lblIdx]).shortValue() :
                ((Integer)_data[lblIdx]).shortValue();
        break;
      }
      case 32: {
        float[] out =
            (float[])(impOut.getStack().getProcessor(i + 1).getPixels());
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
